/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Preconditions;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.litho.ComponentTree.LayoutStateFuture;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Wraps objects which should only be available for the duration of a LayoutState, to access them in
 * other classes such as ComponentContext during layout state calculation. When the layout
 * calculation finishes, the LayoutState reference is nullified. Using a wrapper instead of passing
 * the instances directly helps with clearing out the reference from all objects that hold on to it,
 * without having to keep track of all these objects to clear out the references.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class LayoutStateContext {

  private final @Nullable ComponentTree mComponentTree;

  private @Nullable LayoutState mLayoutStateRef;
  private @Nullable TreeState mTreeState;
  private @Nullable LayoutStateFuture mLayoutStateFuture;
  private @Nullable Map<Integer, LithoNode> mComponentIdToWillRenderLayout;
  private @Nullable DiffNode mCurrentDiffTree;

  private @Nullable DiffNode mCurrentNestedTreeDiffNode;
  private boolean mHasNestedTreeDiffNodeSet = false;

  private boolean mIsLayoutStarted = false;

  private @Nullable PerfEvent mPerfEvent;

  private final String mThreadCreatedOn;
  private List<String> mThreadReleasedOn = new LinkedList<>();
  private List<String> mThreadResumedOn = new LinkedList<>();

  @Deprecated
  public static LayoutStateContext getTestInstance(ComponentContext c) {
    final LayoutState layoutState = new LayoutState(c);
    final LayoutStateContext layoutStateContext =
        new LayoutStateContext(layoutState, new TreeState(), c.getComponentTree(), null, null);
    layoutState.setLayoutStateContextForTest(layoutStateContext);
    return layoutStateContext;
  }

  /**
   * This is only used in tests and marked as {@link Deprecated}. Use {@link
   * LayoutStateContext(LayoutState, ComponentTree, LayoutStateFuture, DiffNode, StateHandler)}
   * instead.
   *
   * @param layoutState
   * @param componentTree
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  @Deprecated
  public LayoutStateContext(
      final LayoutState layoutState, @Nullable final ComponentTree componentTree) {
    this(layoutState, new TreeState(), componentTree, null, null);
  }

  LayoutStateContext(
      final LayoutState layoutState,
      final TreeState treeState,
      final @Nullable ComponentTree componentTree,
      final @Nullable LayoutStateFuture layoutStateFuture,
      final @Nullable DiffNode currentDiffTree) {
    mLayoutStateRef = layoutState;
    mLayoutStateFuture = layoutStateFuture;
    mComponentTree = componentTree;
    mCurrentDiffTree = currentDiffTree;
    mTreeState = treeState;
    mThreadCreatedOn = Thread.currentThread().getName();
  }

  @Nullable
  LithoNode consumeLayoutCreatedInWillRender(int componentId) {
    if (mComponentIdToWillRenderLayout != null) {
      return mComponentIdToWillRenderLayout.remove(componentId);
    } else {
      return null;
    }
  }

  @Nullable
  LithoNode getLayoutCreatedInWillRender(int componentId) {
    if (mComponentIdToWillRenderLayout != null) {
      return mComponentIdToWillRenderLayout.get(componentId);
    } else {
      return null;
    }
  }

  void setLayoutCreatedInWillRender(int componentId, final @Nullable LithoNode node) {
    if (mComponentIdToWillRenderLayout == null) {
      mComponentIdToWillRenderLayout = new HashMap<>();
    }
    mComponentIdToWillRenderLayout.put(componentId, node);
  }

  void releaseReference() {
    mLayoutStateRef = null;
    mTreeState = null;
    mLayoutStateFuture = null;
    mCurrentDiffTree = null;
    mComponentIdToWillRenderLayout = null;
    mPerfEvent = null;
    mThreadReleasedOn.add(Thread.currentThread().getName());
  }

  /** Returns the LayoutState instance or null if the layout state has been released. */
  @Nullable
  LayoutState getLayoutState() {
    return mLayoutStateRef;
  }

  @Nullable
  @VisibleForTesting
  public ComponentTree getComponentTree() {
    return mComponentTree;
  }

  public @Nullable LayoutStateFuture getLayoutStateFuture() {
    return mLayoutStateFuture;
  }

  boolean isLayoutInterrupted() {
    boolean isInterruptRequested =
        mLayoutStateFuture != null
            && mLayoutStateFuture.isInterruptRequested()
            && !ThreadUtils.isMainThread();
    boolean isInterruptible = mLayoutStateRef != null && mLayoutStateRef.isInterruptible();

    return isInterruptible && isInterruptRequested;
  }

  boolean isLayoutReleased() {
    return mLayoutStateFuture != null && mLayoutStateFuture.isReleased();
  }

  public void markLayoutUninterruptible() {
    if (mLayoutStateRef != null) {
      mLayoutStateRef.setInterruptible(false);
    }
  }

  void markLayoutStarted() {
    if (mIsLayoutStarted) {
      throw new IllegalStateException(
          "Duplicate layout of a component: "
              + (mComponentTree != null ? mComponentTree.getRoot() : null));
    }
    mIsLayoutStarted = true;
  }

  public @Nullable DiffNode getCurrentDiffTree() {
    return mCurrentDiffTree;
  }

  void setNestedTreeDiffNode(@Nullable DiffNode diff) {
    mHasNestedTreeDiffNodeSet = true;
    mCurrentNestedTreeDiffNode = diff;
  }

  boolean hasNestedTreeDiffNodeSet() {
    return mHasNestedTreeDiffNodeSet;
  }

  public @Nullable DiffNode consumeNestedTreeDiffNode() {
    final DiffNode node = mCurrentNestedTreeDiffNode;
    mCurrentNestedTreeDiffNode = null;
    mHasNestedTreeDiffNodeSet = false;
    return node;
  }

  TreeState getTreeState() {
    return Preconditions.checkNotNull(mTreeState);
  }

  @Nullable
  public PerfEvent getPerfEvent() {
    return mPerfEvent;
  }

  public void setPerfEvent(@Nullable PerfEvent perfEvent) {
    mPerfEvent = perfEvent;
  }

  public void markLayoutResumed() {
    mThreadResumedOn.add(Thread.currentThread().getName());
  }

  public String getLifecycleDebugString() {
    StringBuilder builder = new StringBuilder();

    builder
        .append("LayoutStateContext was created on: ")
        .append(mThreadCreatedOn)
        .append("\n")
        .append("LayoutStateContext was released on: [");

    for (String thread : mThreadReleasedOn) {
      builder.append(thread).append(" ,");
    }

    builder.append("]").append("LayoutStateContext was resumed on: [");

    for (String thread : mThreadResumedOn) {
      builder.append(thread).append(" ,");
    }

    builder.append("]");

    return builder.toString();
  }
}

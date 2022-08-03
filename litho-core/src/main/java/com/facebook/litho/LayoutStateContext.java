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
import java.util.LinkedList;
import java.util.List;

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
  private @Nullable DiffNode mCurrentDiffTree;

  private @Nullable DiffNode mCurrentNestedTreeDiffNode;

  private boolean mIsLayoutStarted = false;

  private @Nullable PerfEvent mPerfEvent;

  private LayoutPhaseMeasuredResultCache mCache;

  private final String mThreadCreatedOn;
  private List<String> mThreadReleasedOn = new LinkedList<>();
  private List<String> mThreadResumedOn = new LinkedList<>();

  private final RenderStateContext mRenderStateContext;

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
    mRenderStateContext = new RenderStateContext(mLayoutStateFuture, mTreeState);
    mCache = mRenderStateContext.getCache().getLayoutPhaseMeasuredResultCache();
  }

  public RenderStateContext getRenderStateContext() {
    return mRenderStateContext;
  }

  // Post Measure
  // After collect results
  void releaseReference() {
    mLayoutStateRef = null;
    mTreeState = null;
    mLayoutStateFuture = null;
    mCurrentDiffTree = null;
    mRenderStateContext.release();
    mPerfEvent = null;
    mThreadReleasedOn.add(Thread.currentThread().getName());
  }

  static long calculateNextId(
      LayoutStateContext layoutStateContext, Component component, String componentKey) {
    final LayoutState layoutState = Preconditions.checkNotNull(layoutStateContext.getLayoutState());

    return layoutState.calculateLayoutOutputId(
        component,
        componentKey,
        layoutState.getCurrentLevel(),
        OutputUnitType.CONTENT,
        -1 /* previousId */);
  }

  // Create & Measure!
  /** Returns the LayoutState instance or null if the layout state has been released. */
  @Nullable
  LayoutState getLayoutState() {
    return mLayoutStateRef;
  }

  // Only in tests
  @Nullable
  @VisibleForTesting
  public ComponentTree getComponentTree() {
    return mComponentTree;
  }

  // Only in tests
  public @Nullable LayoutStateFuture getLayoutStateFuture() {
    return mLayoutStateFuture;
  }

  // Before create
  void markLayoutStarted() {
    if (mIsLayoutStarted) {
      throw new IllegalStateException(
          "Duplicate layout of a component: "
              + (mComponentTree != null ? mComponentTree.getRoot() : null));
    }
    mIsLayoutStarted = true;
  }

  // Measure
  public @Nullable DiffNode getCurrentDiffTree() {
    return mCurrentDiffTree;
  }

  // Measure
  void setNestedTreeDiffNode(@Nullable DiffNode diff) {
    mCurrentNestedTreeDiffNode = diff;
  }

  // Measure
  boolean hasNestedTreeDiffNodeSet() {
    return mCurrentNestedTreeDiffNode != null;
  }

  // Measure
  public @Nullable DiffNode consumeNestedTreeDiffNode() {
    final DiffNode node = mCurrentNestedTreeDiffNode;
    mCurrentNestedTreeDiffNode = null;
    return node;
  }

  // Create & measure - split by getting render / layout handlers
  TreeState getTreeState() {
    return Preconditions.checkNotNull(mTreeState);
  }

  LayoutPhaseMeasuredResultCache getCache() {
    return mCache;
  }

  // Used in bloks
  @Nullable
  public PerfEvent getPerfEvent() {
    return mPerfEvent;
  }

  // Pre-create
  public void setPerfEvent(@Nullable PerfEvent perfEvent) {
    mPerfEvent = perfEvent;
  }

  // Resume
  public void markLayoutResumed() {
    mThreadResumedOn.add(Thread.currentThread().getName());
  }

  // Resume
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

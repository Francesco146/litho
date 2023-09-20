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

import static com.facebook.litho.ContextUtils.getValidActivityForContext;
import static com.facebook.litho.LithoRenderUnit.getRenderUnit;
import static com.facebook.litho.LithoRenderUnit.isMountableView;
import static com.facebook.rendercore.MountState.ROOT_HOST_ID;

import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.collection.LongSparseArray;
import androidx.core.util.Preconditions;
import com.facebook.infer.annotation.Nullsafe;
import com.facebook.infer.annotation.ThreadSafe;
import com.facebook.litho.EndToEndTestingExtension.EndToEndTestingExtensionInput;
import com.facebook.litho.LithoViewAttributesExtension.ViewAttributesInput;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.rendercore.LayoutCache;
import com.facebook.rendercore.MountItemsPool;
import com.facebook.rendercore.MountState;
import com.facebook.rendercore.RenderTree;
import com.facebook.rendercore.RenderTreeNode;
import com.facebook.rendercore.Systracer;
import com.facebook.rendercore.incrementalmount.IncrementalMountExtensionInput;
import com.facebook.rendercore.incrementalmount.IncrementalMountOutput;
import com.facebook.rendercore.transitions.TransitionsExtensionInput;
import com.facebook.rendercore.visibility.VisibilityBoundsTransformer;
import com.facebook.rendercore.visibility.VisibilityExtensionInput;
import com.facebook.rendercore.visibility.VisibilityOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.CheckReturnValue;

/**
 * The main role of {@link LayoutState} is to hold the output of layout calculation. This includes
 * mountable outputs and visibility outputs. A centerpiece of the class is {@link
 * LithoReducer#setSizeAfterMeasureAndCollectResults(ComponentContext, LithoLayoutContext,
 * LayoutState)} which prepares the before-mentioned outputs based on the provided {@link LithoNode}
 * for later use in {@link MountState}.
 *
 * <p>This needs to be accessible to statically mock the class in tests.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class LayoutState
    implements IncrementalMountExtensionInput,
        VisibilityExtensionInput,
        TransitionsExtensionInput,
        EndToEndTestingExtensionInput,
        PotentiallyPartialResult,
        ViewAttributesInput {

  @Nullable private Transition.RootBoundsTransition mRootWidthAnimation;
  @Nullable private Transition.RootBoundsTransition mRootHeightAnimation;

  public static boolean isFromSyncLayout(@RenderSource int source) {
    switch (source) {
      case RenderSource.MEASURE_SET_SIZE_SPEC:
      case RenderSource.SET_ROOT_SYNC:
      case RenderSource.UPDATE_STATE_SYNC:
      case RenderSource.SET_SIZE_SPEC_SYNC:
      case RenderSource.RELOAD_PREVIOUS_STATE:
        return true;
      default:
        return false;
    }
  }

  private static final AtomicInteger sIdGenerator = new AtomicInteger(1);
  private static final int NO_PREVIOUS_LAYOUT_STATE_ID = -1;

  final Map<String, Rect> mComponentKeyToBounds = new HashMap<>();
  final Map<Handle, Rect> mComponentHandleToBounds = new HashMap<>();
  @Nullable List<ScopedComponentInfo> mScopedSpecComponentInfos;
  private @Nullable List<Pair<String, EventHandler<?>>> mCreatedEventHandlers;

  final ComponentContext mContext;

  private final Component mComponent;

  int mWidthSpec;
  int mHeightSpec;

  final List<RenderTreeNode> mMountableOutputs = new ArrayList<>(8);
  List<VisibilityOutput> mVisibilityOutputs;
  final LongSparseArray<Integer> mOutputsIdToPositionMap = new LongSparseArray<>(8);
  final Map<Long, ViewAttributes> mRenderUnitsWithViewAttributes = new HashMap<>(8);
  final Map<Long, IncrementalMountOutput> mIncrementalMountOutputs = new LinkedHashMap<>(8);
  final ArrayList<IncrementalMountOutput> mMountableOutputTops = new ArrayList<>();
  final ArrayList<IncrementalMountOutput> mMountableOutputBottoms = new ArrayList<>();
  final LongSparseArray<AnimatableItem> mAnimatableItems = new LongSparseArray<>(8);
  final Set<Long> mRenderUnitIdsWhichHostRenderTrees = new HashSet<>(4);
  private final Systracer mTracer = ComponentsSystrace.getSystrace();
  final @Nullable List<TestOutput> mTestOutputs;

  @Nullable LithoNode mRoot;
  @Nullable LithoLayoutResult mLayoutResult;
  @Nullable TransitionId mRootTransitionId;
  @Nullable String mRootComponentName;
  @Nullable LayoutCache.CachedData mLayoutCacheData;

  @Nullable DiffNode mDiffTreeRoot;

  int mWidth;
  int mHeight;

  int mCurrentX;
  int mCurrentY;

  final boolean mShouldGenerateDiffTree;
  private int mComponentTreeId = -1;
  final int mId;
  // Id of the layout state (if any) that was used in comparisons with this layout state.
  final int mPreviousLayoutStateId;

  private final boolean mIsAccessibilityEnabled;

  private final TreeState mTreeState;
  @Nullable List<ScopedComponentInfo> mScopedComponentInfosNeedingPreviousRenderData;
  @Nullable TransitionId mCurrentTransitionId;
  @Nullable OutputUnitsAffinityGroup<AnimatableItem> mCurrentLayoutOutputAffinityGroup;
  final Map<TransitionId, OutputUnitsAffinityGroup<AnimatableItem>> mTransitionIdMapping =
      new LinkedHashMap<>();
  final Set<TransitionId> mDuplicatedTransitionIds = new HashSet<>();
  @Nullable List<Transition> mTransitions;
  private @Nullable RenderTree mCachedRenderTree = null;

  @Nullable WorkingRangeContainer mWorkingRangeContainer;

  @Nullable List<Attachable> mAttachables;

  // If there is any component marked with 'ExcludeFromIncrementalMountComponent'
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public boolean mHasComponentsExcludedFromIncrementalMount;

  // TODO(t66287929): Remove mIsCommitted from LayoutState by matching RenderState logic around
  // Futures.
  private boolean mIsCommitted;

  private boolean mShouldProcessVisibilityOutputs;

  LayoutState(
      ComponentContext context,
      Component rootComponent,
      TreeState treeState,
      @Nullable List<Attachable> attachables,
      @Nullable List<Transition> transitions,
      @Nullable List<ScopedComponentInfo> componentsThatNeedPreviousRenderData,
      @Nullable LayoutState current,
      @Nullable LithoNode root,
      int widthSpec,
      int heightSpec,
      int componentTreeId,
      boolean isLayoutDiffingEnabled,
      boolean isAccessibilityEnabled) {
    mContext = context;
    mComponent = rootComponent;
    mId = sIdGenerator.getAndIncrement();
    mPreviousLayoutStateId = current != null ? current.mId : NO_PREVIOUS_LAYOUT_STATE_ID;
    mLayoutCacheData = current != null ? current.mLayoutCacheData : null;
    mTestOutputs = ComponentsConfiguration.isEndToEndTestRun ? new ArrayList<TestOutput>(8) : null;
    mScopedSpecComponentInfos = new ArrayList<>();
    mVisibilityOutputs = new ArrayList<>(8);

    mTreeState = treeState;
    mAttachables = attachables != null ? new ArrayList<>(attachables) : null;
    mTransitions = transitions != null ? new ArrayList<>(transitions) : null;
    mScopedComponentInfosNeedingPreviousRenderData =
        componentsThatNeedPreviousRenderData != null
            ? new ArrayList<>(componentsThatNeedPreviousRenderData)
            : null;
    mWidthSpec = widthSpec;
    mHeightSpec = heightSpec;
    mComponentTreeId = componentTreeId;
    mRootComponentName = rootComponent.getSimpleName();
    mShouldGenerateDiffTree = isLayoutDiffingEnabled;
    mRoot = root;
    mRootTransitionId = LithoNodeUtils.createTransitionId(root);
    mIsAccessibilityEnabled = isAccessibilityEnabled;
  }

  @VisibleForTesting
  Component getRootComponent() {
    return mComponent;
  }

  @Override
  public boolean isPartialResult() {
    return false;
  }

  Map<String, Rect> getComponentKeyToBounds() {
    return mComponentKeyToBounds;
  }

  Map<Handle, Rect> getComponentHandleToBounds() {
    return mComponentHandleToBounds;
  }

  Set<Handle> getComponentHandles() {
    return mComponentHandleToBounds.keySet();
  }

  @Nullable
  List<ScopedComponentInfo> consumeScopedSpecComponentInfos() {
    final List<ScopedComponentInfo> scopedSpecComponentInfos = mScopedSpecComponentInfos;
    mScopedSpecComponentInfos = null;

    return scopedSpecComponentInfos;
  }

  void setCreatedEventHandlers(@Nullable List<Pair<String, EventHandler<?>>> createdEventHandlers) {
    mCreatedEventHandlers = createdEventHandlers;
  }

  @Nullable
  List<Pair<String, EventHandler<?>>> consumeCreatedEventHandlers() {
    final List<Pair<String, EventHandler<?>>> createdEventHandlers = mCreatedEventHandlers;
    mCreatedEventHandlers = null;

    return createdEventHandlers;
  }

  @Nullable
  List<Attachable> getAttachables() {
    return mAttachables;
  }

  /** @return true, means there are components marked as 'ExcludeFromIncrementalMount'. */
  boolean hasComponentsExcludedFromIncrementalMount() {
    return mHasComponentsExcludedFromIncrementalMount;
  }

  RenderTree toRenderTree() {
    if (mCachedRenderTree != null) {
      return mCachedRenderTree;
    }

    final RenderTreeNode root;

    if (mMountableOutputs.isEmpty()) {
      LithoReducer.addRootHostRenderTreeNode(this, null, null);
    }

    root = mMountableOutputs.get(0);

    if (root.getRenderUnit().getId() != ROOT_HOST_ID) {
      throw new IllegalStateException(
          "Root render unit has invalid id " + root.getRenderUnit().getId());
    }

    RenderTreeNode[] flatList = new RenderTreeNode[mMountableOutputs.size()];
    for (int i = 0, size = mMountableOutputs.size(); i < size; i++) {
      flatList[i] = mMountableOutputs.get(i);
    }

    final RenderTree renderTree =
        new RenderTree(root, flatList, mWidthSpec, mHeightSpec, mComponentTreeId, null, null);
    mCachedRenderTree = renderTree;

    return renderTree;
  }

  static String layoutSourceToString(@RenderSource int source) {
    switch (source) {
      case RenderSource.SET_ROOT_SYNC:
        return "setRootSync";
      case RenderSource.SET_SIZE_SPEC_SYNC:
        return "setSizeSpecSync";
      case RenderSource.UPDATE_STATE_SYNC:
        return "updateStateSync";
      case RenderSource.SET_ROOT_ASYNC:
        return "setRootAsync";
      case RenderSource.SET_SIZE_SPEC_ASYNC:
        return "setSizeSpecAsync";
      case RenderSource.UPDATE_STATE_ASYNC:
        return "updateStateAsync";
      case RenderSource.MEASURE_SET_SIZE_SPEC:
        return "measure_setSizeSpecSync";
      case RenderSource.MEASURE_SET_SIZE_SPEC_ASYNC:
        return "measure_setSizeSpecAsync";
      case RenderSource.TEST:
        return "test";
      case RenderSource.RELOAD_PREVIOUS_STATE:
        return "reloadState";
      case RenderSource.NONE:
        return "none";
      default:
        throw new RuntimeException("Unknown calculate layout source: " + source);
    }
  }

  @ThreadSafe(enableChecks = false)
  void preAllocateMountContent(boolean shouldPreallocatePerMountSpec) {
    if (!shouldPreallocatePerMountSpec) {
      return;
    }

    final boolean isTracing = ComponentsSystrace.isTracing();
    if (isTracing) {
      ComponentsSystrace.beginSection("preAllocateMountContent:" + mComponent.getSimpleName());
    }

    if (!mMountableOutputs.isEmpty()) {
      for (int i = 0, size = mMountableOutputs.size(); i < size; i++) {
        final RenderTreeNode treeNode = mMountableOutputs.get(i);
        final Component component = getRenderUnit(treeNode).getComponent();

        if (!isSpecGeneratedComponentThatCanPreallocate(component)
            && !isPrimitiveThatCanPreallocate(treeNode)) {
          continue;
        }

        if (ComponentsConfiguration.componentPreallocationBlocklist != null
            && ComponentsConfiguration.componentPreallocationBlocklist.contains(
                component.getSimpleName())) {
          continue;
        }

        if (ComponentsConfiguration.enableDrawablePreAllocation
            || isMountableView(treeNode.getRenderUnit())) {
          if (isTracing) {
            ComponentsSystrace.beginSection("preAllocateMountContent:" + component.getSimpleName());
          }

          boolean preallocated =
              MountItemsPool.maybePreallocateContent(
                  mContext.getAndroidContext(), treeNode.getRenderUnit().getContentAllocator());

          Log.d(
              "LayoutState",
              "Preallocation of"
                  + component.getSimpleName()
                  + (preallocated ? " succeeded" : " failed"));

          if (isTracing) {
            ComponentsSystrace.endSection();
          }
        }
      }
    }

    if (isTracing) {
      ComponentsSystrace.endSection();
    }
  }

  private boolean isPrimitiveThatCanPreallocate(RenderTreeNode treeNode) {
    return treeNode.getRenderUnit() instanceof PrimitiveLithoRenderUnit
        && ((PrimitiveLithoRenderUnit) treeNode.getRenderUnit())
            .getPrimitiveRenderUnit()
            .getContentAllocator()
            .canPreallocate();
  }

  private boolean isSpecGeneratedComponentThatCanPreallocate(Component component) {
    return component instanceof SpecGeneratedComponent
        && ((SpecGeneratedComponent) component).canPreallocate();
  }

  boolean isActivityValid() {
    return getValidActivityForContext(mContext.getAndroidContext()) != null;
  }

  @VisibleForTesting
  static @OutputUnitType int getTypeFromId(long id) {
    long masked = id & 0x00000000_00000000_FFFFFFFF_00000000L;
    return (int) (masked >> 32);
  }

  boolean isCompatibleSpec(int widthSpec, int heightSpec) {
    final boolean widthIsCompatible =
        MeasureComparisonUtils.isMeasureSpecCompatible(mWidthSpec, widthSpec, mWidth);

    final boolean heightIsCompatible =
        MeasureComparisonUtils.isMeasureSpecCompatible(mHeightSpec, heightSpec, mHeight);

    return widthIsCompatible && heightIsCompatible;
  }

  boolean isCompatibleComponentAndSpec(int componentId, int widthSpec, int heightSpec) {
    return mComponent.getId() == componentId && isCompatibleSpec(widthSpec, heightSpec);
  }

  boolean isCompatibleSize(int width, int height) {
    return mWidth == width && mHeight == height;
  }

  boolean isForComponentId(int componentId) {
    return mComponent.getId() == componentId;
  }

  @Override
  public int getMountableOutputCount() {
    return mMountableOutputs.size();
  }

  @Override
  public int getIncrementalMountOutputCount() {
    return mIncrementalMountOutputs.size();
  }

  @Override
  public RenderTreeNode getMountableOutputAt(int index) {
    return mMountableOutputs.get(index);
  }

  @Override
  public Map<Long, ViewAttributes> getViewAttributes() {
    return mRenderUnitsWithViewAttributes;
  }

  @Override
  public @Nullable IncrementalMountOutput getIncrementalMountOutputForId(long id) {
    return mIncrementalMountOutputs.get(id);
  }

  @Override
  public Collection<IncrementalMountOutput> getIncrementalMountOutputs() {
    return mIncrementalMountOutputs.values();
  }

  @Override
  public @Nullable AnimatableItem getAnimatableRootItem() {
    return mAnimatableItems.get(ROOT_HOST_ID);
  }

  @Override
  public @Nullable AnimatableItem getAnimatableItem(long id) {
    return mAnimatableItems.get(id);
  }

  public ArrayList<IncrementalMountOutput> getOutputsOrderedByTopBounds() {
    return mMountableOutputTops;
  }

  public ArrayList<IncrementalMountOutput> getOutputsOrderedByBottomBounds() {
    return mMountableOutputBottoms;
  }

  int getVisibilityOutputCount() {
    return mVisibilityOutputs.size();
  }

  VisibilityOutput getVisibilityOutputAt(int index) {
    return mVisibilityOutputs.get(index);
  }

  @Override
  public List<VisibilityOutput> getVisibilityOutputs() {
    return mVisibilityOutputs;
  }

  @Override
  public int getTestOutputCount() {
    return mTestOutputs == null ? 0 : mTestOutputs.size();
  }

  @Nullable
  @Override
  public TestOutput getTestOutputAt(int index) {
    return mTestOutputs == null ? null : mTestOutputs.get(index);
  }

  public synchronized @Nullable DiffNode getDiffTree() {
    return mDiffTreeRoot;
  }

  int getWidth() {
    return mWidth;
  }

  int getHeight() {
    return mHeight;
  }

  int getWidthSpec() {
    return mWidthSpec;
  }

  int getHeightSpec() {
    return mHeightSpec;
  }

  @Override
  public int getTreeId() {
    return getComponentTreeId();
  }

  /** @return The id of the {@link ComponentTree} that generated this {@link LayoutState} */
  public int getComponentTreeId() {
    return mComponentTreeId;
  }

  /** Id of this {@link LayoutState}. */
  int getId() {
    return mId;
  }

  /**
   * Id of the {@link LayoutState} that was compared to when calculating this {@link LayoutState}.
   */
  int getPreviousLayoutStateId() {
    return mPreviousLayoutStateId;
  }

  public ComponentContext getComponentContext() {
    return mContext;
  }

  boolean isAccessibilityEnabled() {
    return mIsAccessibilityEnabled;
  }

  /**
   * Returns the state handler instance currently held by LayoutState.
   *
   * @return the state handler
   */
  @CheckReturnValue
  TreeState getTreeState() {
    return mTreeState;
  }

  @Nullable
  @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
  public LithoNode getLayoutRoot() {
    return mRoot;
  }

  public @Nullable LithoLayoutResult getRootLayoutResult() {
    return mLayoutResult;
  }

  // If the layout root is a nested tree holder node, it gets skipped immediately while
  // collecting the LayoutOutputs. The nested tree itself effectively becomes the layout
  // root in this case.
  boolean isLayoutRoot(LithoLayoutResult result) {
    return mLayoutResult instanceof NestedTreeHolderResult
        ? result == ((NestedTreeHolderResult) mLayoutResult).getNestedResult()
        : result == mLayoutResult;
  }

  /**
   * @return the position of the {@link LithoRenderUnit} with id layoutOutputId in the {@link
   *     LayoutState} list of outputs or -1 if no {@link LithoRenderUnit} with that id exists in the
   *     {@link LayoutState}
   */
  @Override
  public int getPositionForId(long layoutOutputId) {
    return Preconditions.checkNotNull(mOutputsIdToPositionMap.get(layoutOutputId, -1));
  }

  @Override
  public boolean renderUnitWithIdHostsRenderTrees(long id) {
    return mRenderUnitIdsWhichHostRenderTrees.contains(id);
  }

  @Override
  public Set<Long> getRenderUnitIdsWhichHostRenderTrees() {
    return mRenderUnitIdsWhichHostRenderTrees;
  }

  @Override
  @Nullable
  public List<Transition> getTransitions() {
    return mTransitions;
  }

  /** Gets a mapping from transition ids to a group of LayoutOutput. */
  @Override
  public Map<TransitionId, OutputUnitsAffinityGroup<AnimatableItem>> getTransitionIdMapping() {
    return mTransitionIdMapping;
  }

  /** Gets a group of LayoutOutput given transition key */
  @Override
  @Nullable
  public OutputUnitsAffinityGroup<AnimatableItem> getAnimatableItemForTransitionId(
      TransitionId transitionId) {
    return mTransitionIdMapping.get(transitionId);
  }

  @Nullable
  public List<ScopedComponentInfo> getScopedComponentInfosNeedingPreviousRenderData() {
    return mScopedComponentInfosNeedingPreviousRenderData;
  }

  @Override
  public void setInitialRootBoundsForAnimation(
      @Nullable Transition.RootBoundsTransition rootWidth,
      @Nullable Transition.RootBoundsTransition rootHeight) {
    mRootWidthAnimation = rootWidth;
    mRootHeightAnimation = rootHeight;
  }

  @Nullable
  @Override
  public List<Transition> getMountTimeTransitions() {
    if (mTreeState == null) {
      return null;
    }
    mTreeState.applyPreviousRenderData(this);

    List<Transition> mountTimeTransitions = null;

    if (mScopedComponentInfosNeedingPreviousRenderData != null) {
      mountTimeTransitions = new ArrayList<>();
      for (int i = 0, size = mScopedComponentInfosNeedingPreviousRenderData.size(); i < size; i++) {
        final ScopedComponentInfo scopedComponentInfo =
            mScopedComponentInfosNeedingPreviousRenderData.get(i);
        final ComponentContext scopedContext = scopedComponentInfo.getContext();
        final Component component = scopedComponentInfo.getComponent();
        try {
          final Transition transition =
              (component instanceof SpecGeneratedComponent)
                  ? ((SpecGeneratedComponent) component).createTransition(scopedContext)
                  : null;
          if (transition != null) {
            mountTimeTransitions.add(transition);
          }
        } catch (Exception e) {
          ComponentUtils.handleWithHierarchy(scopedContext, component, e);
        }
      }
    }

    final List<Transition> updateStateTransitions = mTreeState.getPendingStateUpdateTransitions();
    if (updateStateTransitions != null) {
      if (mountTimeTransitions == null) {
        mountTimeTransitions = new ArrayList<>();
      }
      mountTimeTransitions.addAll(updateStateTransitions);
    }

    return mountTimeTransitions;
  }

  @Override
  public boolean isIncrementalMountEnabled() {
    return mContext != null && ComponentContext.isIncrementalMountEnabled(mContext);
  }

  @Override
  public Systracer getTracer() {
    return mTracer;
  }

  @Override
  @Nullable
  public TransitionId getRootTransitionId() {
    return mRootTransitionId;
  }

  /** Debug-only: return a string representation of this LayoutState and its LayoutOutputs. */
  String dumpAsString() {
    if (!ComponentsConfiguration.isDebugModeEnabled && !ComponentsConfiguration.isEndToEndTestRun) {
      throw new RuntimeException(
          "LayoutState#dumpAsString() should only be called in debug mode or from e2e tests!");
    }

    String res =
        "LayoutState w/ "
            + getMountableOutputCount()
            + " mountable outputs, root: "
            + mRootComponentName
            + "\n";

    for (int i = 0; i < getMountableOutputCount(); i++) {
      final RenderTreeNode node = getMountableOutputAt(i);
      final LithoRenderUnit renderUnit = getRenderUnit(node);
      res +=
          "  ["
              + i
              + "] id: "
              + node.getRenderUnit().getId()
              + ", host: "
              + (node.getParent() != null ? node.getParent().getRenderUnit().getId() : -1)
              + ", component: "
              + renderUnit.getComponent().getSimpleName()
              + "\n";
    }

    return res;
  }

  void checkWorkingRangeAndDispatch(
      int position,
      int firstVisibleIndex,
      int lastVisibleIndex,
      int firstFullyVisibleIndex,
      int lastFullyVisibleIndex,
      WorkingRangeStatusHandler stateHandler) {
    if (mWorkingRangeContainer == null) {
      return;
    }

    mWorkingRangeContainer.checkWorkingRangeAndDispatch(
        position,
        firstVisibleIndex,
        lastVisibleIndex,
        firstFullyVisibleIndex,
        lastFullyVisibleIndex,
        stateHandler);
  }

  void dispatchOnExitRangeIfNeeded(WorkingRangeStatusHandler stateHandler) {
    if (mWorkingRangeContainer == null) {
      return;
    }

    mWorkingRangeContainer.dispatchOnExitedRangeIfNeeded(stateHandler);
  }

  @Override
  public boolean needsToRerunTransitions() {
    return mContext.getStateUpdater().isFirstMount();
  }

  @Override
  public void setNeedsToRerunTransitions(boolean needsToRerunTransitions) {
    mContext.getStateUpdater().setFirstMount(needsToRerunTransitions);
  }

  boolean isCommitted() {
    return mIsCommitted;
  }

  void markCommitted() {
    mIsCommitted = true;
  }

  @Override
  public boolean isProcessingVisibilityOutputsEnabled() {
    return mShouldProcessVisibilityOutputs;
  }

  public void setShouldProcessVisibilityOutputs(boolean value) {
    mShouldProcessVisibilityOutputs = value;
  }

  @Override
  public @Nullable String getRootName() {
    return mRootComponentName;
  }

  RenderTreeNode getRenderTreeNode(IncrementalMountOutput output) {
    return getMountableOutputAt(output.getIndex());
  }

  @Nullable
  public Transition.RootBoundsTransition getRootHeightAnimation() {
    return mRootHeightAnimation;
  }

  @Nullable
  public Transition.RootBoundsTransition getRootWidthAnimation() {
    return mRootWidthAnimation;
  }

  @Override
  @Nullable
  public VisibilityBoundsTransformer getVisibilityBoundsTransformer() {
    return getComponentContext().getVisibilityBoundsTransformer();
  }
}

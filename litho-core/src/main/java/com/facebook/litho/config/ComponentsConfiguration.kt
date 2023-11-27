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

package com.facebook.litho.config

import android.os.Build
import com.facebook.litho.BuildConfig
import com.facebook.litho.ComponentsLogger
import com.facebook.litho.perfboost.LithoPerfBoosterFactory
import com.facebook.rendercore.incrementalmount.IncrementalMountExtensionConfigs

/**
 * Hi there, traveller! This configuration class is not meant to be used by end-users of Litho. It
 * contains mainly flags for features that are either under active development and not ready for
 * public consumption, or for use in experiments.
 *
 * These values are safe defaults and should not require manual changes.
 *
 * This class hosts all the config parameters that the ComponentTree configures it self .... enable
 * and disable features ... A Component tree uses the [.defaultComponentsConfiguration] by default
 * but a [Builder] can be used to create new instances of the config to override the default
 * parameters ... The default config values can also be overridden by manually setting their values
 * in [.defaultBuilder]
 */
data class ComponentsConfiguration
internal constructor(
    /**
     * This determines if the [ComponentTree] attached to this configuration, will attempt to detect
     * and ignore duplicate state updates coming from usages in the Specs API.
     */
    @JvmField val specsApiStateUpdateDuplicateDetectionEnabled: Boolean = false,
    val useCancellableLayoutFutures: Boolean = true,
    val useInterruptibleResolution: Boolean = true,
    val shouldCacheLayouts: Boolean = true,
    val disableNestedTreeCaching: Boolean = false,
    val shouldAddHostViewForRootComponent: Boolean = false,
    @JvmField
    val useIncrementalMountGapWorker: Boolean = IncrementalMountExtensionConfigs.useGapWorker,
    @JvmField val nestedPreallocationEnabled: Boolean = false,
    val useNonRebindingEventHandlers: Boolean = false,
    internal val shouldDisableBgFgOutputs: Boolean = false,
) {

  val shouldAddRootHostViewOrDisableBgFgOutputs: Boolean =
      shouldAddHostViewForRootComponent || shouldDisableBgFgOutputs

  companion object {

    @JvmField var defaultInstance: ComponentsConfiguration = ComponentsConfiguration()

    /**
     * Indicates whether this is an internal build. Note that the implementation of `BuildConfig ` *
     * that this class is compiled against may not be the one that is included in the APK. See:
     * [android_build_config](http://facebook.github.io/buck/rule/android_build_config.html).
     */
    @JvmField val IS_INTERNAL_BUILD: Boolean = BuildConfig.IS_INTERNAL_BUILD

    /** Indicates that the incremental mount helper is required for this build. */
    @JvmField val USE_INCREMENTAL_MOUNT_HELPER: Boolean = BuildConfig.USE_INCREMENTAL_MOUNT_HELPER

    /** Whether we can access properties in Settings.Global for animations. */
    val CAN_CHECK_GLOBAL_ANIMATOR_SETTINGS: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1

    /** Whether we need to account for lack of synchronization while accessing Themes. */
    @JvmField
    val NEEDS_THEME_SYNCHRONIZATION: Boolean =
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1

    /** The default priority for threads that perform background layout calculations. */
    @JvmField var DEFAULT_BACKGROUND_THREAD_PRIORITY: Int = 5

    /**
     * This makes [ComponentContext] to use the ResourcesResolver when retrieving color, string,
     * etc.
     */
    @JvmField var forceResourcesResolverUsage: Boolean = false

    /**
     * The default priority for threads that perform background sections change set calculations.
     */
    const val DEFAULT_CHANGE_SET_THREAD_PRIORITY: Int = 0

    @JvmField var isDebugModeEnabled: Boolean = IS_INTERNAL_BUILD

    /**
     * Option to enabled debug mode. This will save extra data associated with each node and allow
     * more info about the hierarchy to be retrieved. Used to enable stetho integration. It is
     * highly discouraged to enable this in production builds. Due to how the Litho releases are
     * distributed in open source IS_INTERNAL_BUILD will always be false. It is therefore required
     * to override this value using your own application build configs. Recommended place for this
     * is in a Application subclass onCreate() method.
     */
    @JvmField var isRenderInfoDebuggingEnabled: Boolean = isDebugModeEnabled

    /** Lightweight tracking of component class hierarchy of MountItems. */
    @JvmField var isDebugHierarchyEnabled: Boolean = false

    /**
     * Populates additional metadata to find mounted components at runtime. Defaults to the presence
     * of an
     *
     * ```
     * IS_TESTING
     * ```
     *
     * system property at startup but can be overridden at runtime.
     */
    @JvmField var isEndToEndTestRun = System.getProperty("IS_TESTING") != null
    @JvmField var isAnimationDisabled = "true" == System.getProperty("litho.animation.disabled")

    /**
     * By default end-to-end tests will disable transitions and this flag lets to explicitly enable
     * transitions to test animation related behavior.
     */
    @JvmField var forceEnableTransitionsForInstrumentationTests: Boolean = false

    /**
     * If non-null, a thread pool will be used for async layouts instead of a single layout thread.
     */
    @JvmField var threadPoolConfiguration: LayoutThreadPoolConfiguration? = null
    @JvmField var enableThreadTracingStacktrace: Boolean = false

    /** Sets if is reconciliation is enabled */
    @JvmField var isReconciliationEnabled: Boolean = true

    /**
     * The LazyList is having a default of `false` for the reconciliation being enabled. Ideally, it
     * would default to whatever value is used in its ComponentContext. By enabling this setting,
     * the default will be retrieve via the ComponentContext.
     */
    @JvmField var isLazyListUsingComponentContextReconciliationConfig: Boolean = true
    @JvmField var overrideReconciliation: Boolean? = null
    @JvmField var overrideLayoutDiffing: Boolean? = null

    /**
     * Sets if layout diffing is enabled. This should be used in conjugation with
     * {@link#isReconciliationEnabled}.
     */
    @JvmField var isLayoutDiffingEnabled: Boolean = true
    @JvmField var runLooperPrepareForLayoutThreadFactory: Boolean = true
    @JvmField var enableDrawablePreAllocation: Boolean = false

    @JvmField var perfBoosterFactory: LithoPerfBoosterFactory? = null

    /**
     * If true, the [.perfBoosterFactory] will be used to indicate that LayoutStateFuture thread can
     * use the perf boost
     */
    @JvmField var boostPerfLayoutStateFuture: Boolean = false

    /**
     * Start parallel layout of visible range just before serial synchronous layouts in
     * RecyclerBinder
     */
    @JvmField var computeRangeOnSyncLayout: Boolean = false

    /**
     * When true, IM will not stop when the LithoView's visible rect is empty, and will proceed to
     * unmount everything.
     */
    @JvmField var shouldContinueIncrementalMountWhenVisibileRectIsEmpty: Boolean = false

    /** When `true`, disables incremental mount globally. */
    @JvmField var isIncrementalMountGloballyDisabled: Boolean = false

    /** Keeps the litho layout result tree in the LayoutState. This will increase memory use. */
    @JvmField var keepLayoutResults: Boolean = false

    /**
     * Used by LithoViews to determine whether or not to self-manage the view-port changes, rather
     * than rely on calls to notifyVisibleBoundsChanged.
     */
    @JvmField var lithoViewSelfManageViewPortChanges: Boolean = false
    @JvmField var emitMessageForZeroSizedTexture: Boolean = false
    @JvmField var textureSizeWarningLimit: Int = Int.MAX_VALUE
    @JvmField var overlappingRenderingViewSizeLimit: Int = Int.MAX_VALUE
    @JvmField var partialAlphaWarningSizeThresold: Int = Int.MAX_VALUE

    /** Initialize sticky header during layout when its component tree is null */
    @JvmField var initStickyHeaderInLayoutWhenComponentTreeIsNull: Boolean = false
    @JvmField var unsafeHostComponentRecyclingIsEnabled: Boolean = false

    /** Whether a [ComponentHost] can be pre-allocated */
    @JvmField var isHostComponentPreallocationEnabled: Boolean = false
    @JvmField var hostComponentPoolSize: Int = 30

    /** When `true` ComponentTree records state change snapshots */
    @JvmField var isTimelineEnabled: Boolean = isRenderInfoDebuggingEnabled
    @JvmField var timelineDocsLink: String? = null
    @JvmField var enableIsBoringLayoutCheckTimeout: Boolean = false

    /** Skip checking for root component and tree-props while layout */
    @JvmField var isSkipRootCheckingEnabled: Boolean = false
    @JvmField var enableComputeLayoutAsyncAfterInsertion: Boolean = true
    @JvmField var shouldCompareCommonPropsInIsEquivalentTo: Boolean = false
    @JvmField var shouldCompareRootCommonPropsInSingleComponentSection: Boolean = false
    @JvmField var shouldDelegateContentDescriptionChangeEvent: Boolean = false
    @JvmField var forceDelegateViewBinder: Boolean = false

    /** This toggles whether {@Link #LayoutThreadPoolExecutor} should timeout core threads or not */
    @JvmField var shouldAllowCoreThreadTimeout: Boolean = false
    @JvmField var layoutThreadKeepAliveTimeMs: Long = 1_000
    @JvmField var crashIfExceedingStateUpdateThreshold: Boolean = false
    @JvmField var enableRecyclerBinderStableId: Boolean = true
    @JvmField var recyclerBinderStrategy: Int = 0
    @JvmField var enableMountableRecycler: Boolean = false
    @JvmField var enableMountableTwoBindersRecycler: Boolean = false
    @JvmField var enableSeparateAnimatorBinder: Boolean = false
    @JvmField var enableMountableRecyclerInGroups: Boolean = false
    @JvmField var hostComponentAlwaysShouldUpdate: Boolean = true
    @JvmField var shouldOverrideHasTransientState: Boolean = false
    @JvmField var enableFixForNestedComponentTree: Boolean = false
    @JvmField var reduceMemorySpikeUserSession: Boolean = false
    @JvmField var reduceMemorySpikeDataDiffSection: Boolean = false
    @JvmField var reduceMemorySpikeGetUri: Boolean = false
    @JvmField var bindOnSameComponentTree: Boolean = true
    @JvmField var enableStateUpdatesBatching: Boolean = true
    @JvmField var componentsLogger: ComponentsLogger? = null

    /** Debug option to highlight interactive areas in mounted components. */
    @JvmField var debugHighlightInteractiveBounds: Boolean = false

    /** Debug option to highlight mount bounds of mounted components. */
    @JvmField var debugHighlightMountBounds: Boolean = false
    @JvmField var isEventHandlerRebindLoggingEnabled: Boolean = false
    @JvmField var eventHandlerRebindLoggingSamplingRate: Int = 0

    /**
     * This method is only used so that Java clients can have a builder like approach to override a
     * configuration.
     */
    @JvmStatic fun create(): Builder = create(defaultInstance)

    @JvmStatic
    fun create(configuration: ComponentsConfiguration): Builder = Builder(configuration.copy())
  }

  /**
   * This is a builder that only exists so that Java clients can have an easier time creating and
   * overriding specific configurations. For Kotlin one can use directly the named parameters on the
   * [ComponentsConfiguration] constructor.
   */
  class Builder internal constructor(private var baseConfig: ComponentsConfiguration) {

    fun useCancellableLayoutFutures(enabled: Boolean) = also {
      baseConfig = baseConfig.copy(useCancellableLayoutFutures = enabled)
    }

    fun shouldAddHostViewForRootComponent(enabled: Boolean) = also {
      baseConfig = baseConfig.copy(shouldAddHostViewForRootComponent = enabled)
    }

    fun nestedPreallocationEnabled(enabled: Boolean) = also {
      baseConfig = baseConfig.copy(nestedPreallocationEnabled = enabled)
    }

    fun shouldCacheLayouts(enabled: Boolean) = also {
      baseConfig = baseConfig.copy(shouldCacheLayouts = enabled)
    }

    fun specsApiStateUpdateDetectionEnabled(enabled: Boolean) = also {
      baseConfig = baseConfig.copy(specsApiStateUpdateDuplicateDetectionEnabled = enabled)
    }

    fun build(): ComponentsConfiguration {
      return baseConfig
    }
  }
}

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

package com.facebook.litho

import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.facebook.litho.AccessibilityUtils.isAccessibilityEnabled
import com.facebook.litho.ComponentsSystrace.beginSectionWithArgs
import com.facebook.litho.ComponentsSystrace.endSection
import com.facebook.litho.Layout.measurePendingSubtrees
import com.facebook.litho.Layout.measureTree
import com.facebook.litho.LithoReducer.setSizeAfterMeasureAndCollectResults
import com.facebook.litho.SizeSpec.toString
import com.facebook.litho.debug.DebugOverlay
import com.facebook.litho.debug.DebugOverlay.Companion.updateLayoutHistory
import com.facebook.litho.debug.LithoDebugEvent
import com.facebook.litho.debug.LithoDebugEventAttributes
import com.facebook.litho.stats.LithoStats
import com.facebook.rendercore.LayoutCache
import com.facebook.rendercore.debug.DebugEventAttribute
import com.facebook.rendercore.debug.DebugEventDispatcher.beginTrace
import com.facebook.rendercore.debug.DebugEventDispatcher.endTrace
import com.facebook.rendercore.debug.DebugEventDispatcher.generateTraceIdentifier

class LayoutTreeFuture(
    private val resolveResult: ResolveResult,
    private val currentLayoutState: LayoutState?,
    private val diffTreeRoot: DiffNode?,
    private val perfEvent: PerfEvent?,
    private val widthSpec: Int,
    private val heightSpec: Int,
    private val treeId: Int,
    private val version: Int,
    private val isLayoutDiffingEnabled: Boolean,
    @RenderSource private val source: Int
) : TreeFuture<LayoutState>(false) {

  override fun getDescription(): String = "layout"

  override fun getVersion(): Int = version

  override fun calculate(): LayoutState {
    return layout(
        resolveResult,
        widthSpec,
        heightSpec,
        version,
        treeId,
        isLayoutDiffingEnabled,
        currentLayoutState,
        diffTreeRoot,
        this,
        perfEvent)
  }

  override fun resumeCalculation(partialResult: LayoutState): LayoutState {
    throw UnsupportedOperationException("LayoutTreeFuture cannot be resumed.")
  }

  override fun isEquivalentTo(that: TreeFuture<*>?): Boolean {
    if (that !is LayoutTreeFuture) {
      return false
    }
    return widthSpec == that.widthSpec &&
        heightSpec == that.heightSpec &&
        resolveResult == that.resolveResult
  }

  companion object {
    /** Function to calculate a new layout. */
    fun layout(
        resolveResult: ResolveResult,
        widthSpec: Int,
        heightSpec: Int,
        version: Int,
        treeId: Int,
        isLayoutDiffingEnabled: Boolean,
        currentLayoutState: LayoutState?,
        diffTreeRoot: DiffNode?,
        future: TreeFuture<*>?,
        perfEvent: PerfEvent?
    ): LayoutState {

      LithoStats.incrementLayoutCount()

      val isTracing = ComponentsSystrace.isTracing
      val traceId = generateTraceIdentifier(LithoDebugEvent.ComponentTreeResolve)
      if (traceId != null) {
        val attributes: MutableMap<String, Any> = LinkedHashMap()
        attributes[LithoDebugEventAttributes.Root] = resolveResult.component.simpleName
        attributes[DebugEventAttribute.Version] = version
        beginTrace(traceId, LithoDebugEvent.Layout, traceId.toString(), attributes)
      }

      val treeState = resolveResult.treeState

      return try {

        if (isTracing) {
          beginSectionWithArgs("layoutTree:" + resolveResult.component.simpleName)
              .arg("treeId", treeId)
              .arg("rootId", resolveResult.component.id)
              .arg("widthSpec", toString(widthSpec))
              .arg("heightSpec", toString(heightSpec))
              .flush()
        }

        treeState.registerLayoutState()

        val node = resolveResult.node
        val renderPhaseCache = resolveResult.consumeCache()
        val c = resolveResult.context
        val layoutCache =
            if (currentLayoutState != null) {
              LayoutCache(currentLayoutState.mLayoutCacheData)
            } else {
              LayoutCache()
            }
        val lsc =
            LithoLayoutContext(
                treeId,
                MeasuredResultCache(renderPhaseCache),
                c,
                treeState,
                version,
                resolveResult.component.id,
                isAccessibilityEnabled(
                    c.androidContext.getSystemService(Context.ACCESSIBILITY_SERVICE)
                        as AccessibilityManager),
                layoutCache,
                diffTreeRoot,
                future)
        val layoutState =
            LayoutState(
                resolveResult,
                widthSpec,
                heightSpec,
                treeId,
                isLayoutDiffingEnabled,
                lsc.isAccessibilityEnabled,
                currentLayoutState,
            )

        if (perfEvent != null) {
          lsc.perfEvent = perfEvent
        }

        val prevContext = c.calculationStateContext

        try {

          c.setLithoLayoutContext(lsc)
          val root = measureTree(lsc, c.androidContext, node, widthSpec, heightSpec, perfEvent)
          if (root != null) {
            measurePendingSubtrees(c, root, layoutState, lsc)
          }

          layoutState.mLayoutResult = root
          layoutState.mLayoutCacheData = layoutCache.writeCacheData

          perfEvent?.markerPoint("start_collect_results")
          setSizeAfterMeasureAndCollectResults(c, lsc, layoutState)
          perfEvent?.markerPoint("end_collect_results")

          root?.releaseLayoutPhaseData()

          layoutState.setCreatedEventHandlers(
              mergeLists(resolveResult.eventHandlers, lsc.eventHandlers))
        } finally {
          c.calculationStateContext = prevContext
          lsc.release()
        }

        LithoStats.incrementComponentCalculateLayoutCount()

        if (ThreadUtils.isMainThread()) {
          LithoStats.incrementComponentCalculateLayoutOnUICount()
        }

        if (DebugOverlay.isEnabled) {
          updateLayoutHistory(treeId)
        }

        return layoutState
      } finally {

        treeState.unregisterLayoutInitialState()

        if (isTracing) {
          endSection()
        }

        if (traceId != null) {
          endTrace(traceId)
        }
      }
    }
  }
}

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

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Pair
import androidx.annotation.Px
import com.facebook.litho.drawable.BorderColorDrawable
import com.facebook.rendercore.FastMath
import com.facebook.rendercore.LayoutContext
import com.facebook.rendercore.LayoutResult
import com.facebook.rendercore.MeasureResult
import com.facebook.rendercore.primitives.utils.hasEquivalentFields
import com.facebook.rendercore.utils.MeasureSpecUtils
import com.facebook.yoga.YogaConstants
import com.facebook.yoga.YogaDirection
import com.facebook.yoga.YogaEdge
import com.facebook.yoga.YogaMeasureOutput
import com.facebook.yoga.YogaNode
import java.util.ArrayList

/**
 * This is the default implementation of a [LayoutResult] for Litho. This holds a reference to the
 * [LithoNode] which created it, its [YogaNode], and a list of its children.
 */
open class LithoLayoutResult(
    val context: ComponentContext,
    open val node: LithoNode,
    val yogaNode: YogaNode,
    widthFromStyle: Float = YogaConstants.UNDEFINED,
    heightFromStyle: Float = YogaConstants.UNDEFINED,
) : ComponentLayout, LayoutResult {

  private val children: MutableList<LithoLayoutResult> = ArrayList()
  private val _layoutData: Any? by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        /*

         Ideally the layout data should be created when measure is called on the mount spec or
         primitive component, but because of the current implementation of mount specs, and the way
         Yoga works it a possibility that measure may not be called, and a MountSpec [may] require
         inter stage props, then it is necessary to have a non-null InterStagePropsContainer even if
         the values are uninitialised. Otherwise it will lead to NPEs.

         This should get cleaned up once the implementation is general enough for PrimitiveComponents.

        */
        val component = node.tailComponent
        return@lazy if (component is SpecGeneratedComponent) {
          component.createInterStagePropsContainer()
        } else {
          null
        }
      }
  private var layoutData: Any? = _layoutData
  private var isCachedLayout = false
  private var lastMeasuredSize = Long.MIN_VALUE

  var widthSpec: Int = DiffNode.UNSPECIFIED
    private set

  var heightSpec: Int = DiffNode.UNSPECIFIED
    private set

  var widthFromStyle: Float = widthFromStyle
    private set

  var heightFromStyle: Float = heightFromStyle
    private set

  var delegate: LayoutResult? = null
    private set

  var diffNode: DiffNode? = null

  var cachedMeasuresValid: Boolean = false
    @JvmName("areCachedMeasuresValid") get

  var measureHadExceptions: Boolean = false
    @JvmName("measureHadExceptions") get

  var wasMeasured: Boolean = false
    @JvmName("wasMeasured") get
    private set

  val contentWidth: Int
    get() = YogaMeasureOutput.getWidth(lastMeasuredSize).toInt()

  val contentHeight: Int
    get() = YogaMeasureOutput.getHeight(lastMeasuredSize).toInt()

  val childCount: Int
    get() = children.size

  val touchExpansionBottom: Int
    get() =
        if (shouldApplyTouchExpansion()) {
          node.touchExpansion?.let { edges -> FastMath.round(edges[YogaEdge.BOTTOM]) } ?: 0
        } else 0

  val touchExpansionLeft: Int
    get() =
        if (shouldApplyTouchExpansion()) {
          node.touchExpansion?.let { edges ->
            FastMath.round(resolveHorizontalEdges(edges, YogaEdge.LEFT))
          } ?: 0
        } else 0

  val touchExpansionRight: Int
    get() =
        if (shouldApplyTouchExpansion()) {
          node.touchExpansion?.let { edges ->
            FastMath.round(resolveHorizontalEdges(edges, YogaEdge.RIGHT))
          } ?: 0
        } else 0

  val touchExpansionTop: Int
    get() =
        if (shouldApplyTouchExpansion()) {
          node.touchExpansion?.let { edges -> FastMath.round(edges[YogaEdge.TOP]) } ?: 0
        } else 0

  val expandedTouchBounds: Rect?
    get() {
      if (!node.hasTouchExpansion()) {
        return null
      }
      val left = touchExpansionLeft
      val top = touchExpansionTop
      val right = touchExpansionRight
      val bottom = touchExpansionBottom
      return if (left == 0 && top == 0 && right == 0 && bottom == 0) {
        null
      } else {
        Rect(left, top, right, bottom)
      }
    }

  var contentRenderUnit: LithoRenderUnit? = null
    private set

  var hostRenderUnit: LithoRenderUnit? = null
    private set

  var backgroundRenderUnit: LithoRenderUnit? = null
    private set

  var foregroundRenderUnit: LithoRenderUnit? = null
    private set

  var borderRenderUnit: LithoRenderUnit? = null
    private set

  @Px override fun getX(): Int = yogaNode.layoutX.toInt()

  @Px override fun getY(): Int = yogaNode.layoutY.toInt()

  @Px override fun getWidth(): Int = yogaNode.layoutWidth.toInt()

  @Px override fun getHeight(): Int = yogaNode.layoutHeight.toInt()

  @Px override fun getPaddingTop(): Int = FastMath.round(yogaNode.getLayoutPadding(YogaEdge.TOP))

  @Px
  override fun getPaddingRight(): Int = FastMath.round(yogaNode.getLayoutPadding(YogaEdge.RIGHT))

  @Px
  override fun getPaddingBottom(): Int = FastMath.round(yogaNode.getLayoutPadding(YogaEdge.BOTTOM))

  @Px override fun getPaddingLeft(): Int = FastMath.round(yogaNode.getLayoutPadding(YogaEdge.LEFT))

  override fun isPaddingSet(): Boolean = node.isPaddingSet

  override fun getBackground(): Drawable? = node.background

  override fun getResolvedLayoutDirection(): YogaDirection = yogaNode.layoutDirection

  override fun getRenderUnit(): LithoRenderUnit? = null // Unimplemented.

  override fun getChildrenCount(): Int = children.size

  override fun getChildAt(i: Int): LithoLayoutResult = children[i]

  override fun getXForChildAtIndex(index: Int): Int = children[index].x

  override fun getYForChildAtIndex(index: Int): Int = children[index].y

  override fun getLayoutData(): Any? = layoutData

  fun setLayoutData(data: Any?) {
    layoutData = data
  }

  /**
   * Since layout data like the layout context and the diff node are not required after layout
   * calculation they can be released to free up memory.
   */
  open fun releaseLayoutPhaseData() {
    diffNode = null
    yogaNode.data = null
    for (i in 0 until childCount) {
      getChildAt(i).releaseLayoutPhaseData()
    }
  }

  protected open fun measureInternal(
      context: LayoutContext<LithoRenderContext>,
      widthSpec: Int,
      heightSpec: Int
  ): MeasureResult {
    val isTracing: Boolean = ComponentsSystrace.isTracing
    val node: LithoNode = node
    val component: Component = node.tailComponent
    val componentScopedContext: ComponentContext = node.tailComponentContext
    val diffNode: DiffNode? = if (cachedMeasuresValid) diffNode else null
    val width: Int
    val height: Int
    val delegate: LayoutResult?
    val layoutData: Any?

    // If diff node is set check if measurements from the previous pass can be reused
    if (diffNode?.lastWidthSpec == widthSpec &&
        diffNode.lastHeightSpec == heightSpec &&
        !shouldAlwaysRemeasure(component)) {
      width = diffNode.lastMeasuredWidth
      height = diffNode.lastMeasuredHeight
      layoutData = diffNode.layoutData
      delegate = diffNode.delegate

      // Measure the component
    } else {
      if (isTracing) {
        ComponentsSystrace.beginSection("onMeasure:${component.simpleName}")
      }
      try {
        val primitive = node.primitive
        // measure Primitive
        if (primitive != null) {
          context.setPreviousLayoutDataForCurrentNode(this.layoutData)
          context.layoutContextExtraData = LithoLayoutContextExtraData(yogaNode)
          @Suppress("UNCHECKED_CAST")
          delegate =
              primitive.calculateLayout(context as LayoutContext<Any?>, widthSpec, heightSpec)
          width = delegate.width
          height = delegate.height
          layoutData = delegate.layoutData
        } else {
          val size = Size(Int.MIN_VALUE, Int.MIN_VALUE)
          // If the Layout Result was cached, but the size specs changed, then layout data
          // will be mutated. To avoid that create new (layout data) interstage props container
          // for mount specs to avoid mutating the currently mount layout data.
          layoutData = (component as SpecGeneratedComponent).createInterStagePropsContainer()
          component.onMeasure(componentScopedContext, this, widthSpec, heightSpec, size, layoutData)
          delegate = null
          width = size.width
          height = size.height
        }

        // If layout data has changed then content render unit should be recreated
        if (!hasEquivalentFields(this.layoutData, layoutData)) {
          contentRenderUnit = null
        }
      } finally {
        if (isTracing) {
          ComponentsSystrace.endSection()
        }
      }
    }
    this.delegate = delegate
    this.layoutData = layoutData
    return MeasureResult(width, height, layoutData)
  }

  fun measure(
      context: LayoutContext<LithoRenderContext>,
      widthSpec: Int,
      heightSpec: Int
  ): MeasureResult {
    val renderContext =
        requireNotNull(context.renderContext) { "render context should not be null" }

    val isTracing = ComponentsSystrace.isTracing
    var size: MeasureResult
    wasMeasured = true
    if (renderContext.lithoLayoutContext.isFutureReleased) {

      // If layout is released then skip measurement
      size = MeasureResult.error()
    } else {
      val component = node.tailComponent
      if (isTracing) {
        ComponentsSystrace.beginSectionWithArgs("measure:${component.simpleName}")
            .arg("widthSpec", SizeSpec.toString(widthSpec))
            .arg("heightSpec", SizeSpec.toString(heightSpec))
            .arg("componentId", component.id)
            .flush()
      }
      try {
        size = measureInternal(context, widthSpec, heightSpec)
        check(!(size.width < 0 || size.height < 0)) {
          ("MeasureOutput not set, Component is: $component WidthSpec: ${MeasureSpecUtils.getMeasureSpecDescription(widthSpec)} HeightSpec: ${MeasureSpecUtils.getMeasureSpecDescription(heightSpec)} Measured width : ${size.width} Measured Height: ${size.height}")
        }
      } catch (e: Exception) {

        // Handle then exception
        ComponentUtils.handle(node.tailComponentContext, e)

        // If the exception is handled then return 0 size to continue layout.
        size = MeasureResult.error()
      }
    }

    // Record the last measured width, and height spec
    this.widthSpec = widthSpec
    this.heightSpec = heightSpec

    // If the size of a cached layout has changed then clear size dependant render units
    if (isCachedLayout && (contentWidth != size.width || contentHeight != size.height)) {
      backgroundRenderUnit = null
      foregroundRenderUnit = null
      borderRenderUnit = null
    }
    lastMeasuredSize = YogaMeasureOutput.make(size.width, size.height)

    if (isTracing) {
      ComponentsSystrace.endSection()
    }
    measureHadExceptions = size.mHadExceptions
    return size
  }

  fun setSizeSpec(widthSpec: Int, heightSpec: Int) {
    if (this.widthSpec != widthSpec || this.heightSpec != heightSpec) {
      lastMeasuredSize = Long.MIN_VALUE
    }
    this.widthSpec = widthSpec
    this.heightSpec = heightSpec
  }

  fun copyLayoutResult(node: LithoNode, yogaNode: YogaNode?): LithoLayoutResult {
    val copiedResult = node.createLayoutResult(yogaNode, null)
    copiedResult.isCachedLayout = true
    copiedResult.cachedMeasuresValid = true
    copiedResult.widthSpec = widthSpec
    copiedResult.heightSpec = heightSpec
    copiedResult.lastMeasuredSize = lastMeasuredSize
    copiedResult.delegate = delegate
    copiedResult.layoutData = layoutData
    copiedResult.widthFromStyle = widthFromStyle
    copiedResult.heightFromStyle = heightFromStyle
    copiedResult.contentRenderUnit = contentRenderUnit
    copiedResult.hostRenderUnit = hostRenderUnit
    copiedResult.backgroundRenderUnit = backgroundRenderUnit
    copiedResult.foregroundRenderUnit = foregroundRenderUnit
    copiedResult.borderRenderUnit = borderRenderUnit
    return copiedResult
  }

  fun addChild(child: LithoLayoutResult) {
    children.add(child)
  }

  fun onBoundsDefined() {
    val context = node.tailComponentContext
    val component = node.tailComponent
    var hasLayoutSizeChanged = false

    if (Component.isMountSpec(component) && component is SpecGeneratedComponent) {

      val newContentWidth: Int =
          if (wasMeasured ||
              (isCachedLayout && MeasureSpecUtils.getMode(widthSpec) == SizeSpec.EXACTLY)) {
            contentWidth
          } else {
            (width -
                paddingRight -
                paddingLeft -
                getLayoutBorder(YogaEdge.RIGHT) -
                getLayoutBorder(YogaEdge.LEFT))
          }

      val newContentHeight: Int =
          if (wasMeasured ||
              (isCachedLayout && MeasureSpecUtils.getMode(heightSpec) == SizeSpec.EXACTLY)) {
            contentHeight
          } else {
            (height -
                paddingTop -
                paddingBottom -
                getLayoutBorder(YogaEdge.TOP) -
                getLayoutBorder(YogaEdge.BOTTOM))
          }

      if (lastMeasuredSize == Long.MIN_VALUE ||
          !isCachedLayout ||
          wasMeasured ||
          newContentWidth != contentWidth ||
          newContentHeight != contentHeight) {

        // We should only invoke `onBoundsDefined` if layout is non cached or size has changed.
        // Note: MountSpec is always treated as size changed once `onMeasure` is invoked no matter
        // if the size changed or not.
        val isTracing = ComponentsSystrace.isTracing
        if (isTracing) {
          ComponentsSystrace.beginSection("onBoundsDefined:${component.getSimpleName()}")
        }
        val layoutData: InterStagePropsContainer?
        // If the Layout Result was cached, but the size has changed, then interstage props
        // container (layout data) could be mutated when @OnBoundsDefined is invoked. To avoid that
        // create new interstage props container (layout data), and copy over the current values.
        if (isCachedLayout) {
          layoutData = component.createInterStagePropsContainer()
          if (layoutData != null && this.layoutData != null) {
            component.copyInterStageImpl(layoutData, this.layoutData as InterStagePropsContainer?)
          }
        } else {
          layoutData = this.layoutData as InterStagePropsContainer?
        }
        try {
          component.onBoundsDefined(context, this, layoutData)
        } catch (e: Exception) {
          ComponentUtils.handleWithHierarchy(context, component, e)
          measureHadExceptions = true
        } finally {
          if (isTracing) {
            ComponentsSystrace.endSection()
          }
        }

        // If layout data has changed then content render unit should be recreated
        if (!hasEquivalentFields(this.layoutData, layoutData)) {
          contentRenderUnit = null
        }
        this.layoutData = layoutData

        hasLayoutSizeChanged = true
      }
      if (!wasMeasured) {
        wasMeasured = true
        widthSpec = MeasureSpecUtils.exactly(width)
        heightSpec = MeasureSpecUtils.exactly(height)
        lastMeasuredSize = YogaMeasureOutput.make(width, height)
      }
    } else if (Component.isPrimitive(component)) {

      val measuredContentWidth: Int =
          (width -
              paddingRight -
              paddingLeft -
              getLayoutBorder(YogaEdge.RIGHT) -
              getLayoutBorder(YogaEdge.LEFT))
      val measuredContentHeight: Int =
          (height -
              paddingTop -
              paddingBottom -
              getLayoutBorder(YogaEdge.TOP) -
              getLayoutBorder(YogaEdge.BOTTOM))

      hasLayoutSizeChanged =
          (isCachedLayout &&
              (measuredContentWidth != contentWidth || measuredContentHeight != contentHeight))

      if (delegate == null || hasLayoutSizeChanged) {

        // Check if we need to run measure for Primitive that was skipped due to with fixed size
        val layoutContext = getLayoutContextFromYogaNode(yogaNode)
        @Suppress("UNCHECKED_CAST")
        measure(
            layoutContext as LayoutContext<LithoRenderContext>,
            MeasureSpecUtils.exactly(measuredContentWidth),
            MeasureSpecUtils.exactly(measuredContentHeight))
      }
    } else {
      hasLayoutSizeChanged =
          (lastMeasuredSize == Long.MIN_VALUE) ||
              (isCachedLayout && (contentWidth != width || contentHeight != height))
      if (hasLayoutSizeChanged) {
        lastMeasuredSize = YogaMeasureOutput.make(width, height)
      }
    }

    // Reuse or recreate additional outputs. Outputs are recreated if the size has changed
    if (contentRenderUnit == null) {
      contentRenderUnit =
          LithoNodeUtils.createContentRenderUnit(node, cachedMeasuresValid, diffNode)
    }
    if (hostRenderUnit == null) {
      hostRenderUnit = LithoNodeUtils.createHostRenderUnit(node)
    }
    if (backgroundRenderUnit == null || hasLayoutSizeChanged) {
      backgroundRenderUnit =
          LithoNodeUtils.createBackgroundRenderUnit(node, width, height, diffNode)
    }
    if (foregroundRenderUnit == null || hasLayoutSizeChanged) {
      foregroundRenderUnit =
          LithoNodeUtils.createForegroundRenderUnit(node, width, height, diffNode)
    }
    if (shouldDrawBorders() && (borderRenderUnit == null || hasLayoutSizeChanged)) {
      borderRenderUnit =
          LithoNodeUtils.createBorderRenderUnit(
              node, createBorderColorDrawable(this), width, height, diffNode)
    }
  }

  companion object {

    @JvmStatic
    fun getLayoutContextFromYogaNode(yogaNode: YogaNode): LayoutContext<*> =
        (yogaNode.data as Pair<*, *>).first as LayoutContext<*>

    @JvmStatic
    fun getLayoutResultFromYogaNode(yogaNode: YogaNode): LithoLayoutResult =
        (yogaNode.data as Pair<*, *>).second as LithoLayoutResult

    private fun createBorderColorDrawable(result: LithoLayoutResult): BorderColorDrawable {
      val node = result.node
      val isRtl = result.recursivelyResolveLayoutDirection() == YogaDirection.RTL
      val borderRadius = node.borderRadius
      val borderColors = node.borderColors
      val leftEdge = if (isRtl) YogaEdge.RIGHT else YogaEdge.LEFT
      val rightEdge = if (isRtl) YogaEdge.LEFT else YogaEdge.RIGHT
      return BorderColorDrawable.Builder()
          .pathEffect(node.borderPathEffect)
          .borderLeftColor(Border.getEdgeColor(borderColors, leftEdge))
          .borderTopColor(Border.getEdgeColor(borderColors, YogaEdge.TOP))
          .borderRightColor(Border.getEdgeColor(borderColors, rightEdge))
          .borderBottomColor(Border.getEdgeColor(borderColors, YogaEdge.BOTTOM))
          .borderLeftWidth(result.getLayoutBorder(leftEdge))
          .borderTopWidth(result.getLayoutBorder(YogaEdge.TOP))
          .borderRightWidth(result.getLayoutBorder(rightEdge))
          .borderBottomWidth(result.getLayoutBorder(YogaEdge.BOTTOM))
          .borderRadius(borderRadius)
          .build()
    }

    private fun LithoLayoutResult.shouldDrawBorders(): Boolean =
        node.hasBorderColor() &&
            (yogaNode.getLayoutBorder(YogaEdge.LEFT) != 0f ||
                yogaNode.getLayoutBorder(YogaEdge.TOP) != 0f ||
                yogaNode.getLayoutBorder(YogaEdge.RIGHT) != 0f ||
                yogaNode.getLayoutBorder(YogaEdge.BOTTOM) != 0f)

    private fun LithoLayoutResult.shouldApplyTouchExpansion(): Boolean =
        node.touchExpansion != null && (node.nodeInfo?.hasTouchEventHandlers() == true)

    private fun LithoLayoutResult.resolveHorizontalEdges(spacing: Edges, edge: YogaEdge): Float {
      val isRtl = yogaNode.layoutDirection == YogaDirection.RTL
      val resolvedEdge =
          when (edge) {
            YogaEdge.LEFT -> (if (isRtl) YogaEdge.END else YogaEdge.START)
            YogaEdge.RIGHT -> (if (isRtl) YogaEdge.START else YogaEdge.END)
            else -> throw IllegalArgumentException("Not an horizontal padding edge: $edge")
          }
      var result = spacing.getRaw(resolvedEdge)
      if (YogaConstants.isUndefined(result)) {
        result = spacing[edge]
      }
      return result
    }

    private fun LithoLayoutResult.recursivelyResolveLayoutDirection(): YogaDirection {
      val direction = yogaNode.layoutDirection
      check(direction != YogaDirection.INHERIT) {
        "Direction cannot be resolved before layout calculation"
      }
      return direction
    }

    fun LithoLayoutResult.getLayoutBorder(edge: YogaEdge?): Int =
        FastMath.round(yogaNode.getLayoutBorder(edge))

    private fun shouldAlwaysRemeasure(component: Component): Boolean =
        if (component is SpecGeneratedComponent) {
          component.shouldAlwaysRemeasure()
        } else {
          false
        }
  }
}

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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.facebook.litho.it.R.drawable.background_with_padding;
import static com.facebook.litho.testing.TestViewComponent.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.animation.StateListAnimator;
import android.annotation.TargetApi;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.SparseArray;
import com.facebook.litho.annotations.ImportantForAccessibility;
import com.facebook.litho.drawable.ComparableColorDrawable;
import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.TestComponent;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.Text;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaDirection;
import com.facebook.yoga.YogaEdge;
import com.facebook.yoga.YogaPositionType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LithoTestRunner.class)
public class CommonPropsTest {

  public final @Rule LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();

  private LithoNode mNode;
  private CommonProps mCommonProps;
  private ComponentContext mComponentContext;

  @Before
  public void setup() {
    mNode = mock(LithoNode.class);
    mCommonProps = new CommonProps();
    mComponentContext = new ComponentContext(getApplicationContext());
  }

  @Test
  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public void testSetPropsAndBuild() {
    mCommonProps.layoutDirection(YogaDirection.INHERIT);
    mCommonProps.alignSelf(YogaAlign.AUTO);
    mCommonProps.positionType(YogaPositionType.ABSOLUTE);
    mCommonProps.flex(2);
    mCommonProps.flexGrow(3);
    mCommonProps.flexShrink(4);
    mCommonProps.flexBasisPx(5);
    mCommonProps.flexBasisPercent(6);

    mCommonProps.importantForAccessibility(
        ImportantForAccessibility.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    mCommonProps.duplicateParentState(false);

    mCommonProps.marginPx(YogaEdge.ALL, 5);
    mCommonProps.marginPx(YogaEdge.RIGHT, 6);
    mCommonProps.marginPx(YogaEdge.LEFT, 4);
    mCommonProps.marginPercent(YogaEdge.ALL, 10);
    mCommonProps.marginPercent(YogaEdge.VERTICAL, 12);
    mCommonProps.marginPercent(YogaEdge.RIGHT, 5);
    mCommonProps.marginAuto(YogaEdge.LEFT);
    mCommonProps.marginAuto(YogaEdge.TOP);
    mCommonProps.marginAuto(YogaEdge.RIGHT);
    mCommonProps.marginAuto(YogaEdge.BOTTOM);

    mCommonProps.paddingPx(YogaEdge.ALL, 1);
    mCommonProps.paddingPx(YogaEdge.RIGHT, 2);
    mCommonProps.paddingPx(YogaEdge.LEFT, 3);
    mCommonProps.paddingPercent(YogaEdge.VERTICAL, 7);
    mCommonProps.paddingPercent(YogaEdge.RIGHT, 6);
    mCommonProps.paddingPercent(YogaEdge.ALL, 5);

    mCommonProps.border(Border.create(mComponentContext).build());

    mCommonProps.positionPx(YogaEdge.ALL, 11);
    mCommonProps.positionPx(YogaEdge.RIGHT, 12);
    mCommonProps.positionPx(YogaEdge.LEFT, 13);
    mCommonProps.positionPercent(YogaEdge.VERTICAL, 17);
    mCommonProps.positionPercent(YogaEdge.RIGHT, 16);
    mCommonProps.positionPercent(YogaEdge.ALL, 15);

    mCommonProps.widthPx(5);
    mCommonProps.widthPercent(50);
    mCommonProps.minWidthPx(15);
    mCommonProps.minWidthPercent(100);
    mCommonProps.maxWidthPx(25);
    mCommonProps.maxWidthPercent(26);

    mCommonProps.heightPx(30);
    mCommonProps.heightPercent(31);
    mCommonProps.minHeightPx(32);
    mCommonProps.minHeightPercent(33);
    mCommonProps.maxHeightPx(34);
    mCommonProps.maxHeightPercent(35);

    mCommonProps.aspectRatio(20);

    mCommonProps.touchExpansionPx(YogaEdge.RIGHT, 22);
    mCommonProps.touchExpansionPx(YogaEdge.LEFT, 23);
    mCommonProps.touchExpansionPx(YogaEdge.ALL, 21);
    Drawable background = ComparableColorDrawable.create(Color.RED);
    mCommonProps.background(background);
    Drawable foreground = ComparableColorDrawable.create(Color.BLACK);
    mCommonProps.foreground(foreground);

    mCommonProps.wrapInView();

    final EventHandler<ClickEvent> clickHandler = mock(EventHandler.class);
    final EventHandler<LongClickEvent> longClickHandler = mock(EventHandler.class);
    final EventHandler<TouchEvent> touchHandler = mock(EventHandler.class);
    final EventHandler<InterceptTouchEvent> interceptTouchHandler = mock(EventHandler.class);
    final EventHandler<FocusChangedEvent> focusChangedHandler = mock(EventHandler.class);
    mCommonProps.clickHandler(clickHandler);
    mCommonProps.focusChangeHandler(focusChangedHandler);
    mCommonProps.longClickHandler(longClickHandler);
    mCommonProps.touchHandler(touchHandler);
    mCommonProps.interceptTouchHandler(interceptTouchHandler);

    mCommonProps.focusable(true);
    mCommonProps.clickable(true);
    mCommonProps.selected(false);
    mCommonProps.enabled(false);
    mCommonProps.visibleHeightRatio(55);
    mCommonProps.visibleWidthRatio(56);
    mCommonProps.accessibilityHeading(false);

    final EventHandler<VisibleEvent> visibleHandler = mock(EventHandler.class);
    final EventHandler<FocusedVisibleEvent> focusedHandler = mock(EventHandler.class);
    final EventHandler<UnfocusedVisibleEvent> unfocusedHandler = mock(EventHandler.class);
    final EventHandler<FullImpressionVisibleEvent> fullImpressionHandler = mock(EventHandler.class);
    final EventHandler<InvisibleEvent> invisibleHandler = mock(EventHandler.class);
    final EventHandler<VisibilityChangedEvent> visibleRectChangedHandler = mock(EventHandler.class);
    mCommonProps.visibleHandler(visibleHandler);
    mCommonProps.focusedHandler(focusedHandler);
    mCommonProps.unfocusedHandler(unfocusedHandler);
    mCommonProps.fullImpressionHandler(fullImpressionHandler);
    mCommonProps.invisibleHandler(invisibleHandler);
    mCommonProps.visibilityChangedHandler(visibleRectChangedHandler);

    mCommonProps.contentDescription("test");

    Object viewTag = new Object();
    SparseArray<Object> viewTags = new SparseArray<>();
    mCommonProps.viewTag(viewTag);
    mCommonProps.viewTags(viewTags);

    mCommonProps.shadowElevationPx(60);

    mCommonProps.clipToOutline(false);
    mCommonProps.transitionKey("transitionKey", "");
    mCommonProps.testKey("testKey");

    final EventHandler<DispatchPopulateAccessibilityEventEvent>
        dispatchPopulateAccessibilityEventHandler = mock(EventHandler.class);
    final EventHandler<OnInitializeAccessibilityEventEvent> onInitializeAccessibilityEventHandler =
        mock(EventHandler.class);
    final EventHandler<OnInitializeAccessibilityNodeInfoEvent>
        onInitializeAccessibilityNodeInfoHandler = mock(EventHandler.class);
    final EventHandler<OnPopulateAccessibilityEventEvent> onPopulateAccessibilityEventHandler =
        mock(EventHandler.class);
    final EventHandler<OnPopulateAccessibilityNodeEvent> onPopulateAccessibiliNodeHandler =
        mock(EventHandler.class);
    final EventHandler<OnRequestSendAccessibilityEventEvent>
        onRequestSendAccessibilityEventHandler = mock(EventHandler.class);
    final EventHandler<PerformAccessibilityActionEvent> performAccessibilityActionHandler =
        mock(EventHandler.class);
    final EventHandler<SendAccessibilityEventEvent> sendAccessibilityEventHandler =
        mock(EventHandler.class);
    final EventHandler<SendAccessibilityEventUncheckedEvent>
        sendAccessibilityEventUncheckedHandler = mock(EventHandler.class);
    mCommonProps.accessibilityRole(AccessibilityRole.BUTTON);
    mCommonProps.accessibilityRoleDescription("Test Role Description");
    mCommonProps.dispatchPopulateAccessibilityEventHandler(
        dispatchPopulateAccessibilityEventHandler);
    mCommonProps.onInitializeAccessibilityEventHandler(onInitializeAccessibilityEventHandler);
    mCommonProps.onInitializeAccessibilityNodeInfoHandler(onInitializeAccessibilityNodeInfoHandler);
    mCommonProps.onPopulateAccessibilityEventHandler(onPopulateAccessibilityEventHandler);
    mCommonProps.onPopulateAccessibilityNodeHandler(onPopulateAccessibiliNodeHandler);
    mCommonProps.onRequestSendAccessibilityEventHandler(onRequestSendAccessibilityEventHandler);
    mCommonProps.performAccessibilityActionHandler(performAccessibilityActionHandler);
    mCommonProps.sendAccessibilityEventHandler(sendAccessibilityEventHandler);
    mCommonProps.sendAccessibilityEventUncheckedHandler(sendAccessibilityEventUncheckedHandler);

    final StateListAnimator stateListAnimator = mock(StateListAnimator.class);
    mCommonProps.stateListAnimator(stateListAnimator);

    mCommonProps.copyInto(mComponentContext, mNode);
    final LayoutProps output = spy(LayoutProps.class);
    mCommonProps.copyLayoutProps(output);

    verify(output).layoutDirection(YogaDirection.INHERIT);
    verify(output).alignSelf(YogaAlign.AUTO);
    verify(output).positionType(YogaPositionType.ABSOLUTE);
    verify(output).flex(2);
    verify(output).flexGrow(3);
    verify(output).flexShrink(4);
    verify(output).flexBasisPx(5);
    verify(output).flexBasisPercent(6);

    verify(mNode)
        .importantForAccessibility(ImportantForAccessibility.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    verify(mNode).duplicateParentState(false);

    verify(output).marginPx(YogaEdge.ALL, 5);
    verify(output).marginPx(YogaEdge.RIGHT, 6);
    verify(output).marginPx(YogaEdge.LEFT, 4);
    verify(output).marginPercent(YogaEdge.ALL, 10);
    verify(output).marginPercent(YogaEdge.VERTICAL, 12);
    verify(output).marginPercent(YogaEdge.RIGHT, 5);
    verify(output).marginAuto(YogaEdge.LEFT);
    verify(output).marginAuto(YogaEdge.TOP);
    verify(output).marginAuto(YogaEdge.RIGHT);
    verify(output).marginAuto(YogaEdge.BOTTOM);

    verify(output).paddingPx(YogaEdge.ALL, 1);
    verify(output).paddingPx(YogaEdge.RIGHT, 2);
    verify(output).paddingPx(YogaEdge.LEFT, 3);
    verify(output).paddingPercent(YogaEdge.VERTICAL, 7);
    verify(output).paddingPercent(YogaEdge.RIGHT, 6);
    verify(output).paddingPercent(YogaEdge.ALL, 5);

    verify(mNode).border((Border) any());

    verify(output).positionPx(YogaEdge.ALL, 11);
    verify(output).positionPx(YogaEdge.RIGHT, 12);
    verify(output).positionPx(YogaEdge.LEFT, 13);
    verify(output).positionPercent(YogaEdge.VERTICAL, 17);
    verify(output).positionPercent(YogaEdge.RIGHT, 16);
    verify(output).positionPercent(YogaEdge.ALL, 15);

    verify(output).widthPx(5);
    verify(output).widthPercent(50);
    verify(output).minWidthPx(15);
    verify(output).minWidthPercent(100);
    verify(output).maxWidthPx(25);
    verify(output).maxWidthPercent(26);

    verify(output).heightPx(30);
    verify(output).heightPercent(31);
    verify(output).minHeightPx(32);
    verify(output).minHeightPercent(33);
    verify(output).maxHeightPx(34);
    verify(output).maxHeightPercent(35);

    verify(output).aspectRatio(20);

    verify(mNode).touchExpansionPx(YogaEdge.RIGHT, 22);
    verify(mNode).touchExpansionPx(YogaEdge.LEFT, 23);
    verify(mNode).touchExpansionPx(YogaEdge.ALL, 21);

    verify(mNode).background(background);
    verify(mNode).foreground(foreground);

    verify(mNode).wrapInView();

    verify(mNode).applyNodeInfo(any());
    verify(mNode).visibleHeightRatio(55);
    verify(mNode).visibleWidthRatio(56);

    verify(mNode).visibleHandler(visibleHandler);
    verify(mNode).focusedHandler(focusedHandler);
    verify(mNode).unfocusedHandler(unfocusedHandler);
    verify(mNode).fullImpressionHandler(fullImpressionHandler);
    verify(mNode).invisibleHandler(invisibleHandler);
    verify(mNode).visibilityChangedHandler(visibleRectChangedHandler);

    verify(mNode).transitionKey(eq("transitionKey"), anyString());
    verify(mNode).testKey("testKey");

    verify(mNode).stateListAnimator(stateListAnimator);
  }

  @Test
  public void testSetScalePropsWrapsInView() {
    mCommonProps.scale(5);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode).wrapInView();
  }

  @Test
  public void testSetFullScalePropsDoesNotWrapInView() {
    mCommonProps.scale(0.5f);
    mCommonProps.scale(1f);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode, never()).wrapInView();
  }

  @Test
  public void testSetAlphaPropsWrapsInView() {
    mCommonProps.alpha(5);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode).wrapInView();
  }

  @Test
  public void testSetFullAlphaPropsDoesNotWrapInView() {
    mCommonProps.alpha(5);
    mCommonProps.alpha(1f);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode, never()).wrapInView();
  }

  @Test
  public void testSetRotationPropsWrapsInView() {
    mCommonProps.rotation(5);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode).wrapInView();
  }

  @Test
  public void testSetZeroRotationPropsDoesNotWrapInView() {
    mCommonProps.rotation(1f);
    mCommonProps.rotation(0f);
    mCommonProps.copyInto(mComponentContext, mNode);

    verify(mNode).applyNodeInfo(any());
    verify(mNode, never()).wrapInView();
  }

  @Test
  public void testPaddingFromDrawable() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final Component component =
        Column.create(c)
            .child(Text.create(c).text("Hello World").backgroundRes(background_with_padding))
            .build();

    mLegacyLithoViewRule.attachToWindow().setRoot(component).measure().layout();

    final LithoLayoutResult result = mLegacyLithoViewRule.getCurrentRootNode().getChildAt(0);

    assertThat(result.getPaddingLeft()).isEqualTo(48);
    assertThat(result.getPaddingTop()).isEqualTo(0);
    assertThat(result.getPaddingRight()).isEqualTo(0);
    assertThat(result.getPaddingBottom()).isEqualTo(0);
  }

  @Test
  public void testPaddingFromDrawableIsOverwritten() {
    final ComponentContext c = mLegacyLithoViewRule.getContext();
    final Component component =
        Column.create(c)
            .child(
                Text.create(c)
                    .text("Hello World")
                    .backgroundRes(background_with_padding)
                    .paddingPx(YogaEdge.LEFT, 8)
                    .paddingPx(YogaEdge.RIGHT, 8)
                    .paddingPx(YogaEdge.TOP, 8)
                    .paddingPx(YogaEdge.BOTTOM, 8))
            .build();

    mLegacyLithoViewRule.attachToWindow().setRoot(component).measure().layout();

    final LithoLayoutResult result = mLegacyLithoViewRule.getCurrentRootNode().getChildAt(0);

    assertThat(result.getPaddingLeft()).isEqualTo(8);
    assertThat(result.getPaddingTop()).isEqualTo(8);
    assertThat(result.getPaddingRight()).isEqualTo(8);
    assertThat(result.getPaddingBottom()).isEqualTo(8);
  }

  @Test
  public void testSameObjectEquivalentTo() {
    assertThat(mCommonProps.isEquivalentTo(mCommonProps)).isEqualTo(true);
  }

  @Test
  public void testNullObjectEquivalentTo() {
    assertThat(mCommonProps.isEquivalentTo(null)).isEqualTo(false);
  }

  @Test
  public void testDifferentObjectWithSameContentEquivalentTo() {
    mCommonProps = new CommonProps();
    setCommonProps(mCommonProps);

    CommonProps mCommonProps2 = new CommonProps();
    setCommonProps(mCommonProps2);

    assertThat(mCommonProps.isEquivalentTo(mCommonProps2)).isEqualTo(true);
  }

  @Test
  public void testDifferentObjectWithDifferentContentEquivalentTo() {
    mCommonProps = new CommonProps();
    setCommonProps(mCommonProps);
    mCommonProps.duplicateParentState(false);

    CommonProps mCommonProps2 = new CommonProps();
    setCommonProps(mCommonProps2);
    mCommonProps2.duplicateParentState(true);

    assertThat(mCommonProps.isEquivalentTo(mCommonProps2)).isEqualTo(false);
  }

  private void setCommonProps(CommonProps commonProps) {
    commonProps.layoutDirection(YogaDirection.INHERIT);
    commonProps.alignSelf(YogaAlign.AUTO);
    commonProps.positionType(YogaPositionType.ABSOLUTE);
    commonProps.flex(2);
    commonProps.flexGrow(3);
    commonProps.flexShrink(4);
    commonProps.flexBasisPx(5);
    commonProps.flexBasisPercent(6);

    commonProps.importantForAccessibility(
        ImportantForAccessibility.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    commonProps.duplicateParentState(false);

    commonProps.marginPx(YogaEdge.ALL, 5);
    commonProps.marginPx(YogaEdge.RIGHT, 6);
    commonProps.marginPx(YogaEdge.LEFT, 4);
    commonProps.marginPercent(YogaEdge.ALL, 10);
    commonProps.marginPercent(YogaEdge.VERTICAL, 12);
    commonProps.marginPercent(YogaEdge.RIGHT, 5);
    commonProps.marginAuto(YogaEdge.LEFT);
    commonProps.marginAuto(YogaEdge.TOP);
    commonProps.marginAuto(YogaEdge.RIGHT);
    commonProps.marginAuto(YogaEdge.BOTTOM);

    commonProps.paddingPx(YogaEdge.ALL, 1);
    commonProps.paddingPx(YogaEdge.RIGHT, 2);
    commonProps.paddingPx(YogaEdge.LEFT, 3);
    commonProps.paddingPercent(YogaEdge.VERTICAL, 7);
    commonProps.paddingPercent(YogaEdge.RIGHT, 6);
    commonProps.paddingPercent(YogaEdge.ALL, 5);

    commonProps.border(Border.create(mComponentContext).build());

    commonProps.positionPx(YogaEdge.ALL, 11);
    commonProps.positionPx(YogaEdge.RIGHT, 12);
    commonProps.positionPx(YogaEdge.LEFT, 13);
    commonProps.positionPercent(YogaEdge.VERTICAL, 17);
    commonProps.positionPercent(YogaEdge.RIGHT, 16);
    commonProps.positionPercent(YogaEdge.ALL, 15);

    commonProps.widthPx(5);
    commonProps.widthPercent(50);
    commonProps.minWidthPx(15);
    commonProps.minWidthPercent(100);
    commonProps.maxWidthPx(25);
    commonProps.maxWidthPercent(26);

    commonProps.heightPx(30);
    commonProps.heightPercent(31);
    commonProps.minHeightPx(32);
    commonProps.minHeightPercent(33);
    commonProps.maxHeightPx(34);
    commonProps.maxHeightPercent(35);

    commonProps.aspectRatio(20);

    commonProps.touchExpansionPx(YogaEdge.RIGHT, 22);
    commonProps.touchExpansionPx(YogaEdge.LEFT, 23);
    commonProps.touchExpansionPx(YogaEdge.ALL, 21);
    Drawable background = ComparableColorDrawable.create(Color.RED);
    commonProps.background(background);
    Drawable foreground = ComparableColorDrawable.create(Color.BLACK);
    commonProps.foreground(foreground);

    commonProps.wrapInView();

    final TestComponent content = create(mComponentContext).build();

    final EventHandler<ClickEvent> clickHandler = new EventHandler<>(content, 3);
    final EventHandler<LongClickEvent> longClickHandler = new EventHandler<>(content, 3);
    final EventHandler<TouchEvent> touchHandler = new EventHandler<>(content, 3);
    final EventHandler<InterceptTouchEvent> interceptTouchHandler = new EventHandler<>(content, 3);
    final EventHandler<FocusChangedEvent> focusChangedHandler = new EventHandler<>(content, 3);
    commonProps.clickHandler(clickHandler);
    commonProps.focusChangeHandler(focusChangedHandler);
    commonProps.longClickHandler(longClickHandler);
    commonProps.touchHandler(touchHandler);
    commonProps.interceptTouchHandler(interceptTouchHandler);

    commonProps.focusable(true);
    commonProps.clickable(true);
    commonProps.selected(false);
    commonProps.enabled(false);
    commonProps.visibleHeightRatio(55);
    commonProps.visibleWidthRatio(56);
    commonProps.accessibilityHeading(false);

    final EventHandler<VisibleEvent> visibleHandler = new EventHandler<>(content, 3);
    final EventHandler<FocusedVisibleEvent> focusedHandler = new EventHandler<>(content, 3);
    final EventHandler<UnfocusedVisibleEvent> unfocusedHandler = new EventHandler<>(content, 3);
    final EventHandler<FullImpressionVisibleEvent> fullImpressionHandler =
        new EventHandler<>(content, 3);
    final EventHandler<InvisibleEvent> invisibleHandler = new EventHandler<>(content, 3);
    final EventHandler<VisibilityChangedEvent> visibleRectChangedHandler =
        new EventHandler<>(content, 3);
    commonProps.visibleHandler(visibleHandler);
    commonProps.focusedHandler(focusedHandler);
    commonProps.unfocusedHandler(unfocusedHandler);
    commonProps.fullImpressionHandler(fullImpressionHandler);
    commonProps.invisibleHandler(invisibleHandler);
    commonProps.visibilityChangedHandler(visibleRectChangedHandler);

    commonProps.contentDescription("test");

    commonProps.viewTag("Hello World");

    commonProps.shadowElevationPx(60);
    commonProps.clipToOutline(false);
    commonProps.transitionKey("transitionKey", "");
    commonProps.testKey("testKey");

    final EventHandler<DispatchPopulateAccessibilityEventEvent>
        dispatchPopulateAccessibilityEventHandler = new EventHandler<>(content, 3);
    final EventHandler<OnInitializeAccessibilityEventEvent> onInitializeAccessibilityEventHandler =
        new EventHandler<>(content, 3);
    final EventHandler<OnInitializeAccessibilityNodeInfoEvent>
        onInitializeAccessibilityNodeInfoHandler = new EventHandler<>(content, 3);
    final EventHandler<OnPopulateAccessibilityEventEvent> onPopulateAccessibilityEventHandler =
        new EventHandler<>(content, 3);
    final EventHandler<OnPopulateAccessibilityNodeEvent> onPopulateAccessibilityNodeHandler =
        new EventHandler<>(content, 3);
    final EventHandler<OnRequestSendAccessibilityEventEvent>
        onRequestSendAccessibilityEventHandler = new EventHandler<>(content, 3);
    final EventHandler<PerformAccessibilityActionEvent> performAccessibilityActionHandler =
        new EventHandler<>(content, 3);
    final EventHandler<SendAccessibilityEventEvent> sendAccessibilityEventHandler =
        new EventHandler<>(content, 3);
    final EventHandler<SendAccessibilityEventUncheckedEvent>
        sendAccessibilityEventUncheckedHandler = new EventHandler<>(content, 3);
    commonProps.accessibilityRole(AccessibilityRole.BUTTON);
    commonProps.accessibilityRoleDescription("Test Role Description");
    commonProps.dispatchPopulateAccessibilityEventHandler(
        dispatchPopulateAccessibilityEventHandler);
    commonProps.onInitializeAccessibilityEventHandler(onInitializeAccessibilityEventHandler);

    commonProps.onInitializeAccessibilityNodeInfoHandler(onInitializeAccessibilityNodeInfoHandler);
    commonProps.onPopulateAccessibilityEventHandler(onPopulateAccessibilityEventHandler);
    commonProps.onPopulateAccessibilityNodeHandler(onPopulateAccessibilityNodeHandler);

    commonProps.onRequestSendAccessibilityEventHandler(onRequestSendAccessibilityEventHandler);
    commonProps.performAccessibilityActionHandler(performAccessibilityActionHandler);
    commonProps.sendAccessibilityEventHandler(sendAccessibilityEventHandler);

    commonProps.sendAccessibilityEventUncheckedHandler(sendAccessibilityEventUncheckedHandler);

    /*
         Copied from ViewNodeInfo class TODO: (T33421916) We need compare StateListAnimators more accurately
    */
    //    final StateListAnimator stateListAnimator = mock(StateListAnimator.class);
    //    commonProps.stateListAnimator(stateListAnimator);

    commonProps.copyInto(mComponentContext, mNode);
  }
}

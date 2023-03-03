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

import static android.content.Context.ACCESSIBILITY_SERVICE;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.facebook.litho.LifecycleStep.ON_MOUNT;
import static com.facebook.litho.LifecycleStep.ON_UNMOUNT;
import static com.facebook.litho.SizeSpec.EXACTLY;
import static com.facebook.litho.SizeSpec.makeSizeSpec;
import static com.facebook.litho.testing.TestViewComponent.create;
import static com.facebook.rendercore.utils.MeasureSpecUtils.exactly;
import static com.facebook.yoga.YogaEdge.ALL;
import static com.facebook.yoga.YogaEdge.LEFT;
import static com.facebook.yoga.YogaEdge.TOP;
import static com.facebook.yoga.YogaPositionType.ABSOLUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.config.TempComponentsConfigurations;
import com.facebook.litho.sections.SectionContext;
import com.facebook.litho.sections.common.DynamicComponentGroupSection;
import com.facebook.litho.sections.common.SingleComponentSection;
import com.facebook.litho.sections.widget.ListRecyclerConfiguration;
import com.facebook.litho.sections.widget.RecyclerBinderConfiguration;
import com.facebook.litho.sections.widget.RecyclerCollectionComponent;
import com.facebook.litho.sections.widget.RecyclerConfiguration;
import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.TestComponent;
import com.facebook.litho.testing.TestViewComponent;
import com.facebook.litho.testing.ViewGroupWithLithoViewChildren;
import com.facebook.litho.testing.Whitebox;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.ComponentRenderInfo;
import com.facebook.litho.widget.ComponentTreeHolder;
import com.facebook.litho.widget.LithoViewFactory;
import com.facebook.litho.widget.MountSpecExcludeFromIncrementalMount;
import com.facebook.litho.widget.MountSpecLifecycleTester;
import com.facebook.litho.widget.MountSpecLifecycleTesterDrawable;
import com.facebook.litho.widget.SectionsRecyclerView;
import com.facebook.litho.widget.SimpleMountSpecTester;
import com.facebook.litho.widget.SimpleStateUpdateEmulator;
import com.facebook.litho.widget.SimpleStateUpdateEmulatorSpec;
import com.facebook.litho.widget.Text;
import com.facebook.yoga.YogaEdge;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAccessibilityManager;
import org.robolectric.shadows.ShadowLooper;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner.class)
public class MountStateIncrementalMountTest {

  private ComponentContext mContext;
  private @Nullable ShadowLooper mResolveThreadShadowLooper;
  private ShadowLooper mLayoutThreadShadowLooper;

  public final @Rule LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();

  @Before
  public void setup() {
    TempComponentsConfigurations.setShouldAddHostViewForRootComponent(true);
    mContext = mLegacyLithoViewRule.getContext();
    mLegacyLithoViewRule.useLithoView(new LithoView(mContext));
    mLayoutThreadShadowLooper =
        Shadows.shadowOf(
            (Looper) Whitebox.invokeMethod(ComponentTree.class, "getDefaultLayoutThreadLooper"));

    if (ComponentsConfiguration.isResolveAndLayoutFuturesSplitEnabled) {
      mResolveThreadShadowLooper =
          Shadows.shadowOf(
              (Looper) Whitebox.invokeMethod(ComponentTree.class, "getDefaultResolveThreadLooper"));
    }
  }

  private void runToEndOfTasks() {
    if (mResolveThreadShadowLooper != null) {
      mResolveThreadShadowLooper.runToEndOfTasks();
    }

    mLayoutThreadShadowLooper.runToEndOfTasks();
  }

  @Test
  public void testExcludeFromIncrementalMountForInvisibleComponents() {

    // ┌─────────────┬────────┐
    // │Visible Rect │        │
    // ├─────────────┘        │
    // │ ┌────────────┬─────┐ │
    // │ │ Component1 │     │ │
    // │ ├────────────┘     │ │
    // │ │                  │ │
    // │ └──────────────────┘ │
    // └──────────────────────┘
    //   ┌────────────┬─────┐
    //   │ Component2 │     │
    //   ├────────────┘     │
    //   │                  │
    //   └──────────────────┘
    //   ┌────────────┬─────┐
    //   │ Component3 │     │
    //   ├────────────┘     │
    //   │                  │
    //   └──────────────────┘

    final EventHandler eventHandler1 = mock(EventHandler.class);
    final EventHandler eventHandler2 = mock(EventHandler.class);

    // Component1 without `excludeFromIncrementalMount`
    final LifecycleTracker tracker1 = new LifecycleTracker();
    final Component component1 =
        MountSpecLifecycleTester.create(mContext)
            .widthPx(100)
            .heightPx(30)
            .lifecycleTracker(tracker1)
            .build();

    // Component2 marked with `excludeFromIncrementalMount`
    final LifecycleTracker tracker2 = new LifecycleTracker();
    final Component component2 =
        MountSpecExcludeFromIncrementalMount.create(mContext)
            .widthPx(100)
            .heightPx(30)
            .lifecycleTracker(tracker2)
            .build();

    // Component3 without `excludeFromIncrementalMount`
    final LifecycleTracker tracker3 = new LifecycleTracker();
    final Component component3 =
        MountSpecLifecycleTester.create(mContext)
            .widthPx(100)
            .heightPx(30)
            .lifecycleTracker(tracker3)
            .build();

    // add a RootHost to check if the state of [excludeFromIncrementalMount]
    // propagates up to its parent
    final Component root =
        Wrapper.create(mContext)
            .delegate(
                Column.create(mContext)
                    .child(
                        Wrapper.create(mContext).delegate(component1).visibleHandler(eventHandler1))
                    .child(
                        Wrapper.create(mContext).delegate(component2).visibleHandler(eventHandler2))
                    .child(component3)
                    .build())
            .wrapInView()
            .build();

    mLegacyLithoViewRule.attachToWindow().setSizeSpecs(exactly(100), exactly(100)).measure();

    ComponentRenderInfo info = ComponentRenderInfo.create().component(root).build();
    ComponentTreeHolder holder = ComponentTreeHolder.create().renderInfo(info).build();
    holder.computeLayoutSync(mContext, exactly(100), exactly(100), new Size());

    LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    lithoView.setComponentTree(holder.getComponentTree());

    lithoView.mountComponent(new Rect(0, 0, 100, 30), true);
    assertThat(tracker1.isMounted())
        .describedAs("Visible component WITHOUT excludeFromIM should get mounted")
        .isTrue();
    assertThat(tracker2.isMounted())
        .describedAs("Invisible component WITH excludeFromIM should get mounted")
        .isTrue();
    assertThat(tracker3.isMounted())
        .describedAs("Invisible component WITHOUT excludeFromIM should Not get mounted")
        .isFalse();
    // verify that the visibility callback of the visible component should be called
    verify(eventHandler1, times(1)).call(any(VisibleEvent.class));
    // verify that the visibility callback of the invisible component should not be called
    verify(eventHandler2, times(0)).call(any(VisibleEvent.class));

    // move the view out of visible area and make sure the component that marked as excludeFromIM
    // will not get unmounted
    lithoView.notifyVisibleBoundsChanged(new Rect(0, -50, 100, -10), true);
    assertThat(tracker1.isMounted())
        .describedAs("Invisible component WITHOUT excludeFromIM should get unmounted")
        .isFalse();
    assertThat(tracker2.isMounted())
        .describedAs("Invisible component WITH excludeFromIM should Not get unmounted")
        .isTrue();
    assertThat(tracker3.isMounted())
        .describedAs("Invisible component WITHOUT excludeFromIM should get mounted")
        .isFalse();
    // verify that the visibility callback of the invisible component should not be called
    verify(eventHandler1, times(1)).call(any(VisibleEvent.class));
    verify(eventHandler2, times(0)).call(any(VisibleEvent.class));

    lithoView.notifyVisibleBoundsChanged(new Rect(0, 15, 100, 45), true);
    // verify that the visibility callback of the visible component should be called
    verify(eventHandler1, times(2)).call(any(VisibleEvent.class));
    verify(eventHandler2, times(1)).call(any(VisibleEvent.class));
  }

  @Test
  public void testExcludeFromIncrementalMount() {

    final Component child = create(mContext).widthPx(100).heightPx(100).build();
    final LifecycleTracker skipIMTracker = new LifecycleTracker();
    final Component excludeIMComponent =
        MountSpecExcludeFromIncrementalMount.create(mContext)
            .lifecycleTracker(skipIMTracker)
            .widthPx(100)
            .heightPx(100)
            .build();
    final LifecycleTracker notSkipIMTracker = new LifecycleTracker();
    final Component doesNotExcludeIMComponent =
        MountSpecLifecycleTester.create(mContext)
            .lifecycleTracker(notSkipIMTracker)
            .widthPx(100)
            .heightPx(100)
            .build();

    final Component root =
        Column.create(mContext)
            .widthPercent(100)
            .heightPercent(100)
            .child(
                Column.create(mContext)
                    .child(child)
                    .child(doesNotExcludeIMComponent)
                    .child(excludeIMComponent))
            .build();

    LithoView lithoView = new LithoView(mContext);
    mLegacyLithoViewRule
        .useLithoView(lithoView)
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(exactly(100), exactly(500))
        .measure()
        .layout();

    lithoView.mountComponent(new Rect(0, 400, 100, 500), false);
    assertThat(notSkipIMTracker.isMounted())
        .describedAs(
            "Component without excludeFromIncrementalMount doesn't get mounted when out of visible rect")
        .isFalse();
    assertThat(skipIMTracker.isMounted())
        .describedAs(
            "Component with excludeFromIncrementalMount do get mounted when out of visible rect")
        .isTrue();

    lithoView.mountComponent(new Rect(0, 0, 100, 300), false);
    assertThat(notSkipIMTracker.isMounted())
        .describedAs(
            "Component without excludeFromIncrementalMount get mounted when in visible rect")
        .isTrue();
    assertThat(skipIMTracker.isMounted())
        .describedAs("Component with excludeFromIncrementalMount get mounted when in visible rect")
        .isTrue();

    lithoView.mountComponent(new Rect(0, 400, 50, 450), false);
    assertThat(notSkipIMTracker.isMounted())
        .describedAs(
            "Component without excludeFromIncrementalMount get unmounted when out of visible rect")
        .isFalse();
    assertThat(skipIMTracker.isMounted())
        .describedAs(
            "Component with excludeFromIncrementalMount doesn't get unmounted when out of visible rect")
        .isTrue();

    lithoView.mountComponent(new Rect(0, 400, 50, 450), false);
    assertThat(skipIMTracker.isMounted())
        .describedAs(
            "Component with excludeFromIncrementalMount doesn't get unmounted while doing IncrementalMount")
        .isTrue();
  }

  /** Tests incremental mount behaviour of a vertical stack of components with a View mount type. */
  @Test
  public void testIncrementalMountVerticalViewStackScrollUp() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, -10, 10, -5), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 0, 10, 5), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 15, 10, 25), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 20, 10, 30), true);
    assertThat(child1.isMounted()).isFalse();

    // Inc-Mount-Ext will properly unmount items when their bottom is equal to the container's
    // top.
    assertThat(child2.isMounted()).isFalse();
  }

  @Test
  public void testIncrementalMountVerticalViewStackScrollDown() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 20, 10, 30), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 15, 10, 25), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 0, 10, 9), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, -10, 10, -5), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();
  }

  @Test
  public void incrementalMount_visibleTopIntersectsItemBottom_unmountItem() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final TestComponent child3 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 10, 10, 30), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();
    assertThat(child3.isMounted()).isTrue();
  }

  @Test
  public void incrementalMount_visibleBottomIntersectsItemTop_unmountItem() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final TestComponent child3 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 10, 20), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();
    assertThat(child3.isMounted()).isFalse();
  }

  @Test
  public void incrementalMount_visibleRectIntersectsItemBounds_mountItem() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final TestComponent child3 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 10, 10, 20), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();
    assertThat(child3.isMounted()).isFalse();
  }

  @Test
  public void incrementalMount_visibleBoundsEmpty_unmountAllItems() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final TestComponent child3 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 0, 0), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();
    assertThat(child3.isMounted()).isFalse();
  }

  @Test
  public void incrementalMount_emptyItemBoundsIntersectVisibleRect_mountItem() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final TestComponent child3 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(0))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 10, 30), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();
    assertThat(child3.isMounted()).isTrue();
  }

  @Test
  public void incrementalMount_emptyItemBoundsEmptyVisibleRect_unmountItem() {
    final TestComponent child1 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(0))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 10, 0), true);
    assertThat(child1.isMounted()).isFalse();
  }

  /**
   * Tests incremental mount behaviour of a horizontal stack of components with a View mount type.
   */
  @Test
  public void testIncrementalMountHorizontalViewStack() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final Component root =
        Row.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(-10, 0, -5, 10), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 0, 5, 10), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(5, 0, 15, 10), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(15, 0, 25, 10), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(20, 0, 30, 10), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();
  }

  /**
   * Tests incremental mount behaviour of a vertical stack of components with a Drawable mount type.
   */
  @Test
  public void testIncrementalMountVerticalDrawableStack() {
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker1).build();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final Component child2 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker2).build();

    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, -10, 10, -5), true);
    assertThat(lifecycleTracker1.isMounted()).isFalse();
    assertThat(lifecycleTracker2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 0, 10, 5), true);
    assertThat(lifecycleTracker1.isMounted()).isTrue();
    assertThat(lifecycleTracker2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(lifecycleTracker1.isMounted()).isTrue();
    assertThat(lifecycleTracker2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 15, 10, 25), true);
    assertThat(lifecycleTracker1.isMounted()).isFalse();
    assertThat(lifecycleTracker2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(0, 20, 10, 30), true);
    assertThat(lifecycleTracker1.isMounted()).isFalse();

    // Inc-Mount-Ext will properly unmount items when their bottom is equal to the container's
    // top.
    assertThat(lifecycleTracker2.isMounted()).isFalse();
  }

  /** Tests incremental mount behaviour of a view mount item in a nested hierarchy. */
  @Test
  public void testIncrementalMountNestedView() {
    final TestComponent child = create(mContext).build();

    final Component root =
        Column.create(mContext)
            .wrapInView()
            .paddingPx(ALL, 20)
            .child(Wrapper.create(mContext).delegate(child).widthPx(10).heightPx(10))
            .child(SimpleMountSpecTester.create(mContext))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 50, 20), true);
    assertThat(child.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 0, 50, 40), true);
    assertThat(child.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(30, 0, 50, 40), true);
    assertThat(child.isMounted()).isFalse();
  }

  /**
   * Verify that we can cope with a negative padding on a component that is wrapped in a view (since
   * the bounds of the component will be larger than the bounds of the view).
   */
  @Test
  @Ignore("T146174263")
  public void testIncrementalMountVerticalDrawableStackNegativeMargin() {
    // When self managing, LithoViews will not adhere to translation. Therefore components with
    // negative margins + translations will not be mounted, hence this test is not relevant
    // in this case.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final FrameLayout parent = new FrameLayout(mContext.getAndroidContext());
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 10, 1000);

    mLegacyLithoViewRule
        .setRoot(Row.create(mContext).build())
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    parent.addView(lithoView);

    lithoView.setTranslationY(105);

    final EventHandler eventHandler = mock(EventHandler.class);
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTesterDrawable.create(mContext)
            .lifecycleTracker(lifecycleTracker1)
            .build();
    final Component childHost1 =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .widthPx(10)
                    .heightPx(10)
                    .clickHandler(eventHandler)
                    .marginDip(YogaEdge.TOP, -10))
            .build();

    final Component rootHost =
        Row.create(mContext)
            .child(Wrapper.create(mContext).delegate(childHost1).clickHandler(eventHandler).build())
            .build();

    lithoView.getComponentTree().setRoot(rootHost);

    assertThat(lifecycleTracker1.getSteps()).contains(LifecycleStep.ON_MOUNT);
  }

  @Test
  @Ignore("T146174263")
  public void testIncrementalMountVerticalDrawableStackNegativeMargin_multipleUnmountedHosts() {
    // When self managing, LithoViews do not adhere to translation, and so items set with negative
    // margins won't be mounted.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final FrameLayout parent = new FrameLayout(mContext.getAndroidContext());
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 10, 1000);

    mLegacyLithoViewRule
        .setRoot(Row.create(mContext).build())
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    parent.addView(lithoView);

    lithoView.setTranslationY(105);

    final EventHandler eventHandler = mock(EventHandler.class);
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTesterDrawable.create(mContext)
            .lifecycleTracker(lifecycleTracker1)
            .build();
    final Component childHost1 =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .widthPx(10)
                    .heightPx(10)
                    .clickHandler(eventHandler)
                    .marginDip(YogaEdge.TOP, -10))
            .build();

    final Component rootHost =
        Row.create(mContext)
            .child(
                Row.create(mContext)
                    .viewTag("extra_host")
                    .child(
                        Wrapper.create(mContext)
                            .delegate(childHost1)
                            .clickHandler(eventHandler)
                            .build())
                    .child(
                        Wrapper.create(mContext)
                            .delegate(childHost1)
                            .clickHandler(eventHandler)
                            .build()))
            .build();

    lithoView.getComponentTree().setRoot(rootHost);

    assertThat(lifecycleTracker1.getSteps()).contains(LifecycleStep.ON_MOUNT);
  }

  @Test
  public void itemWithNegativeMargin_removeAndAdd_hostIsMounted() {
    final FrameLayout parent = new FrameLayout(mContext.getAndroidContext());
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 10, 1000);

    mLegacyLithoViewRule
        .setRoot(Row.create(mContext).build())
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    parent.addView(lithoView);

    lithoView.setTranslationY(95);

    final EventHandler eventHandler1 = mock(EventHandler.class);
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTesterDrawable.create(mContext)
            .lifecycleTracker(lifecycleTracker1)
            .build();
    final Component childHost1 =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .widthPx(10)
                    .heightPx(10)
                    .clickHandler(eventHandler1))
            .build();

    final Component host1 =
        Row.create(mContext)
            .child(
                Wrapper.create(mContext).delegate(childHost1).clickHandler(eventHandler1).build())
            .build();

    final EventHandler eventHandler2 = mock(EventHandler.class);
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final Component child2 =
        MountSpecLifecycleTesterDrawable.create(mContext)
            .lifecycleTracker(lifecycleTracker2)
            .build();
    final Component childHost2 =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child2)
                    .widthPx(10)
                    .heightPx(10)
                    .clickHandler(eventHandler2)
                    .marginDip(YogaEdge.TOP, -10))
            .build();

    final Component host2 =
        Row.create(mContext)
            .child(
                Wrapper.create(mContext).delegate(childHost2).clickHandler(eventHandler2).build())
            .build();

    final Component rootHost = Column.create(mContext).child(host1).child(host2).build();

    // Mount both child1 and child2.
    lithoView.getComponentTree().setRoot(rootHost);

    assertThat(lifecycleTracker2.getSteps()).contains(LifecycleStep.ON_MOUNT);
    lifecycleTracker2.reset();

    // Remove child2.
    final Component newHost = Column.create(mContext).child(host1).build();
    lithoView.getComponentTree().setRoot(newHost);

    // Add child2 back.
    assertThat(lifecycleTracker2.getSteps()).contains(LifecycleStep.ON_UNMOUNT);
    lifecycleTracker2.reset();

    lithoView.getComponentTree().setRoot(rootHost);

    assertThat(lifecycleTracker2.getSteps()).contains(LifecycleStep.ON_MOUNT);
  }

  /** Tests incremental mount behaviour of overlapping view mount items. */
  @Test
  public void testIncrementalMountOverlappingView() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .positionType(ABSOLUTE)
                    .positionPx(TOP, 0)
                    .positionPx(LEFT, 0)
                    .widthPx(10)
                    .heightPx(10))
            .child(
                Wrapper.create(mContext)
                    .delegate(child2)
                    .positionType(ABSOLUTE)
                    .positionPx(TOP, 5)
                    .positionPx(LEFT, 5)
                    .widthPx(10)
                    .heightPx(10))
            .child(SimpleMountSpecTester.create(mContext))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 5, 5), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(5, 5, 10, 10), true);
    assertThat(child1.isMounted()).isTrue();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(10, 10, 15, 15), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isTrue();

    lithoView.mountComponent(new Rect(15, 15, 20, 20), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child2.isMounted()).isFalse();
  }

  @Test
  public void testChildViewGroupIncrementallyMounted() {
    // Incremental mounting works differently with self-managing LithoViews, so checking calls
    // to notifyVisibleBoundsChanged is not needed.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final ViewGroup mountedView = mock(ViewGroup.class);
    when(mountedView.getChildCount()).thenReturn(3);

    final LithoView childView1 = getMockLithoViewWithBounds(new Rect(5, 10, 20, 30));
    when(mountedView.getChildAt(0)).thenReturn(childView1);

    final LithoView childView2 = getMockLithoViewWithBounds(new Rect(10, 10, 50, 60));
    when(mountedView.getChildAt(1)).thenReturn(childView2);

    final LithoView childView3 = getMockLithoViewWithBounds(new Rect(30, 35, 50, 60));
    when(mountedView.getChildAt(2)).thenReturn(childView3);

    final Component root = TestViewComponent.create(mContext).testView(mountedView).build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    verify(childView1).notifyVisibleBoundsChanged();
    verify(childView2).notifyVisibleBoundsChanged();
    verify(childView3).notifyVisibleBoundsChanged();

    reset(childView1);
    when(childView1.isIncrementalMountEnabled()).thenReturn(true);
    reset(childView2);
    when(childView2.isIncrementalMountEnabled()).thenReturn(true);
    reset(childView3);
    when(childView3.isIncrementalMountEnabled()).thenReturn(true);

    lithoView.mountComponent(new Rect(15, 15, 40, 40), true);

    verify(childView1, times(1)).notifyVisibleBoundsChanged();
    verify(childView2, times(1)).notifyVisibleBoundsChanged();
    verify(childView3, times(1)).notifyVisibleBoundsChanged();
  }

  @Test
  public void testChildViewGroupAllIncrementallyMountedNotProcessVisibilityOutputs() {
    // Incremental mounting works differently with self-managing LithoViews, so checking calls
    // to notifyVisibleBoundsChanged is not needed.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final ViewGroup mountedView = mock(ViewGroup.class);
    when(mountedView.getLeft()).thenReturn(0);
    when(mountedView.getTop()).thenReturn(0);
    when(mountedView.getRight()).thenReturn(100);
    when(mountedView.getBottom()).thenReturn(100);
    when(mountedView.getChildCount()).thenReturn(3);

    final LithoView childView1 = getMockLithoViewWithBounds(new Rect(5, 10, 20, 30));
    when(childView1.getTranslationX()).thenReturn(5.0f);
    when(childView1.getTranslationY()).thenReturn(-10.0f);
    when(mountedView.getChildAt(0)).thenReturn(childView1);

    final LithoView childView2 = getMockLithoViewWithBounds(new Rect(10, 10, 50, 60));
    when(mountedView.getChildAt(1)).thenReturn(childView2);

    final LithoView childView3 = getMockLithoViewWithBounds(new Rect(30, 35, 50, 60));
    when(mountedView.getChildAt(2)).thenReturn(childView3);

    final Component root = TestViewComponent.create(mContext).testView(mountedView).build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    // Can't verify directly as the object will have changed by the time we get the chance to
    // verify it.
    doAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                Rect rect = (Rect) invocation.getArguments()[0];
                if (!rect.equals(new Rect(0, 0, 15, 20))) {
                  fail();
                }
                return null;
              }
            })
        .when(childView1)
        .notifyVisibleBoundsChanged((Rect) any(), eq(true));

    doAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                Rect rect = (Rect) invocation.getArguments()[0];
                if (!rect.equals(new Rect(0, 0, 40, 50))) {
                  fail();
                }
                return null;
              }
            })
        .when(childView2)
        .notifyVisibleBoundsChanged((Rect) any(), eq(true));

    doAnswer(
            new Answer<Object>() {
              @Override
              public Object answer(InvocationOnMock invocation) throws Throwable {
                Rect rect = (Rect) invocation.getArguments()[0];
                if (!rect.equals(new Rect(0, 0, 20, 25))) {
                  fail();
                }
                return null;
              }
            })
        .when(childView3)
        .notifyVisibleBoundsChanged((Rect) any(), eq(true));

    verify(childView1).notifyVisibleBoundsChanged();
    verify(childView2).notifyVisibleBoundsChanged();
    verify(childView3).notifyVisibleBoundsChanged();

    reset(childView1);
    when(childView1.isIncrementalMountEnabled()).thenReturn(true);
    reset(childView2);
    when(childView2.isIncrementalMountEnabled()).thenReturn(true);
    reset(childView3);
    when(childView3.isIncrementalMountEnabled()).thenReturn(true);

    lithoView.mountComponent(new Rect(0, 0, 100, 100), true);

    verify(childView1, times(1)).notifyVisibleBoundsChanged();
    verify(childView2, times(1)).notifyVisibleBoundsChanged();
    verify(childView3, times(1)).notifyVisibleBoundsChanged();
  }

  /** Tests incremental mount behaviour of a vertical stack of components with a View mount type. */
  @Test
  public void testIncrementalMountDoesNotCauseMultipleUpdates() {
    final TestComponent child1 = create(mContext).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, -10, 10, -5), true);
    assertThat(child1.isMounted()).isFalse();
    assertThat(child1.wasOnUnbindCalled()).isTrue();
    assertThat(child1.wasOnUnmountCalled()).isTrue();

    lithoView.mountComponent(new Rect(0, 0, 10, 5), true);
    assertThat(child1.isMounted()).isTrue();

    child1.resetInteractions();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(child1.isMounted()).isTrue();

    assertThat(child1.wasOnBindCalled()).isFalse();
    assertThat(child1.wasOnMountCalled()).isFalse();
    assertThat(child1.wasOnUnbindCalled()).isFalse();
    assertThat(child1.wasOnUnmountCalled()).isFalse();
  }

  /**
   * Tests incremental mount behaviour of a vertical stack of components with a Drawable mount type
   * after unmountAllItems was called.
   */
  @Test
  public void testIncrementalMountAfterUnmountAllItemsCall() {
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker1).build();
    final Component child2 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker2).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, -10, 10, -5), true);
    assertThat(lifecycleTracker1.isMounted()).isFalse();
    assertThat(lifecycleTracker2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 0, 10, 5), true);
    assertThat(lifecycleTracker1.isMounted()).isTrue();
    assertThat(lifecycleTracker2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(lifecycleTracker1.isMounted()).isTrue();
    assertThat(lifecycleTracker2.isMounted()).isTrue();

    lithoView.unmountAllItems();
    assertThat(lifecycleTracker1.isMounted()).isFalse();
    assertThat(lifecycleTracker2.isMounted()).isFalse();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(lifecycleTracker1.isMounted()).isTrue();
    assertThat(lifecycleTracker2.isMounted()).isTrue();
  }

  @Test
  public void testMountStateNeedsRemount_incrementalMountAfterUnmount_isFalse() {
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final Component child1 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker1).build();
    final Component child2 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker2).build();
    final Component root =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    assertThat(lithoView.mountStateNeedsRemount()).isFalse();

    lithoView.unmountAllItems();
    assertThat(lithoView.mountStateNeedsRemount()).isTrue();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    assertThat(lithoView.mountStateNeedsRemount()).isFalse();
  }

  @Test
  public void testRootViewAttributes_incrementalMountAfterUnmount_setViewAttributes() {
    enableAccessibility();
    final Component root = Text.create(mContext).text("Test").contentDescription("testcd").build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();
    View innerView = lithoView.getChildAt(0);
    assertThat(innerView.getContentDescription()).isEqualTo("testcd");

    lithoView.unmountAllItems();
    assertThat(innerView.getContentDescription()).isNull();

    lithoView.mountComponent(new Rect(0, 5, 10, 15), true);
    innerView = lithoView.getChildAt(0);

    assertThat(innerView.getContentDescription()).isEqualTo("testcd");
  }

  /**
   * Tests incremental mount behaviour of a nested Litho View. We want to ensure that when a child
   * view is first mounted due to a layout pass it does not also have notifyVisibleBoundsChanged
   * called on it.
   */
  @Test
  public void testIncrementalMountAfterLithoViewIsMounted() {
    // Incremental mounting works differently with self-managing LithoViews, so checking calls
    // to notifyVisibleBoundsChanged is not needed.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final LithoView lithoView = mock(LithoView.class);
    when(lithoView.isIncrementalMountEnabled()).thenReturn(true);

    final ViewGroupWithLithoViewChildren viewGroup =
        new ViewGroupWithLithoViewChildren(mContext.getAndroidContext());
    viewGroup.addView(lithoView);

    final Component root =
        TestViewComponent.create(mContext, true, true, true).testView(viewGroup).build();
    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(1000, EXACTLY), makeSizeSpec(1000, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoViewParent = mLegacyLithoViewRule.getLithoView();

    verify(lithoView).notifyVisibleBoundsChanged();
    reset(lithoView);

    // Mount views with visible rect
    lithoViewParent.mountComponent(new Rect(0, 0, 100, 1000), true);
    verify(lithoView, times(1)).notifyVisibleBoundsChanged();
    reset(lithoView);
    when(lithoView.isIncrementalMountEnabled()).thenReturn(true);

    // Unmount views with visible rect outside
    lithoViewParent.mountComponent(new Rect(0, -10, 100, -5), true);
    verify(lithoView, never()).notifyVisibleBoundsChanged();
    reset(lithoView);
    when(lithoView.isIncrementalMountEnabled()).thenReturn(true);

    // Mount again with visible rect
    lithoViewParent.mountComponent(new Rect(0, 0, 100, 1000), true);

    verify(lithoView, times(1)).notifyVisibleBoundsChanged();
  }

  @Test
  public void incrementalMount_dirtyMount_unmountItemsOffScreen_withScroll() {
    final LifecycleTracker info_child1 = new LifecycleTracker();
    final LifecycleTracker info_child2 = new LifecycleTracker();
    final SimpleStateUpdateEmulatorSpec.Caller stateUpdater =
        new SimpleStateUpdateEmulatorSpec.Caller();

    final Component root =
        Column.create(mLegacyLithoViewRule.getContext())
            .child(
                MountSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
                    .intrinsicSize(new Size(10, 10))
                    .lifecycleTracker(info_child1)
                    .key("some_key"))
            .child(
                MountSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
                    .intrinsicSize(new Size(10, 10))
                    .lifecycleTracker(info_child2)
                    .key("other_key"))
            .child(
                SimpleStateUpdateEmulator.create(mLegacyLithoViewRule.getContext())
                    .caller(stateUpdater))
            .build();

    mLegacyLithoViewRule.setRoot(root).setSizePx(10, 40).attachToWindow().measure().layout();

    final FrameLayout parent = new FrameLayout(mContext.getAndroidContext());
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 10, 40);

    parent.addView(mLegacyLithoViewRule.getLithoView(), 0, 40);

    final ScrollView scrollView = new ScrollView(mContext.getAndroidContext());
    scrollView.measure(
        View.MeasureSpec.makeMeasureSpec(10, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY));
    scrollView.layout(0, 0, 10, 20);
    scrollView.addView(parent, 10, 40);

    assertThat(info_child1.getSteps()).describedAs("Mounted.").contains(ON_MOUNT);
    assertThat(info_child2.getSteps()).describedAs("Mounted.").contains(ON_MOUNT);

    stateUpdater.increment();

    info_child1.reset();
    info_child2.reset();

    scrollView.scrollBy(0, 12);
    mLegacyLithoViewRule.dispatchGlobalLayout();
    assertThat(info_child1.getSteps()).describedAs("Mounted.").contains(ON_UNMOUNT);
  }

  @Test
  public void incrementalMount_dirtyMount_unmountItemsOffScreen_withTranslation() {
    // When self-managing LithoViews, translation is ignored. Therefore, this test is redundant
    // when the config is enabled.
    if (ComponentsConfiguration.lithoViewSelfManageViewPortChanges) {
      return;
    }

    final LifecycleTracker info_child1 = new LifecycleTracker();
    final LifecycleTracker info_child2 = new LifecycleTracker();
    final SimpleStateUpdateEmulatorSpec.Caller stateUpdater =
        new SimpleStateUpdateEmulatorSpec.Caller();

    final Component root =
        Column.create(mLegacyLithoViewRule.getContext())
            .child(
                MountSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
                    .intrinsicSize(new Size(10, 10))
                    .lifecycleTracker(info_child1)
                    .key("some_key"))
            .child(
                MountSpecLifecycleTester.create(mLegacyLithoViewRule.getContext())
                    .intrinsicSize(new Size(10, 10))
                    .lifecycleTracker(info_child2)
                    .key("other_key"))
            .child(
                SimpleStateUpdateEmulator.create(mLegacyLithoViewRule.getContext())
                    .caller(stateUpdater))
            .build();

    mLegacyLithoViewRule.setRoot(root).setSizePx(10, 20).attachToWindow().measure().layout();

    final FrameLayout parent = new FrameLayout(mContext.getAndroidContext());
    parent.measure(
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
    parent.layout(0, 0, 10, 20);

    parent.addView(mLegacyLithoViewRule.getLithoView(), 0, 20);

    assertThat(info_child1.getSteps()).describedAs("Mounted.").contains(ON_MOUNT);
    assertThat(info_child2.getSteps()).describedAs("Mounted.").contains(ON_MOUNT);

    stateUpdater.increment();

    info_child1.reset();
    info_child2.reset();

    mLegacyLithoViewRule.getLithoView().setTranslationY(-12);

    assertThat(info_child1.getSteps()).describedAs("Mounted.").contains(ON_UNMOUNT);
  }

  @Test
  public void incrementalMount_setVisibilityHintFalse_preventMount() {
    final TestComponent child1 = create(mContext).build();
    final TestComponent child2 = create(mContext).build();

    final EventHandler<VisibleEvent> visibleEventHandler = new EventHandler<>(child1, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler = new EventHandler<>(child1, 2);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .child(
                Wrapper.create(mContext)
                    .delegate(child2)
                    .visibleHandler(visibleEventHandler)
                    .invisibleHandler(invisibleEventHandler)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(20, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    lithoView.mountComponent(new Rect(0, 0, 10, 5), true);

    assertThat(child2.isMounted()).isFalse();

    child1.getDispatchedEventHandlers().clear();
    child1.resetInteractions();

    lithoView.setVisibilityHint(false, true);

    assertThat(child1.wasOnMountCalled()).isFalse();
    assertThat(child1.wasOnUnmountCalled()).isFalse();
    assertThat(child1.getDispatchedEventHandlers()).contains(invisibleEventHandler);
    assertThat(child1.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);

    child1.getDispatchedEventHandlers().clear();
    child1.resetInteractions();
    child2.resetInteractions();

    lithoView.mountComponent(new Rect(0, 0, 10, 20), true);

    assertThat(child2.wasOnMountCalled()).isFalse();
    assertThat(child1.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler);
    assertThat(child1.getDispatchedEventHandlers()).doesNotContain(invisibleEventHandler);
  }

  @Test
  public void incrementalMount_setVisibilityHintTrue_mountIfNeeded() {
    final TestComponent child1 = create(mContext).build();

    final EventHandler<VisibleEvent> visibleEventHandler1 = new EventHandler<>(child1, 1);
    final EventHandler<InvisibleEvent> invisibleEventHandler1 = new EventHandler<>(child1, 2);

    final Component root =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .visibleHandler(visibleEventHandler1)
                    .invisibleHandler(invisibleEventHandler1)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    mLegacyLithoViewRule
        .setRoot(root)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(100, EXACTLY))
        .measure()
        .layout();

    final LithoView lithoView = mLegacyLithoViewRule.getLithoView();

    assertThat(child1.getDispatchedEventHandlers()).contains(visibleEventHandler1);

    lithoView.setVisibilityHint(false, true);

    final TestComponent child2 = create(mContext).build();
    final EventHandler<VisibleEvent> visibleEventHandler2 = new EventHandler<>(child2, 3);
    final EventHandler<InvisibleEvent> invisibleEventHandler2 = new EventHandler<>(child2, 4);
    final Component newRoot =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(child1)
                    .visibleHandler(visibleEventHandler1)
                    .invisibleHandler(invisibleEventHandler1)
                    .widthPx(10)
                    .heightPx(10))
            .child(
                Wrapper.create(mContext)
                    .delegate(child2)
                    .visibleHandler(visibleEventHandler2)
                    .invisibleHandler(invisibleEventHandler2)
                    .widthPx(10)
                    .heightPx(10))
            .build();

    lithoView.getComponentTree().setRoot(newRoot);
    assertThat(child2.wasOnMountCalled()).isFalse();
    assertThat(child2.getDispatchedEventHandlers()).doesNotContain(visibleEventHandler2);

    lithoView.setVisibilityHint(true, true);
    assertThat(child2.wasOnMountCalled()).isTrue();
    assertThat(child2.getDispatchedEventHandlers()).contains(visibleEventHandler2);
  }

  @Test
  public void dirtyMount_visibleRectChanged_unmountItemNotInVisibleBounds() {
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker3 = new LifecycleTracker();

    final Component child1 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker1).build();
    final Component child2 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker2).build();
    final Component child3 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker3).build();

    final Component root1 =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    final RecyclerBinderConfiguration binderConfig =
        RecyclerBinderConfiguration.create().lithoViewFactory(getLithoViewFactory()).build();
    RecyclerConfiguration config =
        ListRecyclerConfiguration.create().recyclerBinderConfiguration(binderConfig).build();

    final Component rcc =
        RecyclerCollectionComponent.create(mContext)
            .recyclerConfiguration(config)
            .section(
                SingleComponentSection.create(new SectionContext(mContext))
                    .component(root1)
                    .build())
            .build();

    mLegacyLithoViewRule
        .setRoot(rcc)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(10, EXACTLY), makeSizeSpec(19, EXACTLY))
        .measure()
        .layout();

    assertThat(lifecycleTracker1.getSteps()).contains(LifecycleStep.ON_MOUNT);
    assertThat(lifecycleTracker2.getSteps()).contains(LifecycleStep.ON_MOUNT);
    assertThat(lifecycleTracker3.getSteps()).doesNotContain(LifecycleStep.ON_MOUNT);

    lifecycleTracker1.reset();
    lifecycleTracker2.reset();
    lifecycleTracker3.reset();

    final Component root2 =
        Column.create(mContext)
            .child(
                Wrapper.create(mContext)
                    .delegate(
                        MountSpecLifecycleTester.create(mContext)
                            .lifecycleTracker(lifecycleTracker1)
                            .build())
                    .widthPx(10)
                    .heightPx(20))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(10))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(10))
            .build();

    final Component rcc2 =
        RecyclerCollectionComponent.create(mContext)
            .recyclerConfiguration(config)
            .section(
                SingleComponentSection.create(new SectionContext(mContext))
                    .component(root2)
                    .sticky(true)
                    .build())
            .build();

    mLegacyLithoViewRule.setRoot(rcc2);

    runToEndOfTasks();
    mLegacyLithoViewRule.dispatchGlobalLayout();

    assertThat(lifecycleTracker2.getSteps()).contains(LifecycleStep.ON_UNMOUNT);
  }

  @Test
  public void incrementalMount_testScrollDownAndUp_correctMountUnmountCalls() {
    final LifecycleTracker lifecycleTracker1 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker2 = new LifecycleTracker();
    final LifecycleTracker lifecycleTracker3 = new LifecycleTracker();

    final int CHILD_HEIGHT = 10;

    final Component child1 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker1).build();
    final Component child2 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker2).build();
    final Component child3 =
        MountSpecLifecycleTester.create(mContext).lifecycleTracker(lifecycleTracker3).build();

    // Item is composed of 3 children of equal size (10x10), making 1 item of height 30.
    final Component item =
        Column.create(mContext)
            .child(Wrapper.create(mContext).delegate(child1).widthPx(10).heightPx(CHILD_HEIGHT))
            .child(Wrapper.create(mContext).delegate(child2).widthPx(10).heightPx(CHILD_HEIGHT))
            .child(Wrapper.create(mContext).delegate(child3).widthPx(10).heightPx(CHILD_HEIGHT))
            .build();

    final RecyclerBinderConfiguration binderConfig =
        RecyclerBinderConfiguration.create().lithoViewFactory(getLithoViewFactory()).build();
    RecyclerConfiguration config =
        ListRecyclerConfiguration.create().recyclerBinderConfiguration(binderConfig).build();

    final SectionContext sectionContext = new SectionContext(mContext);
    final Component rcc =
        RecyclerCollectionComponent.create(mContext)
            .recyclerConfiguration(config)
            .section(
                DynamicComponentGroupSection.create(sectionContext)
                    .component(item)
                    .totalItems(5)
                    .build())
            .build();

    // Set LithoView with height so that it can fully show exactly 3 items (3 children per item).
    mLegacyLithoViewRule
        .setRoot(rcc)
        .attachToWindow()
        .setSizeSpecs(makeSizeSpec(100, EXACTLY), makeSizeSpec(CHILD_HEIGHT * 9, EXACTLY))
        .measure()
        .layout();

    // Obtain the RV for scrolling later
    final RecyclerView recyclerView =
        ((SectionsRecyclerView) mLegacyLithoViewRule.getLithoView().getChildAt(0))
            .getRecyclerView();

    // All 3 children are visible 3 times, so we should see ON_MOUNT being called 3 times
    // for each child
    assertThat(getCountOfLifecycleSteps(lifecycleTracker1.getSteps(), ON_MOUNT)).isEqualTo(3);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker2.getSteps(), ON_MOUNT)).isEqualTo(3);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker3.getSteps(), ON_MOUNT)).isEqualTo(3);

    // Clear the lifecycle steps
    lifecycleTracker1.reset();
    lifecycleTracker2.reset();
    lifecycleTracker3.reset();

    // Scroll down by the size of 1 child. We are expecting to top item's child1 to be
    // unmounted, and a new bottom item's child1 to be mounted.
    recyclerView.scrollBy(0, CHILD_HEIGHT);

    // Ensure unmount is called once
    assertThat(getCountOfLifecycleSteps(lifecycleTracker1.getSteps(), ON_UNMOUNT)).isEqualTo(1);

    // Ensure mount is called once
    // When using Litho's inc-mount, the exiting item will be mounted twice due to an issue with
    // the calculation there. Inc-mount-ext does not have this issue.
    assertThat(getCountOfLifecycleSteps(lifecycleTracker1.getSteps(), ON_MOUNT)).isEqualTo(1);

    // child2 & 3 of all items should not change.
    assertThat(getCountOfLifecycleSteps(lifecycleTracker2.getSteps(), ON_UNMOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker2.getSteps(), ON_MOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker3.getSteps(), ON_UNMOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker3.getSteps(), ON_MOUNT)).isEqualTo(0);

    // Clear the lifecycle steps
    lifecycleTracker1.reset();
    lifecycleTracker2.reset();
    lifecycleTracker3.reset();

    // Scroll up by the size of 1 component. We are expecting to top item's child1 to be mounted,
    // and the bottom item to be unmounted
    recyclerView.scrollBy(0, -CHILD_HEIGHT);

    // Ensure unmount is called once
    assertThat(getCountOfLifecycleSteps(lifecycleTracker1.getSteps(), ON_UNMOUNT)).isEqualTo(1);

    // Ensure mount is called once
    // When using Litho's inc-mount, the item we previously expected to exit is still there, so
    // we don't expect a mount to occur.
    assertThat(getCountOfLifecycleSteps(lifecycleTracker1.getSteps(), ON_MOUNT)).isEqualTo(1);

    // child2 & 3 of all items should not change.
    assertThat(getCountOfLifecycleSteps(lifecycleTracker2.getSteps(), ON_UNMOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker2.getSteps(), ON_MOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker3.getSteps(), ON_UNMOUNT)).isEqualTo(0);
    assertThat(getCountOfLifecycleSteps(lifecycleTracker3.getSteps(), ON_MOUNT)).isEqualTo(0);
  }

  /** Returns the amount of steps that match the given step in the given list of steps */
  private static int getCountOfLifecycleSteps(List<LifecycleStep> steps, LifecycleStep step) {
    int count = 0;
    for (int i = 0; i < steps.size(); i++) {
      if (steps.get(i) == step) {
        count++;
      }
    }

    return count;
  }

  @After
  public void restoreConfiguration() {
    TempComponentsConfigurations.restoreShouldAddHostViewForRootComponent();
    AccessibilityUtils.invalidateCachedIsAccessibilityEnabled();
    validateMockitoUsage();
  }

  private static void enableAccessibility() {
    AccessibilityUtils.invalidateCachedIsAccessibilityEnabled();
    final ShadowAccessibilityManager manager =
        Shadows.shadowOf(
            (AccessibilityManager) getApplicationContext().getSystemService(ACCESSIBILITY_SERVICE));
    manager.setEnabled(true);
    manager.setTouchExplorationEnabled(true);
  }

  private LithoViewFactory getLithoViewFactory() {
    return new LithoViewFactory() {
      @Override
      public LithoView createLithoView(ComponentContext context) {
        return new LithoView(context);
      }
    };
  }

  private static LithoView getMockLithoViewWithBounds(Rect bounds) {
    final LithoView lithoView = mock(LithoView.class);
    when(lithoView.getLeft()).thenReturn(bounds.left);
    when(lithoView.getTop()).thenReturn(bounds.top);
    when(lithoView.getRight()).thenReturn(bounds.right);
    when(lithoView.getBottom()).thenReturn(bounds.bottom);
    when(lithoView.getWidth()).thenReturn(bounds.width());
    when(lithoView.getHeight()).thenReturn(bounds.height());
    when(lithoView.isIncrementalMountEnabled()).thenReturn(true);

    return lithoView;
  }

  private static class TestLithoView extends LithoView {
    private final Rect mPreviousIncrementalMountBounds = new Rect();

    public TestLithoView(Context context) {
      super(context);
    }

    @Override
    public void notifyVisibleBoundsChanged(Rect visibleRect, boolean processVisibilityOutputs) {
      System.out.println("performIncMount on TestLithoView");
      mPreviousIncrementalMountBounds.set(visibleRect);
    }

    private Rect getPreviousIncrementalMountBounds() {
      return mPreviousIncrementalMountBounds;
    }

    @Override
    public boolean isIncrementalMountEnabled() {
      return true;
    }
  }
}

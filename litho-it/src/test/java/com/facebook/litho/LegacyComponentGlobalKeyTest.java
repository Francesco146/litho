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
import static com.facebook.litho.LayoutOutput.getLayoutOutput;
import static com.facebook.litho.LithoRenderUnit.getComponentContext;

import android.view.View;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.config.TempComponentsConfigurations;
import com.facebook.litho.testing.TestViewComponent;
import com.facebook.litho.testing.inlinelayoutspec.InlineLayoutSpec;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import com.facebook.litho.widget.CardClip;
import com.facebook.litho.widget.Text;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LithoTestRunner.class)
public class LegacyComponentGlobalKeyTest {
  private ComponentContext mContext;

  @Before
  public void setup() {
    TempComponentsConfigurations.setShouldAddHostViewForRootComponent(false);
    mContext = new ComponentContext(getApplicationContext());
  }

  @Test
  public void testMultipleChildrenComponentKey() {
    final Component component = getMultipleChildrenComponent();

    int layoutSpecId = component.getTypeId();
    int nestedLayoutSpecId = layoutSpecId - 1;

    final Component column = Column.create(mContext).build();
    final int columnSpecId = column.getTypeId();

    final LithoView lithoView = getLithoView(component);

    // Text
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(layoutSpecId, columnSpecId, "$[Text2]"),
        getComponentContext(lithoView.getMountItemAt(0)).getGlobalKey());
    // TestViewComponent in child layout
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId, columnSpecId, nestedLayoutSpecId, columnSpecId, "$[TestViewComponent1]"),
        getComponentContext(lithoView.getMountItemAt(1)).getGlobalKey());
    // background in child
    Assert.assertNull(getComponentContext(lithoView.getMountItemAt(2)));
    // CardClip in child
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId,
            columnSpecId,
            nestedLayoutSpecId,
            columnSpecId,
            columnSpecId,
            "$[CardClip1]"),
        getComponentContext(lithoView.getMountItemAt(3)).getGlobalKey());
    // Text in child
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId, columnSpecId, nestedLayoutSpecId, columnSpecId, "$[Text1]"),
        getComponentContext(lithoView.getMountItemAt(4)).getGlobalKey());
    // background
    Assert.assertNull(getComponentContext(lithoView.getMountItemAt(5)));
    // CardClip
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId, columnSpecId, columnSpecId, "$[CardClip2]"),
        getComponentContext(lithoView.getMountItemAt(6)).getGlobalKey());
    // TestViewComponent
    Assert.assertEquals(
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId, columnSpecId, "$[TestViewComponent2]"),
        getComponentContext(lithoView.getMountItemAt(7)).getGlobalKey());
  }

  @Test
  public void testOwnerGlobalKey() {
    final Component root = getMultipleChildrenComponent();

    final int layoutSpecId = root.getTypeId();
    final int nestedLayoutSpecId = layoutSpecId - 1;
    final int columnSpecId = Column.create(mContext).build().getTypeId();

    final LithoView lithoView = getLithoView(root);

    final String rootGlobalKey = ComponentKeyUtils.getKeyWithSeparatorForTest(layoutSpecId);
    final String nestedLayoutGlobalKey =
        ComponentKeyUtils.getKeyWithSeparatorForTest(
            layoutSpecId, columnSpecId, nestedLayoutSpecId);

    // Text
    Assert.assertEquals(rootGlobalKey, getComponentAt(lithoView, 0).getOwnerGlobalKey());

    // TestViewComponent in child layout
    Assert.assertEquals(nestedLayoutGlobalKey, getComponentAt(lithoView, 1).getOwnerGlobalKey());

    // CardClip in child
    Assert.assertEquals(nestedLayoutGlobalKey, getComponentAt(lithoView, 3).getOwnerGlobalKey());

    // Text in child
    Assert.assertEquals(nestedLayoutGlobalKey, getComponentAt(lithoView, 4).getOwnerGlobalKey());

    // CardClip
    Assert.assertEquals(rootGlobalKey, getComponentAt(lithoView, 6).getOwnerGlobalKey());

    // TestViewComponent
    Assert.assertEquals(rootGlobalKey, getComponentAt(lithoView, 7).getOwnerGlobalKey());
  }

  private static Component getComponentAt(LithoView lithoView, int index) {
    return getLayoutOutput(lithoView.getMountItemAt(index)).getComponent();
  }

  private LithoView getLithoView(Component component) {
    LithoView lithoView = new LithoView(mContext);
    lithoView.setComponent(component);
    lithoView.measure(
        View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.UNSPECIFIED));
    lithoView.layout(0, 0, lithoView.getMeasuredWidth(), lithoView.getMeasuredHeight());
    return lithoView;
  }

  private static Component getMultipleChildrenComponent() {
    final int color = 0xFFFF0000;
    final Component testGlobalKeyChildComponent =
        new InlineLayoutSpec() {

          @Override
          @OnCreateLayout
          protected Component onCreateLayout(ComponentContext c) {

            return Column.create(c)
                .child(
                    TestViewComponent.create(c)
                        .widthDip(10)
                        .heightDip(10)
                        .key("[TestViewComponent1]"))
                .child(
                    Column.create(c)
                        .backgroundColor(color)
                        .child(CardClip.create(c).widthDip(10).heightDip(10).key("[CardClip1]")))
                .child(Text.create(c).text("Test").widthDip(10).heightDip(10).key("[Text1]"))
                .build();
          }
        };

    final Component testGlobalKeyChild =
        new InlineLayoutSpec() {

          @Override
          @OnCreateLayout
          protected Component onCreateLayout(ComponentContext c) {

            return Column.create(c)
                .child(Text.create(c).text("test").widthDip(10).heightDip(10).key("[Text2]"))
                .child(testGlobalKeyChildComponent)
                .child(
                    Column.create(c)
                        .backgroundColor(color)
                        .child(CardClip.create(c).widthDip(10).heightDip(10).key("[CardClip2]")))
                .child(
                    TestViewComponent.create(c)
                        .widthDip(10)
                        .heightDip(10)
                        .key("[TestViewComponent2]"))
                .build();
          }
        };

    return testGlobalKeyChild;
  }

  @After
  public void restoreConfiguration() {
    TempComponentsConfigurations.restoreShouldAddHostViewForRootComponent();
  }
}

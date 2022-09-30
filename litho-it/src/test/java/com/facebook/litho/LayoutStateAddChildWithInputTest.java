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
import static org.assertj.core.api.Assertions.assertThat;

import com.facebook.litho.testing.LegacyLithoViewRule;
import com.facebook.litho.testing.TestLayoutComponent;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LithoTestRunner.class)
public class LayoutStateAddChildWithInputTest {
  private ComponentContext mContext;

  @Rule public LegacyLithoViewRule mLegacyLithoViewRule = new LegacyLithoViewRule();

  @Before
  public void setup() {
    mContext = new ComponentContext(getApplicationContext());
    mContext.setRenderStateContextForTests();
  }

  @Test
  public void testNewEmptyLayout() {
    Column component =
        Column.create(mContext)
            .child(TestLayoutComponent.create(mContext))
            .child(TestLayoutComponent.create(mContext))
            .build();

    LithoNode node = LegacyLithoViewRule.getRootLayout(mLegacyLithoViewRule, component).getNode();

    assertThat(node.getChildCount()).isEqualTo(2);
    assertThat(node.getChildAt(0).getChildCount()).isEqualTo(0);
    assertThat(node.getChildAt(1).getChildCount()).isEqualTo(0);
  }
}

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

package com.facebook.litho.kotlin.widget

import com.facebook.litho.Style
import com.facebook.litho.core.height
import com.facebook.litho.core.width
import com.facebook.litho.px
import com.facebook.litho.testing.LithoViewRule
import com.facebook.litho.testing.testrunner.LithoTestRunner
import junit.framework.Assert.assertNotNull
import main.kotlin.com.facebook.litho.kotlin.widget.ExperimentalCardShadow
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.LooperMode

/** Tests for [ExperimentalCardShadow] */
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(LithoTestRunner::class)
class ExperimentalCardShadowTest {

  @Rule @JvmField val lithoViewRule = LithoViewRule()

  @Test
  fun `ExperimentalCardShadow should render`() {
    val testLithoView =
        lithoViewRule.render { ExperimentalCardShadow(style = Style.width(100.px).height(100.px)) }

    // should find an ExperimentalCardShadow in the tree
    assertNotNull(testLithoView.findComponent(ExperimentalCardShadow::class))

    // should mount an ExperimentalCardShadow
    assertThat(testLithoView.lithoView.mountItemCount).isEqualTo(1)
  }
}

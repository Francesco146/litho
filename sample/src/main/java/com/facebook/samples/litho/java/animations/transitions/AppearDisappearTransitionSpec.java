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

package com.facebook.samples.litho.java.animations.transitions;

import android.graphics.Color;
import androidx.annotation.Dimension;
import com.facebook.litho.ClickEvent;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.Row;
import com.facebook.litho.StateValue;
import com.facebook.litho.Transition;
import com.facebook.litho.annotations.LayoutSpec;
import com.facebook.litho.annotations.OnCreateInitialState;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.annotations.OnCreateTransition;
import com.facebook.litho.annotations.OnEvent;
import com.facebook.litho.annotations.OnUpdateState;
import com.facebook.litho.annotations.State;
import com.facebook.yoga.YogaEdge;

@LayoutSpec
class AppearDisappearTransitionSpec {

  private static final @Dimension(unit = Dimension.DP) float SIZE_DP = 50;
  private static final String BLUE_BOX_TRANSITION_KEY = "blue_box";

  @OnCreateInitialState
  static void onCreateInitialState(ComponentContext c, StateValue<Boolean> shouldShowItem) {
    shouldShowItem.set(false);
  }

  @OnCreateLayout
  static Component onCreateLayout(ComponentContext c, @State boolean shouldShowItem) {
    return Row.create(c)
        .paddingDip(YogaEdge.ALL, 20)
        .child(Row.create(c).heightDip(SIZE_DP).widthDip(SIZE_DP).backgroundColor(Color.RED))
        .child(
            shouldShowItem
                ? Row.create(c)
                    .heightDip(SIZE_DP)
                    .widthDip(SIZE_DP)
                    .backgroundColor(Color.BLUE)

                    // Disappearing items require a transition key and a key
                    .transitionKey(BLUE_BOX_TRANSITION_KEY)
                    .key(BLUE_BOX_TRANSITION_KEY)
                : null)
        .child(Row.create(c).heightDip(SIZE_DP).widthDip(SIZE_DP).backgroundColor(Color.RED))
        .clickHandler(AppearDisappearTransition.onClick(c))
        .build();
  }

  @OnEvent(ClickEvent.class)
  static void onClick(ComponentContext c) {
    AppearDisappearTransition.updateState(c);
  }

  @OnUpdateState
  static void updateState(StateValue<Boolean> shouldShowItem) {
    shouldShowItem.set(!shouldShowItem.get());
  }

  @OnCreateTransition
  static Transition onCreateTransition(ComponentContext c) {
    return Transition.allLayout();
  }
}

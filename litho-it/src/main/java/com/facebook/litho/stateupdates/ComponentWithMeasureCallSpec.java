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

package com.facebook.litho.stateupdates;

import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.Size;
import com.facebook.litho.SizeSpec;
import com.facebook.litho.annotations.LayoutSpec;
import com.facebook.litho.annotations.OnCreateLayout;
import com.facebook.litho.annotations.Prop;

@LayoutSpec
public class ComponentWithMeasureCallSpec {

  @OnCreateLayout
  public static Component onCreateLayout(
      ComponentContext c,
      @Prop Component component,
      @Prop boolean shouldCacheResult,
      @Prop(optional = true) int widthSpec,
      @Prop(optional = true) int heightSpec) {

    component.measure(
        c,
        widthSpec != 0 ? widthSpec : SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY),
        heightSpec != 0 ? heightSpec : SizeSpec.makeSizeSpec(200, SizeSpec.EXACTLY),
        new Size(),
        shouldCacheResult);

    return component;
  }
}

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

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.ContextWrapper

object ContextUtils {

  /**
   * @return the Activity representing this Context if the Context is backed by an Activity and the
   *   Activity has not been finished/destroyed yet. Returns null otherwise.
   */
  @JvmStatic
  fun getValidActivityForContext(context: Context?): Activity? {
    return findActivityInContext(context)?.takeUnless { it.isFinishing || it.isDestroyed }
  }

  @JvmStatic
  tailrec fun findActivityInContext(context: Context?): Activity? {
    return when (context) {
      is Activity -> context
      is ContextWrapper -> findActivityInContext(context.baseContext)
      else -> null
    }
  }

  /**
   * @return the "most base" Context of this Context, i.e. the Activity, Application, or Service
   *   backing this Context and all its ContextWrappers. In some cases, e.g. instrumentation tests
   *   or other places we don't wrap a standard Context, this root Context may instead be a raw
   *   ContextImpl.
   */
  @JvmStatic
  fun getRootContext(context: Context): Context {
    var currentContext = context
    while (currentContext is ContextWrapper &&
        currentContext !is Activity &&
        currentContext !is Application &&
        currentContext !is Service) {
      currentContext = currentContext.baseContext
    }
    return currentContext
  }
}

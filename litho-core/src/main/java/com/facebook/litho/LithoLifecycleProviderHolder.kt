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

import com.facebook.litho.LithoLifecycleProvider.LithoLifecycle

/**
 * This is a holder to form listening chains for LithoLifecycleProvider in LithoView hierarchy.
 * Consider a simple following case:
 *
 * LithoView(holder1) <- nested LithoView(holder2) <- nested LithoView(holder3)
 *
 * In this case, holder3 will listen to holder2 and holder2 will listen to holder1. And when holder1
 * is added to hold a AOSPLithoLifecycleProvider, holder2 and holder3 will also listen to it
 * automatically.
 */
internal class LithoLifecycleProviderHolder : LithoLifecycleProvider, LithoLifecycleListener {

  private val lithoLifecycleListeners: MutableSet<LithoLifecycleListener> = HashSet()

  private var internalLifecycleProvider: LithoLifecycleProvider = LithoLifecycleProviderDelegate()

  private var hasHeldLifecycleProvider: Boolean = false

  fun getHeldLifecycleProvider(): LithoLifecycleProvider? {
    return if (hasHeldLifecycleProvider) {
      internalLifecycleProvider
    } else {
      null
    }
  }

  @Synchronized
  fun setHeldLifecycleProvider(lifecycleProvider: LithoLifecycleProvider?) {
    if (internalLifecycleProvider == lifecycleProvider) {
      return
    }
    lithoLifecycleListeners.forEach { listener ->
      internalLifecycleProvider.removeListener(listener)
    }
    // If lifecycleProvider is null, we re-create LithoLifecycleProviderDelegate with default state
    internalLifecycleProvider = lifecycleProvider ?: LithoLifecycleProviderDelegate()
    hasHeldLifecycleProvider = lifecycleProvider != null

    lithoLifecycleListeners.forEach { listener -> internalLifecycleProvider.addListener(listener) }
  }

  override fun onMovedToState(state: LithoLifecycle) {
    if (hasHeldLifecycleProvider) {
      // The held LifecycleProvider should be affected by its LifecycleOwner only.
      return
    }
    when (state) {
      LithoLifecycle.HINT_VISIBLE -> {
        internalLifecycleProvider.moveToLifecycle(LithoLifecycle.HINT_VISIBLE)
        return
      }
      LithoLifecycle.HINT_INVISIBLE -> {
        internalLifecycleProvider.moveToLifecycle(LithoLifecycle.HINT_INVISIBLE)
        return
      }
      LithoLifecycle.DESTROYED -> {
        internalLifecycleProvider.moveToLifecycle(LithoLifecycle.DESTROYED)
        return
      }
      else -> throw IllegalStateException("Illegal state: $state")
    }
  }

  override fun moveToLifecycle(lithoLifecycle: LithoLifecycle) {
    internalLifecycleProvider.moveToLifecycle(lithoLifecycle)
  }

  override val lifecycleStatus: LithoLifecycle
    get() = internalLifecycleProvider.lifecycleStatus

  @Synchronized
  override fun addListener(listener: LithoLifecycleListener) {
    internalLifecycleProvider.addListener(listener)
    lithoLifecycleListeners.add(listener)
  }

  @Synchronized
  override fun removeListener(listener: LithoLifecycleListener) {
    internalLifecycleProvider.removeListener(listener)
    lithoLifecycleListeners.remove(listener)
  }
}

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

import com.facebook.litho.annotations.Hook
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * Returns a [CoroutineScope] that is be active as long as the [Component] is not released. This
 * scope will be canceled when the [ComponentTree] is released.
 */

/**
 * Return a [CoroutineScope] that is be active as long as the [Component] in which it was created is
 * present in the UI tree. The same [CoroutineScope] instance will be returned across re-renders.
 *
 * This scope will be [cancelled][CoroutineScope.cancel] when the [Component] is removed from the
 * tree or the [ComponentTree] is released. The optional [coroutineContext] parameter may not
 * contain a [Job] as this scope is managed by Litho.
 *
 * Use this scope to launch coroutines in response to callback events such as clicks or other user
 * interactions where the launched logic may take some time to finish and it's expected that the
 * launched logic should be canceled when the [Component] is removed from the UI.
 *
 * You should not use this to launch coroutines directly within [Component]'s render method. For
 * such use cases you should use other hooks such as [useCoroutine].
 *
 * This function will throw if [coroutineContext] contains a parent [Job].
 */
@Hook
fun ComponentScope.useComponentCoroutineScope(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
): CoroutineScope {
  if (coroutineContext[Job] != null) {
    // We want to make sure that Litho fully controls the lifecycle of the scope, so we don't allow
    // for passing a parent Job.
    throw IllegalArgumentException(
        "CoroutineContext passed to useComponentCoroutineScope can't contain a Job.")
  }

  val lithoTreeCoroutineContext = checkNotNull(context.lithoTree).lithoTreeScope.coroutineContext
  val state = useState { CoroutineScope(lithoTreeCoroutineContext + coroutineContext) }
  useEffect(Unit) { onCleanup { state.value.cancel() } }
  return state.value
}

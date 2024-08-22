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

package com.facebook.litho.widget.collection

import com.facebook.litho.Component
import com.facebook.litho.ComponentContext
import com.facebook.litho.ContainerDsl
import com.facebook.litho.ResourcesScope

@ContainerDsl
class LazyListScope(override val context: ComponentContext) : ResourcesScope {

  val children: LazyCollectionChildren = LazyCollectionChildren()

  /**
   * Add a child [Component] to the Lazy Collection.
   *
   * @param component The component to add
   * @param id A unique identifier for the child
   * @param isSticky Fix the child to the top of the collection if it is scrolled out of view
   * @param onNearViewport A callback that will be invoked when the child is close to or enters the
   *   visible area.
   * @param parentWidthPercent Percentage value(0-100) of the width of the parent container to fill
   * @param parentHeightPercent Percentage value(0-100) of the height of the parent container to
   *   fill
   */
  fun child(
      component: Component?,
      id: Any? = null,
      isSticky: Boolean = false,
      onNearViewport: OnNearCallback? = null,
      parentWidthPercent: Float = -1f,
      parentHeightPercent: Float = -1f,
  ) =
      children.add(
          component,
          id,
          isSticky,
          false,
          null,
          onNearViewport,
          null,
          parentWidthPercent,
          parentHeightPercent)

  /**
   * Add a child [Component] created by the provided [componentFunction] function to the Lazy
   * Collection.
   *
   * The [Component] will be created by invoking [componentFunction] when the child is first
   * rendered. The [Component] will be reused in subsequent renders unless there is a change to
   * [deps]. [deps] is an array of dependencies that should contain any props or state that are used
   * inside [componentFunction].
   *
   * @param id A unique identifier for the child
   * @param isSticky Fix the child to the top of the collection if it is scrolled out of view
   * @param onNearViewport A callback that will be invoked when the child is close to or enters the
   *   visible area.
   * @param parentWidthPercent Percentage value(0-100) of the width of the parent container to fill
   * @param parentHeightPercent Percentage value(0-100) of the height of the parent container to
   *   fill
   * @param deps An array of prop and state values used by [componentFunction] to create the
   *   [Component]. A change to one of these values will cause [componentFunction] to recreate the
   *   [Component].
   * @param componentFunction A function that returns a [Component]
   */
  fun child(
      id: Any? = null,
      isSticky: Boolean = false,
      onNearViewport: OnNearCallback? = null,
      deps: Array<Any?>,
      parentWidthPercent: Float = -1f,
      parentHeightPercent: Float = -1f,
      componentFunction: ComponentCreationScope.() -> Component?,
  ) =
      children.add(
          id,
          isSticky,
          false,
          null,
          onNearViewport,
          deps,
          parentWidthPercent,
          parentHeightPercent) {
            ComponentCreationScope(context).componentFunction()
          }

  /**
   * Add a list of children generated by applying [componentFunction] to each item in a list of
   * models.
   *
   * @param items Data models to be rendered as children
   * @param id A function to create a unique id from each data model
   * @param isSticky A function that indicates if the child should fix to the top of the collection
   *   when it is scrolled out of view
   * @param parentWidthPercent A function that sets the fill width of the child based on the parent
   *   container's size
   * @param parentHeightPercent A function that sets the fill height of the child based on the
   *   parent container's size
   * @param componentFunction A function that generates a [Component] from a data model
   */
  fun <T> children(
      items: Iterable<T>,
      id: (T) -> Any,
      isSticky: ((T) -> Boolean)? = null,
      parentWidthPercent: ((T) -> Float)? = null,
      parentHeightPercent: ((T) -> Float)? = null,
      componentFunction: ComponentCreationScope.(T) -> Component?,
  ) {
    val componentCreationScope = ComponentCreationScope(context)
    items.forEach { item ->
      children.add(
          id = id(item),
          isSticky = isSticky?.invoke(item) ?: false,
          parentWidthPercent = parentWidthPercent?.invoke(item) ?: -1f,
          parentHeightPercent = parentHeightPercent?.invoke(item) ?: -1f,
          component = componentCreationScope.componentFunction(item))
    }
  }

  /**
   * Add a list of children generated by applying [componentFunction] to each item in a list of
   * models.
   *
   * @param items Data models to be rendered as children
   * @param id A function to create a unique id from each data model
   * @param deps A function to create a list of deps from each data model
   * @param isSticky A function that indicates if the child should fix to the top of the collection
   *   when it is scrolled out of view
   * @param parentWidthPercent A function that sets the fill width of the child based on the parent
   *   container's size
   * @param parentHeightPercent A function that sets the fill height of the child based on the
   *   parent container's size
   * @param componentFunction A function that generates a [Component] from a data model
   */
  fun <T> children(
      items: Iterable<T>,
      id: (T) -> Any,
      deps: (T) -> Array<Any?>,
      isSticky: ((T) -> Boolean)? = null,
      parentWidthPercent: ((T) -> Float)? = null,
      parentHeightPercent: ((T) -> Float)? = null,
      componentFunction: ComponentCreationScope.(T) -> Component?,
  ) {
    val componentCreationScope = ComponentCreationScope(context)
    items.forEach { item ->
      children.add(
          id = id(item),
          deps = deps(item),
          isSticky = isSticky?.invoke(item) ?: false,
          parentWidthPercent = parentWidthPercent?.invoke(item) ?: -1f,
          parentHeightPercent = parentHeightPercent?.invoke(item) ?: -1f,
          componentFunction = { componentCreationScope.componentFunction(item) })
    }
  }

  /**
   * Add the [item] by applying [componentFunction] to it. Similar to [children] but for a single
   * element
   *
   * @param item Data model to be rendered as child
   * @param id A function to create a unique id from data model
   * @param deps A function to create a list of deps from data model
   * @param isSticky A function that indicates if the child should fix to the top of the collection
   *   when it is scrolled out of view
   * @param parentWidthPercent A function that sets the fill width of the child based on the parent
   *   container's size
   * @param parentHeightPercent A function that sets the fill height of the child based on the
   *   parent container's size
   * @param componentFunction A function that generates a [Component] from a data model
   */
  fun <T> child(
      item: T,
      id: (T) -> Any,
      deps: (T) -> Array<Any?>,
      isSticky: ((T) -> Boolean)? = null,
      parentWidthPercent: ((T) -> Float)? = null,
      parentHeightPercent: ((T) -> Float)? = null,
      componentFunction: ComponentCreationScope.(T) -> Component?,
  ) {
    val componentCreationScope = ComponentCreationScope(context)
    children.add(
        id = id(item),
        deps = deps(item),
        isSticky = isSticky?.invoke(item) ?: false,
        parentWidthPercent = parentWidthPercent?.invoke(item) ?: -1f,
        parentHeightPercent = parentHeightPercent?.invoke(item) ?: -1f,
        componentFunction = { componentCreationScope.componentFunction(item) })
  }
}

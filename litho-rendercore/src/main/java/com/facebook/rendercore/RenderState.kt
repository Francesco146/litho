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

package com.facebook.rendercore

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.facebook.infer.annotation.ThreadConfined
import com.facebook.rendercore.StateUpdateReceiver.StateUpdate
import com.facebook.rendercore.extensions.RenderCoreExtension
import com.facebook.rendercore.utils.MeasureSpecUtils
import com.facebook.rendercore.utils.ThreadUtils
import java.util.Objects
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.concurrent.ThreadSafe

/** todo: javadocs * */
class RenderState<State, RenderContext, StateUpdateType : StateUpdate<*>>(
    private val context: Context,
    private val delegate: Delegate<State>,
    private val renderContext: RenderContext?,
    val extensions: Array<RenderCoreExtension<*, *>>?
) : StateUpdateReceiver<StateUpdateType> {

  /**
   * Represents a function capable of creating a tree. The tree is lazy so that the creation can be
   * done inline with the layout pass.
   *
   * @param <State> Represents the State that this tree would like to commit when the tree itself is
   *   committed
   */
  @ThreadSafe
  fun interface ResolveFunc<State, RenderContext, StateUpdateType : StateUpdate<*>> {
    /**
     * Resolves the tree represented by this ResolveFunc. Results for resolve might be cached. The
     * assumption is that multiple resolve calls on a ResolveFunc would return equivalent trees.
     *
     * @param resolveContext
     * @param committedTree
     * @param committedState
     * @param stateUpdatesToApply
     */
    fun resolve(
        resolveContext: ResolveContext<RenderContext, StateUpdateType>,
        committedTree: Node<RenderContext>?,
        committedState: State?,
        stateUpdatesToApply: List<@JvmSuppressWildcards StateUpdateType>
    ): ResolveResult<Node<RenderContext>, State>
  }

  interface Delegate<State> {
    fun commit(
        layoutVersion: Int,
        current: RenderTree?,
        next: RenderTree,
        currentState: State?,
        nextState: State?
    )

    fun commitToUI(tree: RenderTree?, state: State?)
  }

  fun interface HostListener {
    fun onUIRenderTreeUpdated(newRenderTree: RenderTree?)
  }

  private val uIHandler: RenderStateHandler = RenderStateHandler(Looper.getMainLooper())
  val id: Int = ID_GENERATOR.incrementAndGet()

  @ThreadConfined(ThreadConfined.UI) private var hostListener: HostListener? = null

  @get:ThreadConfined(ThreadConfined.UI)
  var uiRenderTree: RenderTree? = null
    private set

  private var latestResolveFunc: ResolveFunc<State, RenderContext, StateUpdateType>? = null
  private var resolveFuture: ResolveFuture<State, RenderContext, StateUpdateType>? = null
  private var layoutFuture: LayoutFuture<State, RenderContext>? = null
  private var committedResolvedTree: Node<RenderContext>? = null
  private var committedState: State? = null
  private val pendingStateUpdates: MutableList<StateUpdateType> = ArrayList()
  private var committedRenderResult: RenderResult<State, RenderContext>? = null
  private var resolveVersionCounter = 0
  private var layoutVersionCounter = 0
  private var committedResolveVersion = UNSET
  private var committedLayoutVersion = UNSET
  private var widthSpec = UNSET
  private var heightSpec = UNSET

  @ThreadConfined(ThreadConfined.ANY)
  fun setTree(resolveFunc: ResolveFunc<State, RenderContext, StateUpdateType>?) {
    setTree(resolveFunc, null)
  }

  @ThreadConfined(ThreadConfined.ANY)
  fun setTree(
      resolveFunc: ResolveFunc<State, RenderContext, StateUpdateType>?,
      executor: Executor?
  ) {
    requestResolve(resolveFunc, executor)
  }

  @ThreadConfined(ThreadConfined.ANY)
  override fun enqueueStateUpdate(stateUpdate: StateUpdateType) {
    synchronized(this) {
      pendingStateUpdates.add(stateUpdate)
      if (latestResolveFunc == null) {
        return
      }
    }
    if (!uIHandler.hasMessages(UPDATE_STATE_MESSAGE)) {
      uIHandler.sendEmptyMessage(UPDATE_STATE_MESSAGE)
    }
  }

  private fun flushStateUpdates() {
    requestResolve(null, null)
  }

  private fun requestResolve(
      resolveFunc: ResolveFunc<State, RenderContext, StateUpdateType>?,
      executor: Executor?
  ) {
    val future: ResolveFuture<State, RenderContext, StateUpdateType>
    synchronized(this) {

      // Resolve was triggered by State Update, but all pendingStateUpdates are already applied.
      if (resolveFunc == null && pendingStateUpdates.isEmpty()) {
        return
      }
      if (resolveFunc != null) {
        latestResolveFunc = resolveFunc
      }
      future =
          ResolveFuture(
              requireNotNull(latestResolveFunc),
              ResolveContext(renderContext, this),
              committedResolvedTree,
              committedState,
              if (pendingStateUpdates.isEmpty()) emptyList() else ArrayList(pendingStateUpdates),
              resolveVersionCounter++)
      resolveFuture = future
    }
    if (executor != null) {
      executor.execute(Runnable { resolveTreeAndMaybeCommit(future) })
    } else {
      resolveTreeAndMaybeCommit(future)
    }
  }

  private fun resolveTreeAndMaybeCommit(
      future: ResolveFuture<State, RenderContext, StateUpdateType>
  ) {
    val result = future.runAndGet()
    if (maybeCommitResolveResult(result, future)) {
      layoutAndMaybeCommitInternal(null)
    }
  }

  @Synchronized
  private fun maybeCommitResolveResult(
      result: ResolveResult<Node<RenderContext>, State>,
      future: ResolveFuture<State, RenderContext, StateUpdateType>
  ): Boolean {
    // We don't want to compute, layout, or reduce trees while holding a lock. However this means
    // that another thread could compute a layout and commit it before we get to this point. To
    // handle this, we make sure that the committed resolve version is only ever increased, meaning
    // we only go "forward in time" and will eventually get to the latest layout.
    var didCommit = false
    if (future.version > committedResolveVersion) {
      committedResolveVersion = future.version
      committedResolvedTree = result.resolvedNode
      committedState = result.resolvedState
      pendingStateUpdates.removeAll(future.stateUpdatesToApply)
      didCommit = true
    }
    if (resolveFuture == future) {
      resolveFuture = null
    }
    return didCommit
  }

  private fun layoutAndMaybeCommitInternal(measureOutput: IntArray?) {
    val layoutFuture: LayoutFuture<State, RenderContext>
    val previousRenderResult: RenderResult<State, RenderContext>?
    synchronized(this) {
      if (widthSpec == UNSET || heightSpec == UNSET) {
        return
      }
      val commitedTree =
          requireNotNull(committedResolvedTree) {
            "Tried executing the layout step before resolving a tree"
          }
      val layout = this.layoutFuture
      if (layout == null ||
          layout.tree != commitedTree ||
          !hasSameSpecs(layout, widthSpec, heightSpec)) {
        this.layoutFuture =
            LayoutFuture(
                context,
                renderContext,
                commitedTree,
                committedState,
                layoutVersionCounter++,
                committedRenderResult,
                extensions,
                widthSpec,
                heightSpec)
      }
      layoutFuture = requireNotNull(this.layoutFuture)
      previousRenderResult = committedRenderResult
    }
    val renderResult = layoutFuture.runAndGet()
    var committedNewLayout = false
    synchronized(this) {
      if (hasSameSpecs(layoutFuture, widthSpec, heightSpec) &&
          layoutFuture.version > committedLayoutVersion &&
          committedRenderResult != renderResult) {
        committedLayoutVersion = layoutFuture.version
        committedNewLayout = true
        committedRenderResult = renderResult
      }
      if (this.layoutFuture == layoutFuture) {
        this.layoutFuture = null
      }
    }
    if (measureOutput != null) {
      measureOutput[0] = renderResult.renderTree.width
      measureOutput[1] = renderResult.renderTree.height
    }
    if (committedNewLayout) {
      delegate.commit(
          layoutFuture.version,
          previousRenderResult?.renderTree,
          renderResult.renderTree,
          previousRenderResult?.state,
          renderResult.state)
      schedulePromoteCommittedTreeToUI()
    }
  }

  @ThreadConfined(ThreadConfined.UI)
  fun measure(widthSpec: Int, heightSpec: Int, measureOutput: IntArray?) {
    val futureToResolveBeforeMeasuring: ResolveFuture<State, RenderContext, StateUpdateType>?
    synchronized(this) {
      if (this.widthSpec != widthSpec || this.heightSpec != heightSpec) {
        this.widthSpec = widthSpec
        this.heightSpec = heightSpec
      }

      val renderTree = uiRenderTree
      // The current UI tree is compatible. We might just return those values
      if (renderTree != null && hasCompatibleSize(renderTree, widthSpec, heightSpec)) {
        if (measureOutput != null) {
          measureOutput[0] = renderTree.width
          measureOutput[1] = renderTree.height
        }
        return
      }
      val renderResult = committedRenderResult
      if (renderResult != null &&
          hasCompatibleSize(renderResult.renderTree, widthSpec, heightSpec)) {
        maybePromoteCommittedTreeToUI()
        if (measureOutput != null) {
          // We have a tree that we previously resolved with these contraints. For measuring we can
          // just return it
          measureOutput[0] = renderResult.renderTree.width
          measureOutput[1] = renderResult.renderTree.height
        }
        return
      }

      // We don't have a valid resolve function yet. Let's just bail until then.
      if (latestResolveFunc == null) {
        if (measureOutput != null) {
          measureOutput[0] = 0
          measureOutput[1] = 0
        }
        return
      }

      // If we do have a resolve function we expect to have either a committed resolved tree or a
      // future. If we have neither something has gone wrong with the setTree call.
      futureToResolveBeforeMeasuring =
          if (committedResolvedTree != null) {
            null
          } else {
            Objects.requireNonNull(resolveFuture)
          }
    }
    if (futureToResolveBeforeMeasuring != null) {
      val result = futureToResolveBeforeMeasuring.runAndGet()
      maybeCommitResolveResult(result, futureToResolveBeforeMeasuring)
    }
    layoutAndMaybeCommitInternal(measureOutput)
  }

  @ThreadConfined(ThreadConfined.UI)
  fun attach(hostListener: HostListener) {
    if (this.hostListener != null && this.hostListener != hostListener) {
      throw RuntimeException("Must detach from previous host listener first")
    }
    this.hostListener = hostListener
  }

  @ThreadConfined(ThreadConfined.UI)
  fun detach() {
    hostListener = null
  }

  private fun schedulePromoteCommittedTreeToUI() {
    if (ThreadUtils.isMainThread()) {
      maybePromoteCommittedTreeToUI()
    } else {
      if (!uIHandler.hasMessages(PROMOTION_MESSAGE)) {
        uIHandler.sendEmptyMessage(PROMOTION_MESSAGE)
      }
    }
  }

  @ThreadConfined(ThreadConfined.UI)
  private fun maybePromoteCommittedTreeToUI() {
    synchronized(this) {
      delegate.commitToUI(committedRenderResult?.renderTree, committedRenderResult?.state)
      if (uiRenderTree == committedRenderResult?.renderTree) {
        return
      }
      uiRenderTree = committedRenderResult?.renderTree
    }
    hostListener?.onUIRenderTreeUpdated(uiRenderTree)
  }

  private inner class RenderStateHandler(looper: Looper) : Handler(looper) {
    override fun handleMessage(msg: Message) {
      when (msg.what) {
        PROMOTION_MESSAGE -> maybePromoteCommittedTreeToUI()
        UPDATE_STATE_MESSAGE -> flushStateUpdates()
        else -> throw RuntimeException("Unknown message: " + msg.what)
      }
    }
  }

  companion object {
    @JvmField var NO_ID: Int = -1
    private const val UNSET: Int = -1
    private const val PROMOTION_MESSAGE: Int = 99
    private const val UPDATE_STATE_MESSAGE: Int = 100
    private val ID_GENERATOR: AtomicInteger = AtomicInteger(0)

    @JvmStatic
    private fun hasCompatibleSize(tree: RenderTree, widthSpec: Int, heightSpec: Int): Boolean {
      return (MeasureSpecUtils.isMeasureSpecCompatible(tree.widthSpec, widthSpec, tree.width) &&
          MeasureSpecUtils.isMeasureSpecCompatible(tree.heightSpec, heightSpec, tree.height))
    }

    @JvmStatic
    private fun <State, RenderContext> hasSameSpecs(
        future: LayoutFuture<State, RenderContext>,
        widthSpec: Int,
        heightSpec: Int
    ): Boolean {
      return (MeasureSpecUtils.areMeasureSpecsEquivalent(future.widthSpec, widthSpec) &&
          MeasureSpecUtils.areMeasureSpecsEquivalent(future.heightSpec, heightSpec))
    }
  }
}

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

package com.facebook.rendercore;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.facebook.rendercore.RenderState.ResolveFunc;
import com.facebook.rendercore.extensions.RenderCoreExtension;
import com.facebook.rendercore.utils.MeasureSpecUtils;

/**
 * Result from resolving a {@link ResolveFunc}. A {@link RenderResult} from a previous computation
 * will make the next computation of a new {@link ResolveFunc} more efficient with internal caching.
 */
public class RenderResult<State> {
  private final RenderTree mRenderTree;
  private final ResolveFunc mResolveFunc;
  private final Node mNodeTree;
  private final LayoutCache.CachedData mLayoutCacheData;
  @Nullable private final State mState;

  public static <State, RenderContext> RenderResult<State> resolve(
      final Context context,
      final ResolveFunc<State> resolveFunc,
      final @Nullable RenderContext renderContext,
      final @Nullable RenderCoreExtension<?, ?>[] extensions,
      final @Nullable RenderResult<State> previousResult,
      final int layoutVersion,
      final int widthSpec,
      final int heightSpec) {
    final Node previousTree = previousResult != null ? previousResult.getNodeTree() : null;
    final State previousState = previousResult != null ? previousResult.getState() : null;

    RenderCoreSystrace.beginSection("RC Create Tree");
    final Pair<Node, State> result;

    if (previousResult != null && resolveFunc == previousResult.getResolveFunc()) {
      result = new Pair<>(previousTree, previousState);
    } else {
      result = resolveFunc.resolve();
    }
    final RenderResult renderResult;

    if (shouldReuseResult(result.first, widthSpec, heightSpec, previousResult)) {
      renderResult =
          new RenderResult<>(
              previousResult.getRenderTree(),
              resolveFunc,
              result.first,
              previousResult.getLayoutCacheData(),
              result.second);
    } else {
      RenderCoreSystrace.beginSection("RC Layout");

      final LayoutCache layoutCache =
          buildCache(previousResult == null ? null : previousResult.getLayoutCacheData());

      final LayoutContext<RenderContext> layoutContext =
          new LayoutContext<>(context, renderContext, layoutVersion, layoutCache, extensions);

      final Node.LayoutResult layoutResult =
          result.first.calculateLayout(layoutContext, widthSpec, heightSpec);
      RenderCoreSystrace.endSection();

      RenderCoreSystrace.beginSection("RC Reduce");
      renderResult =
          create(
              layoutContext,
              result.first,
              layoutResult,
              resolveFunc,
              widthSpec,
              heightSpec,
              result.second);
      RenderCoreSystrace.endSection();
      layoutContext.clearCache();
    }

    RenderCoreSystrace.endSection();

    return renderResult;
  }

  public static <State> RenderResult<State> create(
      final LayoutContext c,
      final Node node,
      final Node.LayoutResult layoutResult,
      final ResolveFunc<State> resolveFunc,
      final int widthSpec,
      final int heightSpec,
      final @Nullable State state) {
    return new RenderResult<>(
        Reducer.getReducedTree(
            c.getAndroidContext(), layoutResult, widthSpec, heightSpec, c.getExtensions()),
        resolveFunc,
        node,
        c.getLayoutCache().getWriteCacheData(),
        state);
  }

  public static <State> boolean shouldReuseResult(
      final Node node,
      final int widthSpec,
      final int heightSpec,
      @Nullable final RenderResult<State> previousResult) {
    if (previousResult == null) {
      return false;
    }

    final RenderTree prevRenderTree = previousResult.getRenderTree();
    return previousResult.getRenderTree() != null
        && node == previousResult.getNodeTree()
        && MeasureSpecUtils.isMeasureSpecCompatible(
            prevRenderTree.getWidthSpec(), widthSpec, prevRenderTree.getWidth())
        && MeasureSpecUtils.isMeasureSpecCompatible(
            prevRenderTree.getHeightSpec(), heightSpec, prevRenderTree.getHeight());
  }

  private RenderResult(
      RenderTree renderTree,
      ResolveFunc resolveFunc,
      Node nodeTree,
      LayoutCache.CachedData layoutCacheData,
      @Nullable State state) {
    mRenderTree = renderTree;
    mResolveFunc = resolveFunc;
    mNodeTree = nodeTree;
    mLayoutCacheData = layoutCacheData;
    mState = state;
  }

  public RenderTree getRenderTree() {
    return mRenderTree;
  }

  ResolveFunc getResolveFunc() {
    return mResolveFunc;
  }

  Node getNodeTree() {
    return mNodeTree;
  }

  LayoutCache.CachedData getLayoutCacheData() {
    return mLayoutCacheData;
  }

  @Nullable
  public State getState() {
    return mState;
  }

  @VisibleForTesting
  public static LayoutCache buildCache(@Nullable LayoutCache.CachedData previousCache) {
    return previousCache != null ? new LayoutCache(previousCache) : new LayoutCache(null);
  }

  public static ResolveFunc<Void> wrapInResolveFunc(Node node) {
    return wrapInResolveFunc(node, (Void) null);
  }

  public static <T> ResolveFunc<T> wrapInResolveFunc(final Node node, final @Nullable T state) {
    return new ResolveFunc<T>() {
      @Override
      public Pair<Node, T> resolve() {
        return new Pair<>(node, state);
      }
    };
  }
}

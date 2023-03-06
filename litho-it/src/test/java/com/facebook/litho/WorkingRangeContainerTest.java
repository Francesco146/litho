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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.facebook.litho.WorkingRangeContainer.RangeTuple;
import com.facebook.litho.testing.testrunner.LithoTestRunner;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(LithoTestRunner.class)
public class WorkingRangeContainerTest {
  private static final String NAME = "workingRangeName";

  private WorkingRangeContainer mWorkingRangeContainer;
  private WorkingRange mWorkingRange;
  private SpecGeneratedComponent mComponent;
  private SpecGeneratedComponent mComponent2;
  private ScopedComponentInfo mScopedComponentInfo;
  private ScopedComponentInfo mScopedComponentInfo2;
  private ComponentContext mComponentContext;
  private ComponentContext mComponentContext2;
  private LayoutStateContext mLayoutStateContext;

  @Before
  public void setup() {
    mWorkingRangeContainer = new WorkingRangeContainer();

    mComponentContext = mock(ComponentContext.class);
    mComponentContext2 = mock(ComponentContext.class);
    mLayoutStateContext = mock(LayoutStateContext.class);

    mWorkingRange = new TestWorkingRange();
    mComponent = mock(SpecGeneratedComponent.class);
    mComponent2 = mock(SpecGeneratedComponent.class);

    mScopedComponentInfo = mock(ScopedComponentInfo.class);
    mScopedComponentInfo2 = mock(ScopedComponentInfo.class);
    when(mScopedComponentInfo.getContext()).thenReturn(mComponentContext);
    when(mScopedComponentInfo2.getContext()).thenReturn(mComponentContext2);

    when(mScopedComponentInfo.getComponent()).thenReturn(mComponent);
    when(mScopedComponentInfo2.getComponent()).thenReturn(mComponent2);

    when(mComponentContext.getGlobalKey()).thenReturn("component");
    when(mComponentContext2.getGlobalKey()).thenReturn("component2");
  }

  @Test
  public void testRegisterWorkingRange() {
    mWorkingRangeContainer.registerWorkingRange(NAME, mWorkingRange, mScopedComponentInfo, null);

    final Map<String, RangeTuple> workingRanges =
        mWorkingRangeContainer.getWorkingRangesForTestOnly();
    assertThat(workingRanges.size()).isEqualTo(1);

    final String key = workingRanges.keySet().iterator().next();
    assertThat(key).isEqualTo(NAME + "_" + mWorkingRange.hashCode());

    final RangeTuple rangeTuple = workingRanges.get(key);
    assertThat(rangeTuple.workingRange).isEqualTo(mWorkingRange);
    assertThat(rangeTuple.scopedComponentInfos.size()).isEqualTo(1);
    assertThat(rangeTuple.scopedComponentInfos.get(0)).isEqualTo(mScopedComponentInfo);
  }

  @Test
  public void testIsEnteredRange() {
    RangeTuple rangeTuple = new RangeTuple(NAME, mWorkingRange, mScopedComponentInfo, null);
    WorkingRange workingRange = rangeTuple.workingRange;

    assertThat(WorkingRangeContainer.isEnteringRange(workingRange, 0, 0, 1, 0, 1)).isEqualTo(true);
    assertThat(WorkingRangeContainer.isEnteringRange(workingRange, 0, 1, 2, 1, 2)).isEqualTo(false);
  }

  @Test
  public void testIsExitedRange() {
    RangeTuple rangeTuple = new RangeTuple(NAME, mWorkingRange, mScopedComponentInfo, null);
    WorkingRange workingRange = rangeTuple.workingRange;

    assertThat(WorkingRangeContainer.isExitingRange(workingRange, 0, 0, 1, 0, 1)).isEqualTo(false);
    assertThat(WorkingRangeContainer.isExitingRange(workingRange, 0, 1, 2, 1, 2)).isEqualTo(true);
  }

  @Test
  public void testDispatchOnExitedRangeIfNeeded() {
    TestWorkingRange workingRange = new TestWorkingRange();
    mWorkingRangeContainer.registerWorkingRange(NAME, workingRange, mScopedComponentInfo, null);

    TestWorkingRange workingRange2 = new TestWorkingRange();
    mWorkingRangeContainer.registerWorkingRange(NAME, workingRange2, mScopedComponentInfo2, null);

    final WorkingRangeStatusHandler statusHandler = new WorkingRangeStatusHandler();
    statusHandler.setStatus(
        NAME, mComponent, "component", WorkingRangeStatusHandler.STATUS_IN_RANGE);
    doNothing()
        .when(mComponent)
        .dispatchOnExitedRange(isA(ComponentContext.class), isA(String.class), isNull());

    statusHandler.setStatus(
        NAME, mComponent2, "component2", WorkingRangeStatusHandler.STATUS_OUT_OF_RANGE);
    doNothing()
        .when(mComponent2)
        .dispatchOnExitedRange(isA(ComponentContext.class), isA(String.class), isNull());

    mWorkingRangeContainer.dispatchOnExitedRangeIfNeeded(statusHandler);

    verify(mComponent, times(1)).dispatchOnExitedRange(mComponentContext, NAME, null);
    verify(mComponent2, times(0)).dispatchOnExitedRange(mComponentContext, NAME, null);
  }

  private static class TestWorkingRange implements WorkingRange {

    boolean isExitRangeCalled = false;

    @Override
    public boolean shouldEnterRange(
        int position,
        int firstVisibleIndex,
        int lastVisibleIndex,
        int firstFullyVisibleIndex,
        int lastFullyVisibleIndex) {
      return isInRange(position, firstVisibleIndex, lastVisibleIndex);
    }

    @Override
    public boolean shouldExitRange(
        int position,
        int firstVisibleIndex,
        int lastVisibleIndex,
        int firstFullyVisibleIndex,
        int lastFullyVisibleIndex) {
      isExitRangeCalled = true;
      return !isInRange(position, firstVisibleIndex, lastVisibleIndex);
    }

    private static boolean isInRange(int position, int firstVisibleIndex, int lastVisibleIndex) {
      return (position >= firstVisibleIndex && position <= lastVisibleIndex);
    }
  }
}

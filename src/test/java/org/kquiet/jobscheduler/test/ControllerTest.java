/*
 * Copyright 2019 P. Kimberly Chang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kquiet.jobscheduler.test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.kquiet.jobscheduler.JobBase;
import org.kquiet.jobscheduler.JobCtrl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller unit test.
 * 
 * @author Kimberly
 */
public class ControllerTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ControllerTest.class);

  @BeforeAll
  public static void setUpClass() {
  }

  @AfterAll
  public static void tearDownClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @AfterEach
  public void tearDown() {
  }

  @Test
  public void customJobTest() {
    CountDownLatch latch = new CountDownLatch(2);
    List<String> parameterValueList = Collections.synchronizedList(new ArrayList<>());
    JobCtrl controller = new JobCtrl();
    TestJobBase job1 = new TestJobBase("TestJob1", controller, latch, parameterValueList);
    TestJobBase job2 = new TestJobBase("TestJob2", controller, latch, parameterValueList);
    controller.start(Arrays.asList(job2, job1));

    boolean temp = false;
    try {
      temp = latch.await(600, TimeUnit.SECONDS);
    } catch (Exception ex) {
      System.err.println(ex.toString());
    }
    controller.stop();
    boolean jobDone = temp;
    assertAll(
        () -> assertTrue(jobDone),
        () -> assertEquals("JobSchedulerTest", job1.getInstanceName(),
            "Wrong instance name on TestJob1"),
        () -> assertEquals("JobSchedulerTest", job2.getInstanceName(),
            "Wrong instance name on TestJob2"),
        () -> assertEquals(2, parameterValueList.size(),
            "Wrong parameter value size"),
        () -> assertEquals("paramValue1,paramValue2",
            String.join(",", parameterValueList.stream().sorted().collect(Collectors.toList())),
            "Wrong parameter value sequence")
    );
  }

  static class TestJobBase extends JobBase {
    private CountDownLatch latch = null;
    private List<String> parameterValueList = null;

    public TestJobBase(String jobName, JobCtrl ctrl) {
      super(jobName, ctrl);
    }

    public TestJobBase(String jobName, JobCtrl ctrl, CountDownLatch latch,
        List<String> parameterValueList) {
      super(jobName, ctrl);
      this.latch = latch;
      this.parameterValueList = parameterValueList;
    }

    @Override
    protected boolean checkBizToDo() {
      return true;
    }

    @Override
    protected void doJob() {
      LOGGER.info("[{}] starts", this.getJobName());
      try {
        Thread.sleep(2000);
      } catch (Exception ex) {
        fail("sleep interrupted");
      }
      parameterValueList.add(getParameter("testParameter"));
      LOGGER.info("[{}] done with testParameter={}", this.getJobName(),
          this.getParameter("testParameter"));
      if (latch != null) {
        latch.countDown();
      }
    }
  }
}

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

package org.kquiet.jobscheduler;

import java.time.LocalDateTime;
import java.util.Map;

import org.aeonbits.owner.ConfigCache;
import org.kquiet.browser.ActionComposer;
import org.kquiet.concurrent.PausableThreadPoolExecutor;
import org.kquiet.jobscheduler.JobCtrl.InteractionType;
import org.kquiet.jobscheduler.JobCtrl.PauseTarget;
import org.kquiet.jobscheduler.SystemConfig.JobConfig;
import org.kquiet.jobscheduler.util.TimeUtility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the base of all jobs. Each job class should extend this class
 * and provide their own implementation.
 *
 * @author Kimberly
 */
public abstract class JobBase {
  private static final Logger LOGGER = LoggerFactory.getLogger(JobBase.class);

  private final SystemConfig systemConfig = ConfigCache.getOrCreate(SystemConfig.class);
  private final JobCtrl controller;
  private final String jobName;
  private final PausableThreadPoolExecutor eventExecutor;
  private volatile boolean isPaused = false;

  /**
   * Create a new job.
   * 
   * @param jobName name of job
   * @param ctrl associated job controller
   */
  public JobBase(String jobName, JobCtrl ctrl) {
    this.jobName = jobName;
    this.controller = ctrl;
    this.eventExecutor = new PausableThreadPoolExecutor("EventExecutor-" + jobName, 1, 1);
  }

  protected abstract boolean checkBizToDo();

  protected abstract void doJob();

  protected boolean pause() {
    if (isPaused) {
      return false;
    } else {
      isPaused = true;
      return true;
    }
  }

  protected boolean isPaused() {
    return isPaused;
  }

  protected boolean resume() {
    if (!isPaused) {
      return false;
    } else {
      isPaused = false;
      return true;
    }
  }

  protected String ping() {
    return "";
  }

  /**
   * Calculate the date and time of next fire according to job configuration.
   * 
   * @param from the {@link LocalDateTime} to calculate from
   * @return the {@link LocalDateTime} of next fire 
   */
  public final LocalDateTime calculateNextFireDateTime(LocalDateTime from) {
    return TimeUtility.calculateNextFireDateTime(getTimerConfig().start().get(),
        getTimerConfig().end().get(), getTimerConfig().dailyStart().get(),
        getTimerConfig().dailyEnd().get(), from);
  }

  /**
   * Raise an event from this job. Associated job controller will forward this event to the other
   * jobs.
   * 
   * @param event the event to raise
   */
  public final void raiseEvent(Object event) {
    if (event == null) {
      return;
    }
    controller.forwardEvent(this, event);
  }

  /**
   * Receive an event from the other jobs.
   * 
   * @param event the event to receive
   */
  public final void receiveEvent(Object event) {
    if (event == null) {
      return;
    }

    eventExecutor.submit(() -> {
      try {
        onEvent(event);
      } catch (Exception ex) {
        LOGGER.error("[{}] process event error:{}", "EventExecutor-" + jobName, event, ex);
      }
    });
  }

  protected void onEvent(Object event) {
    if (event == null) {
      return;
    }

    LOGGER.info("event bypassed:{}", event);
  }

  public final String getInstanceName() {
    return systemConfig.instanceName();
  }

  protected final JobConfig getTimerConfig() {
    return systemConfig.jobs().get(getJobName());
  }

  /**
   * Get parameter value.
   * 
   * @param name name of parameter
   * @return parameter value of specified parameter name
   */
  public final String getParameter(String name) {
    Map<String, String> parameter = getTimerConfig().parameter();
    if (parameter != null) {
      return parameter.get(name);
    } else {
      return null;
    }
  }
  
  /**
   * Restart internal browser of controlling job controller.
   */
  public final void restartInternalBrowser() {    
    if (controller != null) {
      controller.restartBrowserTaskManager();
    }
  }
  
  
  /**
   * Pause the execution of internal browser. All executing browser tasks will
   * remain running until they complete.
   */
  public final void pauseInternalBrowser() {
    if (controller != null) {
      controller.pause(PauseTarget.Browser);
    }
  }
  
  /**
   * Resume the execution of internal browser.
   */
  public final void resumeInternalBrowser() {
    if (controller != null) {
      controller.resume(PauseTarget.Browser);
    }
  }
  
  /**
   * Register a browser task to be executed in internal browser.
   * 
   * @param task browser task
   * @return true if the browser task is successfully accepted, otherwise false
   */
  public boolean registerInternalBrowserTask(ActionComposer task) {
    if (controller != null) {
      return controller.acceptBrowserTask(task);
    } else {
      throw new RuntimeException("No assocaited job controller available");
    }
  }
  
  /**
   * Await external interaction from job controller.
   */
  public void awaitInteraction() {
    if (controller != null) {
      controller.awaitInteraction();
    } else {
      throw new RuntimeException("No assocaited job controller available");
    }
  }
  
  /**
   * Get the latest interaction from job controller.
   * 
   * @return the latest interaction
   */
  public InteractionType getLatestInteraction() {
    if (controller != null) {
      return controller.getLatestInteraction();
    } else {
      throw new RuntimeException("No assocaited job controller available");
    }
  }

  /**
   * Get the name of this job.
   * @return job name
   */
  public final String getJobName() {
    return jobName;
  }
}

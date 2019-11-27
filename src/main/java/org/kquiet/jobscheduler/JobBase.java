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
import java.util.UUID;

import org.aeonbits.owner.ConfigCache;
import org.kquiet.browser.ActionComposer;
import org.kquiet.concurrent.PausableThreadPoolExecutor;
import org.kquiet.jobscheduler.JobController.InteractionType;
import org.kquiet.jobscheduler.JobController.PauseTarget;
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
public abstract class JobBase implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(JobBase.class);

  private final SystemConfig systemConfig = ConfigCache.getOrCreate(SystemConfig.class);
  private volatile JobController controller = null;
  private final String jobName;
  private final PausableThreadPoolExecutor eventExecutor;
  private volatile boolean isPaused = false;

  /**
   * Create a new job with randomly generated name.
   */
  public JobBase() {
    this(UUID.randomUUID().toString());
  }
  
  /**
   * Create a new job with specified name.
   * 
   * @param jobName the name of job
   */
  public JobBase(String jobName) {
    this.jobName = jobName;    
    this.eventExecutor = new PausableThreadPoolExecutor("EventExecutor-" + jobName, 1, 1);
  }
  
  public final JobBase setJobController(JobController ctrl) {
    this.controller = ctrl;
    return this;
  }

  /**
   * The processing logic of job goes here.
   */
  public abstract void run();

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

  public final JobConfig getTimerConfig() {
    return systemConfig.jobs().get(getJobName());
  }

  /**
   * Get parameter value.
   * 
   * @param name the name of parameter
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
  protected final void restartInternalBrowser() {    
    if (controller != null) {
      controller.restartBrowserTaskManager();
    }
  }
  
  
  /**
   * Pause the execution of internal browser. All executing browser tasks will
   * remain running until they complete.
   */
  protected final void pauseInternalBrowser() {
    if (controller != null) {
      controller.pause(PauseTarget.Browser);
    }
  }
  
  /**
   * Resume the execution of internal browser.
   */
  protected final void resumeInternalBrowser() {
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
  protected final boolean registerInternalBrowserTask(ActionComposer task) {
    if (controller != null) {
      return controller.acceptBrowserTask(task);
    } else {
      throw new RuntimeException("No assocaited job controller available");
    }
  }
  
  /**
   * Await external interaction from job controller.
   */
  protected final void awaitInteraction() {
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
  protected final InteractionType getLatestInteraction() {
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

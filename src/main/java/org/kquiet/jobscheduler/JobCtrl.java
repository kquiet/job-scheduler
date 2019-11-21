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

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aeonbits.owner.ConfigCache;

import org.kquiet.browser.ActionComposer;
import org.kquiet.browser.ActionRunner;
import org.kquiet.browser.BasicActionRunner;
import org.kquiet.browser.BrowserType;
import org.kquiet.concurrent.PausableScheduledThreadPoolExecutor;
import org.kquiet.jobscheduler.SystemConfig.JobConfig;
import org.kquiet.jobscheduler.util.TimeUtility;

import org.openqa.selenium.PageLoadStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class controls whole execution process of all associated jobs.
 * 
 * @author Kimberly
 */
public class JobCtrl {
  private static final Logger LOGGER = LoggerFactory.getLogger(JobCtrl.class);

  private final SystemConfig configInfo = ConfigCache.getOrCreate(SystemConfig.class);
  private volatile ActionRunner actionRunner;
  private final Phaser interactionPhaser;
  private volatile boolean positiveInteractionFlag;
  private Runnable preInteractionFunc;
  private Runnable postInteractionFunc;
  private final PausableScheduledThreadPoolExecutor timerExecutor;
  private volatile boolean isPaused = false;
  private volatile boolean resumableAutomatically = true;
  private final List<JobBase> scheduleJobList = new ArrayList<>();
  private final Map<String, ScheduledFuture<?>> scheduledTask = new LinkedHashMap<>();
  private volatile boolean scheduled = false;

  private Runnable pauseDelegate = null;
  private Runnable resumeDelegate = null;
  private Consumer<String> executingJobDescriptionConsumer = null; 

  /**
   * Create a new job controller.
   */
  public JobCtrl() {
    actionRunner = createNewActionRunner();
    int parallelism = configInfo.jobParallelism();
    timerExecutor = new PausableScheduledThreadPoolExecutor("CtrlTimerExecutor", parallelism);
    timerExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    timerExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    timerExecutor.setRemoveOnCancelPolicy(true);
    interactionPhaser = new Phaser(1);
    LOGGER.info("[Ctrl] Job Parallelism:{}", parallelism);
  }

  private void scheduleJobs(Iterable<JobBase> jobs) {
    Iterable<JobBase> jobIterator = jobs == null ? getConfigJobs() : jobs;
    LocalDateTime now = LocalDateTime.now();
    for (JobBase impl: jobIterator) {
      //avoid initializing the jobs with the same name more than once
      if (!scheduledTask.containsKey(impl.getJobName())) {
        JobConfig config = impl.getTimerConfig();
        long interval = config.interval();
        LocalDateTime initDateTime = impl.getNextFireDateTime(now);

        if (initDateTime != null) {
          long initialDelay = ChronoUnit.MILLIS.between(now, initDateTime);
          Runnable toRun = compileJob(impl, interval);
          if (config.scheduleAfterExec()) {
            scheduledTask.put(impl.getJobName(), timerExecutor.scheduleWithFixedDelay(toRun,
                initialDelay, interval * 1000, TimeUnit.MILLISECONDS));
          } else {
            scheduledTask.put(impl.getJobName(), timerExecutor.scheduleAtFixedRate(toRun,
                initialDelay, interval * 1000, TimeUnit.MILLISECONDS));
          }
          scheduleJobList.add(impl);
          LOGGER.info("[Ctrl] Job {}({}) scheduled with fixed {}({})"
              + ", first execution will be at around {}", impl.getJobName(),
              impl.getClass().getName(), config.scheduleAfterExec() ? "delay" : "rate", interval,
              TimeUtility.toStr(initDateTime, "yyyy-MM-dd HH:mm:ss"));
        } else {
          LOGGER.info("[Ctrl] Job {}({}) won't be fired due to its configuration",
              impl.getJobName(), impl.getClass().getName());
        }
      } else {
        LOGGER.info("[Ctrl] Duplicate job found:{}({}), skipped", impl.getJobName(),
            impl.getClass().getName());
      }
    }
  }

  private Runnable compileJob(JobBase jobToRun, long interval) {
    return () -> {
      try {
        JobConfig config = jobToRun.getTimerConfig();

        if (!config.scheduleAfterExec()) {
          LOGGER.info("[Scheduler] Next execution of {} will be at around {}",
              jobToRun.getJobName(), TimeUtility.toStr(LocalDateTime.now().plusSeconds(interval),
              "yyyy-MM-dd HH:mm:ss"));
        }

        if (getExecutingJobDescriptionConsumer() != null) {
          try {
            getExecutingJobDescriptionConsumer().accept(jobToRun.ping());
          } catch (Exception ex) {
            LOGGER.error("[Scheduler] {} executingJobDescriptionDelegate exception:",
                jobToRun.getJobName(), ex);
          }
        }

        //check & do
        boolean bizFlag = false;
        try {
          bizFlag = jobToRun.checkBizToDo();
          LOGGER.info("[Scheduler] {} checkBizToDo:{}", jobToRun.getJobName(), bizFlag);
        } catch (Exception ex)  {
          LOGGER.error("[Scheduler] {} checkBizToDo exception:", jobToRun.getJobName(), ex);
        }
        if (bizFlag) {
          try {
            jobToRun.doJob();
          } catch (Exception ex) {
            LOGGER.error("[Scheduler] {} doJob exception:", jobToRun.getJobName(), ex);
          }
        }

        if (config.scheduleAfterExec()) {
          LOGGER.info("[Scheduler] Next execution of {} will be at around {}",jobToRun.getJobName(),
              TimeUtility.toStr(LocalDateTime.now().plusSeconds(interval), "yyyy-MM-dd HH:mm:ss"));
        }
      } catch (Exception ex) {
        LOGGER.error("[Scheduler] {} unknown exception:", jobToRun.getJobName(), ex);
      }
    };
  }

  private Iterable<JobBase> getConfigJobs() {
    Map<String, JobConfig> jobConfigMap = configInfo.jobs();
    List<JobBase> jobList = new ArrayList<>();
    for (Map.Entry<String, JobConfig> jobConfig:jobConfigMap.entrySet()) {
      String jobName = jobConfig.getKey();
      String implName = jobConfig.getValue().implementName();
      try {
        Class<?> implClass = Class.forName(implName);
        Constructor<?> implConstructor = implClass.getConstructor(String.class, JobCtrl.class);
        jobList.add((JobBase)implConstructor.newInstance(jobName, this));
      } catch (Exception ex) {
        LOGGER.error("Can't instantiate job class:{},{}", jobName, implName, ex);
      }
    }
    return jobList;
  }

  public void start() {
    this.start(null);
  }

  /**
   * Start to initialize jobs and schedule them by configuration.
   * 
   * @param jobList the jobs to be scheduled; if not presented,
   this controller will try to load job from jobscheduler.config
   */
  public void start(Iterable<JobBase> jobList) {
    try {
      if (!scheduled) {
        synchronized (this) {
          if (!scheduled) {
            scheduleJobs(jobList);
            scheduled = true;
          }
        }
      }
    } catch (Exception ex) {
      LOGGER.error("[Ctrl] start fail", ex);
    }
  }

  /**
   * Stop this controller and all scheduled jobs it manages.
   */
  public void stop() {
    try {
      timerExecutor.shutdown();
      if (actionRunner != null) {
        actionRunner.close();
      }
      scheduledTask.clear();
    } catch (Exception ex) {
      LOGGER.error("[Ctrl] stop fail", ex);
    } finally {
      isPaused = false;
    }
  }

  /**
   * Pause the execution of all managed jobs and internal browser.
   * 
   * @param resumableAutomatically indicate whether this pause could be resumed automatically
   * @return true if paused, otherwise false
   */
  public synchronized boolean pause(boolean resumableAutomatically) {
    if (isPaused) {
      return false;
    }

    try {
      if (this.actionRunner != null) {
        this.actionRunner.pause();
      }

      if (this.timerExecutor != null) {
        this.timerExecutor.pause();
      }

      try {
        if (this.pauseDelegate != null) {
          this.pauseDelegate.run();
        }
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] pause delegate error", ex);
      }

      for (JobBase impl: scheduleJobList) {
        try {
          impl.pause();
        } catch (Exception ex) {
          LOGGER.error("[Ctrl] job {} pause delegate error", impl.getJobName(), ex);
        }
      }
    } finally {
      this.resumableAutomatically = this.resumableAutomatically && resumableAutomatically;
      isPaused = true;
    }
    return true;
  }

  public synchronized boolean pause() {
    return pause(false);
  }

  /**
   * Resume the execution of all managed jobs and internal browser.
   * 
   * @param resumeAutomatically indicate whether this resume is automatically or manually
   * @return true if resumed, otherwise false
   */
  public synchronized boolean resume(boolean resumeAutomatically) {
    if (!isPaused) {
      return false;
    }

    if (resumeAutomatically && !this.resumableAutomatically) {
      LOGGER.info("[Ctrl] Can't resume automatically because system was paused by user"
          + ", please resume manually");
      return false;
    }

    try {
      if (this.actionRunner != null) {
        this.actionRunner.resume();
      }

      if (this.timerExecutor != null) {
        this.timerExecutor.resume();
      }

      try {
        if (this.resumeDelegate != null) {
          this.resumeDelegate.run();
        }
      } catch (Exception ex) {
        LOGGER.error("resume delegate error", ex);
      }

      for (JobBase impl: scheduleJobList) {
        try {
          impl.resume();
        } catch (Exception ex) {
          LOGGER.error("[Ctrl] job {} resume delegate error", impl.getJobName(), ex);
        }
      }
    } finally {
      isPaused = false;
      this.resumableAutomatically = true;  //reset
    }
    return true;
  }

  public synchronized boolean resume() {
    return resume(false);
  }

  public synchronized boolean isPaused() {
    return isPaused;
  }

  /**
   * Signal the interaction result.
   * 
   * @param isPositive true if the result is positive, otherwise false
   */
  public void signalInteractionResult(boolean isPositive) {
    this.positiveInteractionFlag = isPositive;
    this.interactionPhaser.arrive();        
  }

  /**
   * Forward event from source job to the other managed jobs.
   * 
   * @param sourceJob the job raising the event
   * @param event the event object to forward
   */
  public final void forwardEvent(JobBase sourceJob, Object event) {
    if (event == null) {
      return;
    }

    for (JobBase impl: scheduleJobList) {
      try {
        if (impl != sourceJob) {
          impl.receiveEvent(event);
        }
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] job {} resume delegate error", impl.getJobName(), ex);
      }
    }
  }

  /**
   * Set the delegate function to execute before pause.
   * 
   * @param pauseDelegate the pauseDelegate to set
   */
  public synchronized void setPauseDelegate(Runnable pauseDelegate) {
    this.pauseDelegate = pauseDelegate;
  }

  /**
   * Set the delegate function to execute before resume.
   * @param resumeDelegate the resumeDelegate to set
   */
  public synchronized void setResumeDelegate(Runnable resumeDelegate) {
    this.resumeDelegate = resumeDelegate;
  }

  private ActionRunner createNewActionRunner() {
    BrowserType browserType = BrowserType.fromString(configInfo.browserType());
    ActionRunner btm = browserType == null ? null : new BasicActionRunner(PageLoadStrategy.NONE,
        browserType, 1).setName("ActionRunner");
    if (btm != null) {
      LOGGER.info("[Ctrl] browser task manger created");
    }
    return btm;
  }

  /**
   * Accept a browser task to be executed in internal browser.
   * 
   * @param task browser task
   * @return true if the browser task is successfully accepted, otherwise false
   */
  public boolean acceptBrowserTask(ActionComposer task) {
    if (actionRunner == null) {
      return false;
    }

    try {
      if (!actionRunner.isBrowserAlive()) {
        restartBrowserTaskManager();
      }
      actionRunner.executeComposer(task);
      return true;
    } catch (Exception ex) {
      LOGGER.error("[Ctrl] accept task fail", ex);
      return false;
    }
  }

  /**
   * Close current internal browser and recreate a new one.
   */
  public synchronized void restartBrowserTaskManager() {
    try {
      if (actionRunner != null) {
        actionRunner.close();
      }
      LOGGER.info("[Ctrl] browser task manger closed");
    } catch (Exception ex) {
      LOGGER.error("[Ctrl] Close browser error", ex);
    }
    actionRunner = createNewActionRunner();
  }
  
  /**
   * Await the interaction result.
   */
  public void awaitInteractionResult() {
    int phaseNo = interactionPhaser.getPhase();
    if (preInteractionFunc != null) {
      try {
        preInteractionFunc.run();
      } catch (Exception e) {
        LOGGER.error("[Ctrl] preInteraction function error", e);
      }
    }
    
    try {
      interactionPhaser.awaitAdvanceInterruptibly(phaseNo);
    } catch (InterruptedException e) {
      LOGGER.info("[Ctrl] awaiting interaction result interrupted");
    }
    
    if (postInteractionFunc != null) {
      try {
        postInteractionFunc.run();
      } catch (Exception e) {
        LOGGER.error("[Ctrl] postInteraction function error", e);
      }
    }
  }

  /**
   * Get the interaction result.
   * 
   * @return the positiveInteractionFlag true if positive, otherwise false
   */
  public boolean isPositiveInteraction() {
    return positiveInteractionFlag;
  }

  /**
   * Set the function to be executed before awaiting interaction result.
   * @param func the function to set
   */
  public void setPreInteractionFunction(Runnable func) {
    this.preInteractionFunc = func;
  }

  /**
   * Set the function to be executed after awaiting interaction result.
   * @param func the function to set
   */
  public void setPostInteractionFunction(Runnable func) {
    this.postInteractionFunc = func;
  }   

  /**
   * Get the function consuming the description of executing job at the moment.
   * @return consumer function
   */
  public Consumer<String> getExecutingJobDescriptionConsumer() {
    return executingJobDescriptionConsumer;
  }

  /**
   * Set the function consuming the description of executing job at the moment.
   * @param executingJobDescriptionConsumer consumer function
   */
  public void setExecutingJobDescriptionConsumer(Consumer<String> executingJobDescriptionConsumer) {
    this.executingJobDescriptionConsumer = executingJobDescriptionConsumer;
  }
}

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
import java.util.HashMap;
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
public class JobController {
  private static final Logger LOGGER = LoggerFactory.getLogger(JobController.class);

  private final SystemConfig configInfo = ConfigCache.getOrCreate(SystemConfig.class);
  private volatile ActionRunner browserAgent;
  private final Phaser interactionPhaser;
  private volatile InteractionType latestInteractionType;
  private volatile Runnable preInteractionFunc;
  private volatile Runnable postInteractionFunc;
  private final PausableScheduledThreadPoolExecutor jobExecutor;
  private final Map<PauseTarget, PauseConfig> pauseConfigMap = new HashMap<>();
  private final List<JobBase> scheduleJobList = new ArrayList<>();
  private final Map<String, ScheduledFuture<?>> scheduledTask = new LinkedHashMap<>();
  private volatile boolean scheduled = false;

  private volatile Consumer<String> executingJobDescriptionConsumer = null; 

  /**
   * Create a new job controller.
   */
  public JobController() {
    browserAgent = createNewActionRunner();
    int parallelism = configInfo.jobParallelism();
    jobExecutor = new PausableScheduledThreadPoolExecutor("CtrlJobExecutor", parallelism);
    jobExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    jobExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    jobExecutor.setRemoveOnCancelPolicy(true);
    interactionPhaser = new Phaser(1);
    LOGGER.info("[Ctrl] Job Parallelism:{}", parallelism);
    pauseConfigMap.put(PauseTarget.Browser, new PauseConfig());
    pauseConfigMap.put(PauseTarget.JobExecutor, new PauseConfig());
  }

  private void scheduleJobs(Iterable<JobBase> jobs) {
    Iterable<JobBase> jobIterator = jobs == null ? getConfigJobs() : jobs;
    LocalDateTime now = LocalDateTime.now();
    for (JobBase impl: jobIterator) {
      //avoid initializing the jobs with the same name more than once
      if (!scheduledTask.containsKey(impl.getJobName())) {
        JobConfig config = impl.getTimerConfig();
        long interval = config.interval();
        LocalDateTime initDateTime = impl.calculateNextFireDateTime(now);

        if (initDateTime != null) {
          long initialDelay = ChronoUnit.MILLIS.between(now, initDateTime);
          Runnable toRun = compileJob(impl, interval);
          if (config.scheduleAfterExec()) {
            scheduledTask.put(impl.getJobName(), jobExecutor.scheduleWithFixedDelay(toRun,
                initialDelay, interval * 1000, TimeUnit.MILLISECONDS));
          } else {
            scheduledTask.put(impl.getJobName(), jobExecutor.scheduleAtFixedRate(toRun,
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
        //cancel according to config
        if (!TimeUtility.isBetween(config.start().get(), config.end().get(), LocalDateTime.now())) {
          scheduledTask.get(jobToRun.getJobName()).cancel(false);
          LOGGER.info("[Scheduler] Job({}) is cancelled because it isn't allowed to be executed"
              + " outside the period:{} ~ {}", jobToRun.getJobName(),
              TimeUtility.toStr(config.start().get(), "yyyy-MM-dd HH:mm:ss"),
              TimeUtility.toStr(config.end().get(), "yyyy-MM-dd HH:mm:ss"));
          return;
        }

        if (!config.scheduleAfterExec()) {
          LOGGER.info("[Scheduler] Next execution of {} will be at around {}",
              jobToRun.getJobName(), TimeUtility.toStr(LocalDateTime.now().plusSeconds(interval),
              "yyyy-MM-dd HH:mm:ss"));
        }

        if (getExecutingJobDescriptionConsumer() != null) {
          try {
            getExecutingJobDescriptionConsumer().accept(jobToRun.getJobName());
          } catch (Exception ex) {
            LOGGER.error("[Scheduler] {} executingJobDescriptionDelegate exception:",
                jobToRun.getJobName(), ex);
          }
        }

        try {
          jobToRun.run();
        } catch (Exception ex) {
          LOGGER.error("[Scheduler] {} execution exception:", jobToRun.getJobName(), ex);
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
    if (jobConfigMap == null) {
      LOGGER.warn("[Ctrl] Can't find jobs from config!");
      return new ArrayList<JobBase>();
    }
    
    List<JobBase> jobList = new ArrayList<>();
    for (Map.Entry<String, JobConfig> jobConfig:jobConfigMap.entrySet()) {
      String jobName = jobConfig.getKey();
      String implName = jobConfig.getValue().implementName();
      try {
        Class<?> implClass = Class.forName(implName);
        Constructor<?> implConstructor = implClass.getConstructor(String.class);
        jobList.add(((JobBase)implConstructor.newInstance(jobName)).setJobController(this));
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] Can't instantiate job class:{},{}", jobName, implName, ex);
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
      jobExecutor.shutdown();
      if (browserAgent != null) {
        browserAgent.close();
      }
      scheduledTask.clear();
    } catch (Exception ex) {
      LOGGER.error("[Ctrl] stop fail", ex);
    }
  }

  /**
   * Pause the execution of specified target.
   * 
   * @param target the target to pause
   * @param autoResumable indicate whether this pause could be resumed automatically
   * @return true if paused, otherwise false
   */
  public synchronized boolean pause(PauseTarget target, boolean autoResumable) {
    if (target == null || !pauseConfigMap.containsKey(target)) {
      return false;
    }
    
    PauseConfig pauseConfig = pauseConfigMap.get(target);    
    if (pauseConfig.isPaused) {
      return false;
    }
    
    try {
      switch (target)  {
        case Browser:
          if (this.browserAgent != null) {
            this.browserAgent.pause();
            LOGGER.info("[Ctrl] Bowser paused");
          }
          break;
        case JobExecutor:
          if (this.jobExecutor != null) {
            this.jobExecutor.pause();
            LOGGER.info("[Ctrl] JobExecutor paused");
          }
          break;
        default:
          break;
      }
      
      try {
        if (pauseConfig.afterPauseFunc != null) {
          pauseConfig.afterPauseFunc.run();
        }
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] error after pause({})", target.toString(), ex);
      }
      
      for (JobBase impl: scheduleJobList) {
        try {
          impl.pause();
        } catch (Exception ex) {
          LOGGER.error("[Ctrl] job({}) pause error", impl.getJobName(), ex);
        }
      }
    } finally {
      pauseConfig.autoResumable = pauseConfig.autoResumable && autoResumable;
      pauseConfig.isPaused = true;
    }
    return true;
  }
  
  public synchronized boolean pause(PauseTarget target) {
    return pause(target, false);
  }

  /**
   * Resume the execution of specified target.
   * 
   * @param target the target to resume
   * @param autoResumable indicate whether this resume is automatically or manually
   * @return true if resumed, otherwise false
   */
  public synchronized boolean resume(PauseTarget target, boolean autoResumable) {
    if (target == null || !pauseConfigMap.containsKey(target)) {
      return false;
    }
    
    PauseConfig pauseConfig = pauseConfigMap.get(target);
    if (!pauseConfig.isPaused) {
      return false;
    }

    if (autoResumable && !pauseConfig.autoResumable) {
      LOGGER.info("[Ctrl] Can't resume " + target.toString()
          + " automatically because system was paused by user, please resume manually");
      return false;
    }

    try {
      switch (target)  {
        case Browser:
          if (this.browserAgent != null) {
            this.browserAgent.resume();
            LOGGER.info("[Ctrl] Browser resumed");
          }
          break;
        case JobExecutor:
          if (this.jobExecutor != null) {
            this.jobExecutor.resume();
            LOGGER.info("[Ctrl] JobExecutor resumed");
          }
          break;
        default:
          break;
      }

      try {
        if (pauseConfig.afterResumeFunc != null) {
          pauseConfig.afterResumeFunc.run();
        }
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] error after resume({})", target.toString(), ex);
      }
      
      for (JobBase impl: scheduleJobList) {
        try {
          impl.resume();
        } catch (Exception ex) {
          LOGGER.error("[Ctrl] job({}) resume error", impl.getJobName(), ex);
        }
      }
    } finally {
      pauseConfig.isPaused = false;
      pauseConfig.autoResumable = true;  //reset
    }
    return true;
  }
  
  public synchronized boolean resume(PauseTarget target) {
    return resume(target, false);
  }

  /**
   * Check if the specified target has already paused.
   * 
   * @param target the target to check
   * @return true if paused, otherwise false
   */
  public synchronized boolean isPaused(PauseTarget target) {
    if (target == null || !pauseConfigMap.containsKey(target)) {
      return false;
    }
    PauseConfig pauseConfig = pauseConfigMap.get(target);
    return pauseConfig.isPaused;
  }

  /**
   * Signal the interaction type.
   * 
   * @param interaction the interaction type
   */
  public void signalInteractionType(InteractionType interaction) {
    this.latestInteractionType = interaction;
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
   * Set the function to execute after pause.
   * 
   * @param target the target to pause
   * @param afterPauseFunc the pauseDelegate to set
   */
  public void setAfterPauseFunction(PauseTarget target, Runnable afterPauseFunc) {
    if (target != null && pauseConfigMap.containsKey(target)) {
      pauseConfigMap.get(target).afterPauseFunc = afterPauseFunc;
    }
  }

  /**
   * Set the function to execute after resume.
   * 
   * @param target the target to resume
   * @param afterResumeFunc the resumeDelegate to set
   */
  public void setAfterResumeFunction(PauseTarget target, Runnable afterResumeFunc) {
    if (target != null && pauseConfigMap.containsKey(target)) {
      pauseConfigMap.get(target).afterResumeFunc = afterResumeFunc;
    }
  }

  private ActionRunner createNewActionRunner() {
    BrowserType browserType = BrowserType.fromString(configInfo.browserType());
    if (configInfo.headlessBrowser()) {
      System.setProperty("webdriver_headless","yes");
    }
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
    if (browserAgent == null || task == null) {
      return false;
    }

    synchronized (browserAgent) {
      if (browserAgent == null) {
        return false;
      }
      
      try {
        if (!browserAgent.isBrowserAlive()) {
          restartBrowserTaskManager();
        }
        browserAgent.executeComposer(task);
        return true;
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] accept task fail", ex);
        return false;
      }
    }
  }

  /**
   * Close current internal browser and recreate a new one.
   */
  public void restartBrowserTaskManager() {
    if (browserAgent == null) {
      return;
    }
    synchronized (browserAgent) {
      if (browserAgent == null) {
        return;
      }
      
      try {
        browserAgent.close();
        LOGGER.info("[Ctrl] browser task manger closed");
      } catch (Exception ex) {
        LOGGER.error("[Ctrl] Close browser error", ex);
      }
    }
    browserAgent = createNewActionRunner();
  }
  
  /**
   * Await the external interaction.
   */
  public void awaitInteraction() {
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
      LOGGER.info("[Ctrl] awaiting interaction result...");
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
   * Get the latest interaction.
   * 
   * @return the latest interaction
   */
  public InteractionType getLatestInteraction() {
    return latestInteractionType;
  }

  /**
   * Set the function to be executed before awaiting interaction.
   * @param func the function to set
   */
  public void setPreInteractionFunction(Runnable func) {
    this.preInteractionFunc = func;
  }

  /**
   * Set the function to be executed after awaiting interaction.
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
  
  private static class PauseConfig {
    private volatile boolean isPaused = false;
    private volatile boolean autoResumable = true;
    private volatile Runnable afterPauseFunc = null;
    private volatile Runnable afterResumeFunc = null;
  }
  
  public enum PauseTarget {
    Browser, JobExecutor;
  }
  
  public enum InteractionType {
    Positive, Negative;
  }
}

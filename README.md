# Job-Scheduler
Job-Scheduler is a simple platform made in Java language which could control
multiple jobs to be executed by configured schedules. It incorporates
[auto-browser][] to support customized browser automation in addition to
vanilla(non-browser) jobs.

## Getting Started
Add below to maven's `pom.xml`:
```xml
<dependency>
  <groupId>org.kquiet</groupId>
  <artifactId>job-scheduler</artifactId>
  <version>X.X.X</version>
</dependency>
```

## What is a job
In Job-Scheduler, a job is a class which inherits the designated abstract class
`JobBase` and implements its own processing logic of job.
A sample job is as follows:
```java
import org.kquiet.jobscheduler.JobBase;

public class RestartBrowser extends JobBase {
  public RestartBrowser(String jobName) {
    super(jobName);
  }

  @Override
  public void run() {
    //The processing logic of job goes here.
    restartInternalBrowser();
  }
}
```
It's pretty easy because the only thing needed to do is to implement the
processing logic inside method `run()`. This job will be executed in a thread
managed by an internal thread pool executor.

## What is a job schedule
In Job-Scheduler, all configuration should be placed in a file named
`jobscheduler.config`. A job schedule could be configured like this:
```Java Properties
job.TestJob1.start=2019-11-01T00:00:00
job.TestJob1.end=2019-12-01T00:00:00
job.TestJob1.dailyStart=03:00:00
job.TestJob1.dailyEnd=15:00:00
job.TestJob1.interval=10
job.TestJob1.scheduleAfterExec=true
```
Which means job `TestJob1` will be executed repeatedly during `03:00:00 ~
15:00:00` everyday with a fix `delay` equal to `10` seconds in the period from
`2019-11-01T00:00:00` through `2019-12-01T00:00:00`.

## Description of `jobscheduler.config`
A complete configuration sample:
```Java Properties
instanceName=JobSchedulerDefault
gui.enable=false
gui.clearLogInterval=86400
browser.type=

job.parallelism=1
job.enable=RestartBrowser
job.RestartBrowser.impl=org.kquiet.jobscheduler.impl.common.RestartBrowser
job.RestartBrowser.start=2019-11-01T00:00:00
job.RestartBrowser.end=2019-12-01T00:00:00
job.RestartBrowser.dailyStart=03:00:00
job.RestartBrowser.dailyEnd=03:00:01
job.RestartBrowser.interval=43200
job.RestartBrowser.scheduleAfterExec=true
job.RestartBrowser.parameter={}
```
|Name|Default|Description|
|---|---|---|
|`instanceName`||The instance name of job-scheduler|
|`gui.enable`|`false`|Set `true` to enable optional monitoring GUI|
|`gui.clearLogInterval`|`86400`|Clear log on GUI with specified rate(in seconds)|
|`browser.type`||Available values are `chrome` and `firefox`. Leave it to blank if internal browser is not required.|
|`browser.headless`|`true`|`true`: internal browser will display its GUI;`false`: internal browser won't display its GUI|
|`job.parallelism`|`1`|It controls how many jobs could be executed concurrently at most.|
|`job.enable`||Only the jobs with their names listed here will be scheduled. Use `,` to separate each other.|
|`job.${name}.impl`||Class name of job|
|`job.${name}.start`||The start of the absolute period in which the execution of job is allowed|
|`job.${name}.end`||The end of the absolute period in which the execution of job is allowed|
|`job.${name}.dailyStart`||The start of the daily period in which the execution of job is allowed|
|`job.${name}.dailyEnd`||The end of the daily period in which the execution of job is allowed|
|`job.${name}.interval`||The interval(in seconds) between two consecutive execution of job|
|`job.${name}.scheduleAfterExec`||`true`: means the `interval` is the delay between the termination of one execution and the commencement of the next ;`false`: means the `interval` is the rate between the commencement of one execution and the commencement of the next.|
|`job.${name}.parameter`|`{}`|The parameters of job in json format.|

***Note: All times are in local time zone.***

## Use of Docker Image
1. Get the image from docker hub: `docker pull kquiet/job-scheduler:latest`
2. Prepare all the jar files of your libraries and dependencies with
`jobschedule.config`, then:
    - Map the library path to `/opt/kquiet/job-scheduler/lib` when you run it,
    e.g., `docker run -d -v /path/to/library:/opt/kquiet/job-scheduler/lib
    kquiet/job-scheduler:latest`
    - Or use `docker build` to put them into `/opt/kquiet/job-scheduler/lib`
    to create your own image to run it without volume mapping 

## Q&A
1. How to configure a job with multiple combination of (dailyStart, dailyEnd, interval)?  
=> Currently it can only be achieved by configuring multiple job schedules with
the same job class in different job names like this:
	```Java Properties
	job.TestJob1.impl=xxx.yyy.zzz.JobClass
	job.TestJob1.dailyStart=03:00:00
	job.TestJob1.dailyEnd=05:00:00
	job.TestJob1.interval=10
	
	job.TestJob2.impl=xxx.yyy.zzz.JobClass
	job.TestJob2.dailyStart=15:00:00
	job.TestJob2.dailyEnd=17:00:00
	job.TestJob2.interval=10
	```
    The caveat of this approach is that two instances of JobClass are created.
2. I can't see the optional GUI or browser on screen even with `gui.enable`, `browser.type`
, `browser.headless` configured correctly when using docker image to run.  
=> When creating docker container, you need to add the environment variable `DISPLAY` with
proper value according to your environment, and add the mapping of volume path
`/tmp/.X11-unix` to container, e.g., `docker run -d -e DISPLAY=:0 -v /tmp/.X11-unix:/tmp/.X11-unix kquiet/job-scheduler:latest`.

[auto-browser]: https://github.com/kquiet/auto-browser "auto-browser in github"
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

package org.kquiet.jobscheduler.impl.common;

import org.kquiet.jobscheduler.JobBase;
import org.kquiet.jobscheduler.JobCtrl;

/**
 * Restart internal browser of associated job controller.
 * 
 * @author Kimberly
 */
public class RestartBrowser extends JobBase {
  public RestartBrowser(String jobName, JobCtrl ctrl) {
    super(jobName, ctrl);
  }

  @Override
  protected boolean checkBizToDo() {
    return true;
  }

  @Override
  protected void doJob() {
    this.getController().restartBrowserTaskManager();
  }

  @Override
  protected String ping() {
    return getJobName();
  }
}

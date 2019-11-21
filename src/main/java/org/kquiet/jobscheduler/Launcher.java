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

import static javax.swing.UIManager.getInstalledLookAndFeels;

import javax.swing.UIManager.LookAndFeelInfo;

import org.aeonbits.owner.ConfigCache;

/**
 * Application launcher.
 *
 * @author Kimberly
 */
public class Launcher {
  /**
   * Start the application with/without gui by config.
   * @param args arguments
   */
  public static void main(String[] args) {
    try {
      SystemConfig configInfo = ConfigCache.getOrCreate(SystemConfig.class);

      if (configInfo.guiFlag()) {
        for (LookAndFeelInfo info : getInstalledLookAndFeels()) {
          if ("Nimbus".equals(info.getName())) {
            javax.swing.UIManager.setLookAndFeel(info.getClassName());
            break;
          }
        }
        DashboardJFrame launchedObj = new DashboardJFrame();
        launchedObj.setVisible(true);
      } else {
        JobCtrl controller = new JobCtrl();
        controller.start();
      }
    } catch (Exception ex) {
      ex.printStackTrace(System.out);
    }
  }
}
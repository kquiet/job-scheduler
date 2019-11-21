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

package org.kquiet.jobscheduler.util;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * A log appender used to display message on JTextArea.
 * 
 * @author Kimberly
 */
public class JTextAreaLogAppender extends AppenderBase<ILoggingEvent> {
  private PatternLayoutEncoder encoder = null;
  private JTextArea textArea = null;

  @Override
  public void append(ILoggingEvent event) {
    String logStr = this.encoder.getLayout().doLayout(event);

    if (this.textArea != null) {
      SwingUtilities.invokeLater(() -> {
        this.textArea.append(logStr);
      });
    }
  }

  public PatternLayoutEncoder getEncoder() {
    return encoder;
  }

  public void setEncoder(PatternLayoutEncoder encoder) {
    this.encoder = encoder;
  }
  
  public void setTextArea(JTextArea textArea) {
    this.textArea = textArea;
  }
}
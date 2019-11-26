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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Time utility class.
 * 
 * @author Kimberly
 */
public final class TimeUtility {
  private TimeUtility(){}

  /**
   * Format {@code LocalDteTime.now()} as a string.
   * 
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String nowStr(String format) {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
  }
  
  /**
   * Format {@code LocalDateTime.now(ZoneOffset.UTC)} as a string.
   * 
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String utcNowStr(String format) {
    return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(format));
  }
  
  /**
   * Format given LocalDateTime as a string.
   * 
   * @param dateTime the {@link LocalDateTime} to be formatted
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String toStr(LocalDateTime dateTime, String format) {
    if (dateTime == null) {
      return "";
    }
    return dateTime.format(DateTimeFormatter.ofPattern(format));
  }
  
  /**
   * Format given ZonedDateTime as a string.
   * 
   * @param zonedDateTime the {@link ZonedDateTime} to be formatted
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String toStr(ZonedDateTime zonedDateTime, String format) {
    if (zonedDateTime == null) {
      return "";
    }
    return zonedDateTime.format(DateTimeFormatter.ofPattern(format));
  }
  
  /**
   * Format given LocalDate as a string.
   * 
   * @param date the {@link LocalDate} to be formatted
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String toStr(LocalDate date, String format) {
    if (date == null) {
      return "";
    }
    return date.format(DateTimeFormatter.ofPattern(format));
  }
  
  /**
   * Format given LocalTime as a string.
   * 
   * @param time the {@link LocalTime} to be formatted
   * @param format the format to be used in formating
   * @return formatted result string
   */
  public static String toStr(LocalTime time, String format) {
    if (time == null) {
      return "";
    }
    return time.format(DateTimeFormatter.ofPattern(format));
  }

  /**
   * Check whether specific LocalDateTime is in the period formed by two given LocalDateTime.
   * 
   * @param start the start of period
   * @param end the end of period
   * @param dateTime the {@link LocalDateTime} to check
   * @return true if {@code dateTime} is between the period, otherwise false
   */
  public static boolean isBetween(LocalDateTime start, LocalDateTime end, LocalDateTime dateTime) {
    if (start.isAfter(end)) {
      return false;
    } else {
      return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }
  }

  /**
   * Check whether specific LocalTime is in the period formed by two given LocalTime.
   * 
   * @param start the start of period
   * @param end the end of period
   * @param time the {@link LocalTime} to check
   * @return true if {@code time} is between the period, otherwise false
   */
  public static boolean isBetween(LocalTime start, LocalTime end, LocalTime time) {
    if (start.equals(end)) {
      return true;
    } else if (start.isAfter(end)) {
      return !time.isBefore(start) || !time.isAfter(end);
    } else {
      return !time.isBefore(start) && !time.isAfter(end);
    }
  }

  /**
   * Calculate the date and time of next fire.
   * 
   * @param start the start {@link LocalDateTime} of fireable period
   * @param end  the end {@link LocalDateTime} of fireable period
   * @param dailyStart the start {@link LocalTime} of daily fireable period
   * @param dailyEnd the end {@link LocalTime} of daily fireable period
   * @param from the {@link LocalDateTime} to calculate from
   * @return the {@link LocalDateTime} of next fire 
   */
  public static LocalDateTime calculateNextFireDateTime(LocalDateTime start, LocalDateTime end,
      LocalTime dailyStart, LocalTime dailyEnd, LocalDateTime from) {
    //impossible to fire
    if (start.isAfter(end) || from.isAfter(end)) {
      return null;
    }

    if (from.isBefore(start)) {
      from = start;
    }
    LocalDate startDate = from.toLocalDate();
    LocalTime startTime = from.toLocalTime();
    LocalDateTime candidate = null;

    if (dailyStart.equals(dailyEnd)) {
      //fire at any time in a day
      candidate = from;
    } else if (dailyStart.isAfter(dailyEnd)) {
      if (startTime.isBefore(dailyStart) && startTime.isAfter(dailyEnd)) {
        candidate = LocalDateTime.of(startDate, dailyStart);
      } else {
        candidate = from;
      }
    } else {
      if (!startTime.isBefore(dailyStart) && !startTime.isAfter(dailyEnd)) {
        candidate = from;
      } else if (startTime.isBefore(dailyStart)) {
        candidate = LocalDateTime.of(startDate, dailyStart);
      } else {
        candidate = LocalDateTime.of(startDate.plusDays(1), dailyStart);
      }
    }

    //no fire after end
    if (candidate.isAfter(end)) {
      return null;
    } else {
      return candidate;
    }
  }
}

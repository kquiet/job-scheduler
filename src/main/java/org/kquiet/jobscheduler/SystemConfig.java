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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.Reloadable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System config.
 * 
 * @author Kimberly
 */
@Config.HotReload(value = 10, unit = TimeUnit.SECONDS, type = Config.HotReloadType.ASYNC)
@Config.Sources({"file:jobscheduler.config", "classpath:jobscheduler.config"})
@Config.LoadPolicy(Config.LoadType.FIRST)
public interface SystemConfig extends Reloadable {
  @Config.Key("gui.enable")
  @DefaultValue("false")
  boolean guiFlag();

  @Config.Key("instanceName")
  @DefaultValue("")
  String instanceName();

  @Config.Key("browser.type")
  @DefaultValue("")
  String browserType();
  
  @Config.Key("browser.headless")
  @DefaultValue("true")
  boolean headlessBrowser();

  @Config.Key("gui.clearLogInterval")
  @DefaultValue("86400")
  int clearLogInterval();

  @Config.Key("job.parallelism")
  @DefaultValue("1")
  int jobParallelism();

  @Config.ConverterClass(JobListConverter.class)
  @Config.Key("job.enable")
  @DefaultValue("")
  Map<String, JobConfig> jobs();

  @Sources({"file:jobscheduler.config", "classpath:jobscheduler.config"})
  @LoadPolicy(LoadType.FIRST)
  interface JobConfig extends Reloadable {
    @Key("job.${jobName}.impl")
    String implementName();

    @ConverterClass(OptionalStartLocalDateTimeConverter.class)
    @Key("job.${jobName}.start")
    @DefaultValue("")
    Optional<LocalDateTime> start();

    @ConverterClass(OptionalEndLocalDateTimeConverter.class)
    @Key("job.${jobName}.end")
    @DefaultValue("")
    Optional<LocalDateTime> end();

    @ConverterClass(OptionalLocalTimeConverter.class)
    @Key("job.${jobName}.dailyStart")
    Optional<LocalTime> dailyStart();

    @ConverterClass(OptionalLocalTimeConverter.class)
    @Key("job.${jobName}.dailyEnd")
    Optional<LocalTime> dailyEnd();

    @Config.Key("job.${jobName}.interval")
    int interval();

    @ConverterClass(BooleanConverter.class)
    @Key("job.${jobName}.scheduleAfterExec")
    boolean scheduleAfterExec();


    @ConverterClass(JobParameterConverter.class)
    @Key("job.${jobName}.parameter")
    @DefaultValue("{}")
    Map<String,String> parameter();
  }

  //converter classes
  class BooleanConverter implements Converter<Boolean> {
    @Override
    public Boolean convert(Method method, String input) {
      return Boolean.valueOf(input);
    }
  }

  class OptionalStartLocalDateTimeConverter implements Converter<Optional<LocalDateTime>> {
    @Override
    public Optional<LocalDateTime> convert(Method method, String input) {
      Logger logger = LoggerFactory.getLogger(SystemConfig.class);
      try {
        if (Optional.ofNullable(input).orElse("").trim().isEmpty()) {
          return Optional.of(LocalDateTime.parse("1900-01-01T00:00:00"));
        } else {
          return Optional.of(LocalDateTime.parse(input));
        }
      } catch (Exception ex) {
        logger.error("Error config:{}", input);
        return Optional.ofNullable(null);
      }
    }
  }

  class OptionalEndLocalDateTimeConverter implements Converter<Optional<LocalDateTime>> {
    @Override
    public Optional<LocalDateTime> convert(Method method, String input) {
      Logger logger = LoggerFactory.getLogger(SystemConfig.class);
      try {
        if (Optional.ofNullable(input).orElse("").trim().isEmpty()) {
          return Optional.of(LocalDateTime.MAX);
        } else {
          return Optional.of(LocalDateTime.parse(input));
        }
      } catch (Exception ex) {
        logger.error("Error config:{}", input);
        return Optional.ofNullable(null);
      }
    }
  }

  class OptionalLocalTimeConverter implements Converter<Optional<LocalTime>> {
    @Override
    public Optional<LocalTime> convert(Method method, String input) {
      Logger logger = LoggerFactory.getLogger(SystemConfig.class);
      try {
        return Optional.of(LocalTime.parse(input));
      } catch (Exception ex) {
        logger.error("Error config:{}", input);
        return Optional.ofNullable(null);
      }
    }
  }

  class JobListConverter implements Converter<Map<String, JobConfig>> {
    @Override
    public Map<String, JobConfig> convert(Method method, String input) {
      Map<String, JobConfig> jobList = new LinkedHashMap<>();
      if (input != null) {
        List<String> nameList = Arrays.asList(input.split(",")).stream()
            .map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        for (String name:nameList) {
          Map<String, String> imports = new HashMap<>();
          imports.put("jobName", name);
          JobConfig config = ConfigFactory.create(JobConfig.class, imports);
          jobList.put(name, config);
        }
      }

      return jobList;
    }
  }

  class JobParameterConverter implements Converter<Map<String,String>> {
    @Override
    public Map<String,String> convert(Method method, String input) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        if (input == null || input.isEmpty()) {
          return new LinkedHashMap<>();
        }

        Map<String, String> parameterMap = mapper.readValue(input,
            new TypeReference<Map<String,String>>(){});
        return parameterMap;
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }
}

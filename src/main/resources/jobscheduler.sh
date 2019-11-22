#!/bin/sh

exec java -Dwebdriver.gecko.driver=geckodriver -Dwebdriver.chrome.driver=chromedriver -Dchrome_sandbox=no -jar ${project.build.finalName}.jar

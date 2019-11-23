#!/bin/sh

exec java -Dwebdriver.gecko.driver=geckodriver -Dwebdriver.chrome.driver=chromedriver -Dchrome_sandbox=no -Dwebdriver_headless=yes -cp "lib/*" org.kquiet.jobscheduler.Launcher

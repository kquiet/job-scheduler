cd %~dp0
start "" javaw -Dwebdriver.gecko.driver=geckodriver.exe -Dwebdriver.chrome.driver=chromedriver.exe -cp "lib/;lib/*" org.kquiet.jobscheduler.Launcher

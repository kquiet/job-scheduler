cd %~dp0
start "" javaw -Dwebdriver.gecko.driver=geckodriver.exe -Dwebdriver.chrome.driver=chromedriver.exe -cp "lib/*" org.kquiet.jobscheuler.Launcher

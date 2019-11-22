cd %~dp0
start "" javaw -Dwebdriver.gecko.driver=geckodriver.exe -Dwebdriver.chrome.driver=chromedriver.exe -jar ${project.build.finalName}.jar

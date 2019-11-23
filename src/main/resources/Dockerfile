FROM openjdk:8-jre-alpine3.9

RUN echo '@edgetesting http://dl-cdn.alpinelinux.org/alpine/edge/testing' >> /etc/apk/repositories \
 && apk update \
 && apk add dbus-x11 fontconfig tzdata pptpclient@edgetesting \
 && mkdir -p /usr/share/fonts \
 && wget https://github.com/googlei18n/noto-cjk/raw/master/NotoSansCJK-Regular.ttc -O /usr/share/fonts/NotoSansCJK-Regular.ttc \
 && mkdir -p /opt/kquiet/${project.artifactId} \
 && apk add 'chromium>70' 'chromium-chromedriver>70' \
 && ln -s /usr/bin/chromedriver /opt/kquiet/${project.artifactId}/chromedriver \
 && rm -rf /usr/share/fonts/TTF/ \
 && apk add 'firefox-esr>60' \
 && wget https://github.com/mozilla/geckodriver/releases/download/v0.24.0/geckodriver-v0.24.0-linux64.tar.gz -O /opt/kquiet/${project.artifactId}/geckodriver.tar.gz \
 && cd /opt/kquiet/${project.artifactId} && tar -zxvf geckodriver.tar.gz && rm geckodriver.tar.gz

WORKDIR /opt/kquiet/${project.artifactId}

COPY ["jobscheduler.sh", "./"]

COPY ["${project.build.finalName}.jar", "lib", "./lib/"]

ENTRYPOINT ["./jobscheduler.sh"]
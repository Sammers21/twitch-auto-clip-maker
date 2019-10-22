FROM alpine as build
RUN apk add --no-cache --upgrade nodejs nodejs-npm openjdk11 gradle --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
ENV BUILD_HOME=/build-dir
WORKDIR $BUILD_HOME
COPY build.gradle settings.gradle gradle.properties $BUILD_HOME/
COPY core/build.gradle $BUILD_HOME/core/
COPY http-server/build.gradle $BUILD_HOME/http-server/
COPY chat-recorder/build.gradle $BUILD_HOME/chat-recorder/
COPY youtube-producer/build.gradle $BUILD_HOME/youtube-producer/
RUN gradle compileJava --info
COPY . ./
RUN cd http-server \
    && gradle jar --info \
    && mkdir /app \
    && cp build/libs/server.jar /app

FROM azul/zulu-openjdk-alpine:11
COPY --from=build /app /app
EXPOSE 80
ENTRYPOINT java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
                -jar /app/server.jar 80
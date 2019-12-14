FROM alpine as build
RUN apk add --no-cache --upgrade openjdk11 gradle --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
ENV BUILD_HOME=/build-dir
ENV APP_DIR=/app
WORKDIR $BUILD_HOME
COPY build.gradle settings.gradle gradle.properties $BUILD_HOME/
COPY core/build.gradle $BUILD_HOME/core/
COPY http-server/build.gradle $BUILD_HOME/http-server/
COPY chat-recorder/build.gradle $BUILD_HOME/chat-recorder/
COPY youtube-producer/build.gradle $BUILD_HOME/youtube-producer/
RUN gradle compileJava --info
COPY . ./
RUN cd clip-producer \
    && gradle jar --info \
    && mkdir $APP_DIR \
    && cp build/libs/clip-producer.jar $APP_DIR

COPY deployment/cfg.json $APP_DIR/
COPY deployment/db.json $APP_DIR/

FROM azul/zulu-openjdk-alpine:11
COPY --from=build /app /app
EXPOSE 5005
ENTRYPOINT java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
                -jar /app/clip-producer.jar -cfg $APP_DIR/cfg.json -db $APP_DIR/db.json
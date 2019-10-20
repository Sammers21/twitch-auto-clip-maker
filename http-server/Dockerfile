FROM alpine as build
RUN apk add --no-cache --upgrade nodejs nodejs-npm openjdk11 gradle --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
WORKDIR /build-dir
COPY . ./
RUN cd http-server \
    && PUBLIC_URL=http://static.clip-maker.com:81 gradle jar --info \
    && mkdir /app \
    && cp build/libs/server.jar /app

FROM azul/zulu-openjdk-alpine:11
COPY --from=build /app /app
EXPOSE 80
ENTRYPOINT java -jar /app/server.jar 80
#!/usr/bin/env bash
set -x
set -e
SSH_HOST="clip-maker.com"
cd ~
cd twitch-auto-clip-maker
git pull
gradle youtube-producer:clean youtube-producer:versionTxt youtube-producer:build
ssh root@$SSH_HOST <<'ENDSSH'
set -x
mkdir -p /root/youtube-producer
ENDSSH
scp youtube-producer/build/libs/youtube-producer-*.jar root@$SSH_HOST:/root/youtube-producer
scp ~/deployments/twitch-clip-maker/cfg.json root@$SSH_HOST:/root/youtube-producer
scp ~/deployments/twitch-clip-maker/db.json root@$SSH_HOST:/root/youtube-producer
scp -r ~/deployments/twitch-clip-maker/youtube_production root@$SSH_HOST:/root/youtube-producer/
ssh root@$SSH_HOST <<'ENDSSH'
set -x
$(/root/.sdkman/candidates/java/current/bin/jps | grep "youtube-producer*" | awk '{print $1}' | xargs kill -9) || true
echo $EXIT_CODE
cd /root/youtube-producer
JVM_OPTS="-XX:+HeapDumpOnOutOfMemoryError -Xmx1g -Xlog:gc*:file=./gc.log"
nohup /root/.sdkman/candidates/java/current/bin/java $JVM_OPTS \
                                                     -jar \
                                                     /root/youtube-producer/youtube-producer-*.jar \
                                                     -cfg /root/youtube-producer/cfg.json \
                                                     -db /root/youtube-producer/db.json \
                                                     -pd /root/youtube-producer/youtube_production \
                                                     -host clip-maker.com \
                                                    &
sleep 3
ENDSSH
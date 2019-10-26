#!/usr/bin/env bash
set -x
set -e
SSH_HOST="159.89.11.41"
cd ~
cd twitch-auto-clip-maker 
git pull
gradle clip-producer:clean clip-producer:versionTxt clip-producer:build
ssh root@$SSH_HOST <<'ENDSSH'
set -x
mkdir -p /root/twitch-auto-clip
ENDSSH
scp clip-producer/build/libs/twitch-auto-clip-producer-*.jar root@$SSH_HOST:/root/twitch-auto-clip
scp ~/deployments/twitch-clip-maker/cfg.json root@$SSH_HOST:/root/twitch-auto-clip
scp ~/deployments/twitch-clip-maker/db.json root@$SSH_HOST:/root/twitch-auto-clip
ssh root@$SSH_HOST <<'ENDSSH'
set -x
JVM_OPTS="-XX:+HeapDumpOnOutOfMemoryError -Xmx400m -Xlog:gc*:file=./gc.log"
$(/root/.sdkman/candidates/java/current/bin/jps | grep "twitch-auto-clip*" | awk '{print $1}' | xargs kill -9) || true
echo $EXIT_CODE
cd /root/twitch-auto-clip
nohup /root/.sdkman/candidates/java/current/bin/java $JVM_OPTS \
                                                     -jar \
                                                     /root/twitch-auto-clip/twitch-auto-clip-producer-*.jar \
                                                     -cfg /root/twitch-auto-clip/cfg.json \
                                                     -db /root/twitch-auto-clip/db.json \
                                                      2> err.log
                                                    &
sleep 3
ENDSSH
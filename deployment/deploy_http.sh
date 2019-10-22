#!/usr/bin/env bash
set -x
set -e
CONTAINER_NAME="http-server"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
SSH_HOST="159.89.11.41"

cd ~
cd twitch-auto-clip-maker
docker build -f http-server/Dockerfile . -t $IMAGE_NAME
docker push $IMAGE_NAME:latest
ssh root@$SSH_HOST <<'ENDSSH'
CONTAINER_NAME="http-server"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
set -x
docker pull $IMAGE_NAME
docker stop $CONTAINER_NAME
docker rm $CONTAINER_NAME
docker run -p80:80  -p5005:5005 --name=$CONTAINER_NAME --restart=always -d $IMAGE_NAME
docker logs -f $CONTAINER_NAME
ENDSSH
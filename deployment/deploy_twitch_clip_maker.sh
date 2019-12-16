#!/usr/bin/env bash
set -x
set -e
CONTAINER_NAME="clip-producer"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
SSH_HOST="app.clip-maker.com"

cd ~/twitch-auto-clip-maker
docker build -f clip-producer/Dockerfile . -t $IMAGE_NAME
docker push $IMAGE_NAME:latest
ssh root@$SSH_HOST <<'ENDSSH'
CONTAINER_NAME="clip-producer"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
set -x
docker pull $IMAGE_NAME
docker stop $CONTAINER_NAME
docker rm $CONTAINER_NAME
docker run -p5006:5005 --name=$CONTAINER_NAME --restart=always -d $IMAGE_NAME
docker logs -f $CONTAINER_NAME
ENDSSH
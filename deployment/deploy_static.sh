#!/usr/bin/env bash
set -x
set -e
CONTAINER_NAME="static-front"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
SSH_HOST="159.89.11.41"

cd ~
cd twitch-auto-clip-maker/frontend
docker build . -t $IMAGE_NAME
docker push $IMAGE_NAME:latest
ssh root@$SSH_HOST <<'ENDSSH'
CONTAINER_NAME="static-front"
REGISTRY="repo.treescale.com/sammers"
IMAGE_NAME="$REGISTRY/$CONTAINER_NAME"
set -x
docker pull $IMAGE_NAME
docker stop $CONTAINER_NAME
docker rm $CONTAINER_NAME
docker run -p81:80 --name=$CONTAINER_NAME --restart=always -d $IMAGE_NAME
ENDSSH
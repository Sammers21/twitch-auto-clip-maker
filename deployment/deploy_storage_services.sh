#!/usr/bin/env bash
set -x
set -e
SSH_HOST="159.89.11.41"
scp ~/twitch-auto-clip-maker/deployment/stack.yml root@$SSH_HOST:/root
ssh root@$SSH_HOST <<'ENDSSH'
set -x
docker-compose -f /root/stack.yml down
docker-compose -f /root/stack.yml up -d
ENDSSH
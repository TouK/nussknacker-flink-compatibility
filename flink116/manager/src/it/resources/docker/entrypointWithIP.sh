#!/bin/bash

mkdir -p /tmp/$SAVEPOINT_DIR_NAME
chmod -R 777 /tmp/$SAVEPOINT_DIR_NAME

cat /conf.yml | sed s/SAVEPOINT_DIR_NAME/$SAVEPOINT_DIR_NAME/ >> $FLINK_HOME/conf/flink-conf.yaml

/docker-entrypoint.sh "$@"

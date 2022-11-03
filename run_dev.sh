#!/bin/sh
echo 'RUNNING PROGRAM'
cd /app/comet-algorithm
env $(cat /env/.env | tr -d '\r') ./bin/comet-algorithm

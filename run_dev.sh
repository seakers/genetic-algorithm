#!/bin/sh
echo 'RUNNING PROGRAM'
cd /app/genetic-algorithm
env $(cat /env/.env | tr -d '\r') ./bin/genetic-algorithm

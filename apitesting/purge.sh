#!/bin/bash

SCRIPT=$(readlink -f "$0")
SCRIPTPATH=$(dirname "$SCRIPT")
QUEUE_URL="https://us-east-2.queue.amazonaws.com/923405430231/$1"
MSGPATH="file://${SCRIPTPATH}/$2"


sudo aws sqs purge-queue --queue-url "$QUEUE_URL"
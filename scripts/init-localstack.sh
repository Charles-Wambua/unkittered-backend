#!/bin/bash
# Runs inside the localstack container on startup. Creates the SQS queues the
# backend publishes to / consumes from.
set -e
awslocal sqs create-queue --queue-name unkittered-match-events
echo "✅ SQS queue 'unkittered-match-events' created"

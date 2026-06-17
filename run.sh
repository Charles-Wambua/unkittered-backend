#!/bin/bash
# Runs the API with JDK 21 (Spring Boot 3.4 targets Java 21 LTS).
set -e
export JAVA_HOME="${JAVA_HOME:-/Users/charleswambua/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home}"
echo "Using JAVA_HOME=$JAVA_HOME"
exec mvn spring-boot:run

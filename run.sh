#!/bin/bash
# ScreenAI Client Launch Script

cd "$(dirname "$0")" || exit

echo "Building ScreenAI Client..."
./mvnw clean compile -q

echo "Starting ScreenAI Client..."
./mvnw compile exec:java -Dexec.mainClass="App" 2>&1


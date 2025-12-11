#!/bin/bash

echo "========================================="
echo "  ScreenAI JavaFX Application Launcher"
echo "========================================="
echo ""
echo "Project: ScreenAI Screen Sharing Client"
echo "Status: Starting build and launch..."
echo ""

cd /Users/rajatkumar/Documents/Rajat/ScreenAI-Client/untitled

# Make mvnw executable
chmod +x ./mvnw

echo "✅ Step 1: Checking Java..."
java -version 2>&1 | head -1

echo ""
echo "✅ Step 2: Building project..."
echo "   Running: ./mvnw clean javafx:run -DskipTests"
echo ""

# Run Maven
./mvnw clean javafx:run -DskipTests

echo ""
echo "✅ Application execution complete!"


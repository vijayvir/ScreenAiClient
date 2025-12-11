#!/bin/bash
#
# Maven Wrapper Script for macOS/Linux
#

# Resolve the project directory
PRG="$0"
while [ -h "$PRG" ]; do
    ls -ld "$PRG"
    LINK=$(expr "$PRG" : '.*-> \(.*\)$')
    if expr "$LINK" : '/.*' > /dev/null; then
        PRG="$LINK"
    else
        PRG=$(dirname "$PRG")"/$LINK"
    fi
done

SAVED="$(pwd)"
cd "$(dirname "$PRG")/.." >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

# Download Maven if needed
WRAPPER_JAR="$APP_HOME/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$APP_HOME/.mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Maven wrapper..."
    mkdir -p "$APP_HOME/.mvn/wrapper"
    curl -sL https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar -o "$WRAPPER_JAR"
fi

# Get Maven version
MAVEN_VERSION="3.9.6"
MAVEN_HOME="$APP_HOME/.mvn/apache-maven-$MAVEN_VERSION"

if [ ! -d "$MAVEN_HOME" ]; then
    echo "Downloading Maven $MAVEN_VERSION..."
    mkdir -p "$APP_HOME/.mvn"
    cd "$APP_HOME/.mvn"
    curl -sL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip -o maven.zip
    unzip -q maven.zip
    rm maven.zip
    cd "$SAVED"
fi

# Run Maven
export M2_HOME="$MAVEN_HOME"
export CLASSPATH="$WRAPPER_JAR"

"$M2_HOME/bin/mvn" "$@"


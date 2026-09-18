#!/bin/bash

# Run with: ./build_and_run.sh
# Requires Java 21+ on PATH or installed via Homebrew

# Try Homebrew openjdk@21 first, fallback to system java
if [ -d "/opt/homebrew/opt/openjdk@21/bin" ]; then
    export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
elif [ -d "/usr/local/opt/openjdk@21/bin" ]; then
    export PATH="/usr/local/opt/openjdk@21/bin:$PATH"
fi

mkdir -p target/classes

echo "Compiling..."
find src/main/java -name "*.java" > sources.txt
javac -d target/classes @sources.txt

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Compilation successful. Starting server on port 6379..."
java -cp target/classes com.redisclone.server.RedisServer

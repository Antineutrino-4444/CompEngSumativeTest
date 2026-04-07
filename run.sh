#!/usr/bin/env bash
# run.sh – Launch the game-selector menu (opens all demos)
set -e
cd "$(dirname "$0")/demos"
# Compile if needed
if [ ! -f Launcher.class ]; then
  javac *.java
fi
java Launcher

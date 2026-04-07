#!/usr/bin/env bash
# compile.sh – Compile all demo Java files
set -e
cd "$(dirname "$0")/demos"
echo "Compiling demo files..."
javac *.java
echo "Compilation successful. Run  ./run.sh  to launch the game menu."

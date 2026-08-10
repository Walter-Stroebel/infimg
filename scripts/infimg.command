#!/bin/bash
# macOS start script — double-clickable in Finder (opens in Terminal).
# Assumes "java" is on PATH.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec java -jar "$DIR/target/infimg-1.0-jar-with-dependencies.jar" "$@"

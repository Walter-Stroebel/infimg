#!/bin/bash
# Linux start script. Assumes "java" is on PATH.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec java -jar "$DIR/target/infimg-1.2-jar-with-dependencies.jar" "$@"

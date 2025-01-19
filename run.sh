#!/usr/bin/env bash

set -e

cd "$(dirname "$0")"
BENCHMARK_DIR="$(pwd)"
TIMESTAMP=$(date '+%F_%H%M%S')

# Build Java
./mvnw clean verify

# Build Native
cd "$BENCHMARK_DIR/src/main/native"
make

# run
mkdir -p "$BENCHMARK_DIR/logs"

java_options=(
  --enable-native-access=ALL-UNNAMED
  --add-opens=java.base/java.lang=ALL-UNNAMED
  --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED
  -Xms4g -Xmx4g
  "-Dorg.glavo.benchmark.libpath=$BENCHMARK_DIR/src/main/native/library.so"
)

benchmark_options=(
  -tu ms -f 1 -gc true -wi 5 -w 5 -i 5 -r 5
)

if [ "$JIT_COMPILER" == "C1" ]; then
  java_options+=(-XX:TieredStopAtLevel=1)
elif [ "$JIT_COMPILER" == "NONE" ]; then
  java_options+=(-Xint)
fi

set -x

$JAVA_HOME/bin/java \
  "${java_options[@]}" \
  -jar "$BENCHMARK_DIR/target/benchmarks.jar" \
  -rf json -rff "$BENCHMARK_DIR/logs/benchmark-$TIMESTAMP.json" \
  "${benchmark_options[@]}" "$@" \
  2>&1 | tee "$BENCHMARK_DIR/logs/benchmark-$TIMESTAMP.log"

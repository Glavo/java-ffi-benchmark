#!/usr/bin/env bash

set -e

cd "$(dirname $0)"
BENCHMARK_DIR="$(pwd)"

# Build Java
mvn clean verify

# Build Native
cd "$BENCHMARK_DIR/src/main/native"
rm -rf "cmake-build-release"
mkdir "cmake-build-release"
cd "cmake-build-release"
cmake -DCMAKE_BUILD_TYPE=Release ..
cmake --build .

# run
cd "$BENCHMARK_DIR"
$JAVA_HOME/bin/java \
  --enable-preview \
  --enable-native-access=ALL-UNNAMED \
  -Xms1g -Xmx1g\
  "-Dorg.glavo.benchmark.libpath=$BENCHMARK_DIR/src/main/native/cmake-build-release/libffi-benchmark.so" \
  -jar "$BENCHMARK_DIR/target/benchmarks.jar" \
  -tu ms -wi 5 -w 3 -i 3 -r 3 -f 1 \
  "$@"

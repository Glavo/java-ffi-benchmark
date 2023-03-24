#!/usr/bin/env bash

set -e

cd "$(dirname $0)"

java ./Data.java NoopBenchmark          --save=NoopBenchmark.png
java ./Data.java SysinfoBenchmark       --save=SysinfoBenchmark-0.png       --page=0
java ./Data.java SysinfoBenchmark       --save=SysinfoBenchmark-1.png       --page=1
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-0.png --page=0 --method=getStringFromNative
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-1.png --page=1 --method=getStringFromNative
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-2.png --page=0 --method=passStringToNative
java ./Data.java QSortBenchmark         --save=QSortBenchmark.png

for file in *.png; do cwebp -lossless -q 100 "$file" -o "${file%%.*}.webp"; done
rm ./*.png

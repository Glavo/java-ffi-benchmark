#!/usr/bin/env bash

set -e

cd "$(dirname $0)"

java ./Data.java NoopBenchmark          --save=NoopBenchmark.png
java ./Data.java SysinfoBenchmark       --save=SysinfoBenchmark.png               --page=0
java ./Data.java SysinfoBenchmark       --save=SysinfoBenchmark-no-allocate.png   --page=1
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-c2j.png         --page=0 --method=getStringFromNative
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-c2j-detail.png  --page=1 --method=getStringFromNative
java ./Data.java StringConvertBenchmark --save=StringConvertBenchmark-j2c.png         --page=0 --method=passStringToNative
java ./Data.java QSortBenchmark         --save=QSortBenchmark.png

rm ./*.webp
for file in *.png; do cwebp -lossless -q 100 "$file" -o "${file%%.*}.webp"; done
rm ./*.png

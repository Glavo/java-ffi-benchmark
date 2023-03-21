#include <string.h>

#include "library.h"

// ========= noop =========

void ffi_benchmark_noop() {
    // do nothing
}

void JNICALL Java_benchmark_NoopBenchmark_noop(JNIEnv *env, jclass cls) {
    // do nothing
}

void JNICALL JavaCritical_benchmark_NoopBenchmark_noop_critical() {
    // do nothing
}

// ========= strlen =========

long ffi_benchmark_strlen(const char *str) {
    return strlen(str);
}

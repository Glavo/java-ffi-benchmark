#include "library.h"
#include "library-jni.h"

// ========= noop =========

void ffi_benchmark_noop() {
    // do nothing
}

void JNICALL FFI_BENCHMARK_JNI(noop) (JNIEnv *env, jclass cls) {
    // do nothing
}

void JNICALL FFI_BENCHMARK_JNI_CRITICAL(noop_critical) () {
    // do nothing
}

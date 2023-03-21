#include <jni.h>

#define FFI_BENCHMARK_JNI(x) Java_org_glavo_FFIBenchmark_##x
#define FFI_BENCHMARK_JNI_CRITICAL(x) JavaCritical_org_glavo_FFIBenchmark_##x

#ifdef __cplusplus
extern "C" {
#endif

// ========= noop =========

extern void ffi_benchmark_noop();

/*
 * Class:     benchmark_NoopBenchmark
 * Method:    noop
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_benchmark_NoopBenchmark_noop
        (JNIEnv *, jclass);


JNIEXPORT void JavaCritical_benchmark_NoopBenchmark_noop_critical();

extern long ffi_benchmark_strlen(const char *);

#ifdef __cplusplus
}
#endif
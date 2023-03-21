#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FFI_BENCHMARK_JNI(x) Java_org_glavo_FFIBenchmark_##x
#define FFI_BENCHMARK_JNI_CRITICAL(x) JavaCritical_org_glavo_FFIBenchmark_##x

/*
 * Class:     org_glavo_FFIBenchmark
 * Method:    noop
 * Signature: ()V
 */
JNIEXPORT void JNICALL FFI_BENCHMARK_JNI(noop)
        (JNIEnv *, jclass);

JNIEXPORT void FFI_BENCHMARK_JNI_CRITICAL(noop_critical) ();

#ifdef __cplusplus
}
#endif
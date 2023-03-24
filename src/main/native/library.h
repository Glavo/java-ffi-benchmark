#include <jni.h>

#define FFI_BENCHMARK_JNI(x) Java_org_glavo_FFIBenchmark_##x
#define FFI_BENCHMARK_JNI_CRITICAL(x) JavaCritical_org_glavo_FFIBenchmark_##x

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *);

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

// ========= string convert =========

extern void ffi_benchmark_accept_string(const char *);

extern const char *ffi_benchmark_get_string(jint);

/*
 * Class:     benchmark_StringConvertBenchmark
 * Method:    getString
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_benchmark_StringConvertBenchmark_getString
        (JNIEnv *, jclass, jint);

// ========= strlen =========

extern long ffi_benchmark_strlen(const char *);

// ========= sysinfo =========

extern void ffi_benchmark_sysinfo(struct sysinfo *info);

/*
 * Class:     benchmark_SysinfoBenchmark
 * Method:    getMemUnit
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_benchmark_SysinfoBenchmark_getMemUnit
        (JNIEnv *, jclass);


// ========= qsort =========

/*
 * Class:     benchmark_QSortBenchmark
 * Method:    qsort
 * Signature: (JJ)V
 */
JNIEXPORT void JNICALL Java_benchmark_QSortBenchmark_qsort
        (JNIEnv *, jclass, jlong, jlong);

#ifdef __cplusplus
}
#endif
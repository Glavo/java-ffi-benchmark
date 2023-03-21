#include <string.h>
#include <stdio.h>
#include <stdlib.h>

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

// ========= qsort =========

void ffi_benchmark_qsort(jint *base, jlong numElements, jint (*comparator)(const void *, const void *)) {
    qsort(base, numElements, sizeof(jint), comparator);
}

struct {
    jboolean needToInit;
    JavaVM *vm;
    jobject comparator;
    jmethodID methodId;
} stub = {
    .needToInit = JNI_TRUE,
    .vm = NULL,
    .comparator = NULL,
    .methodId = NULL
};

static int qsortCompare(const void *a, const void *b) {
    JNIEnv *env = NULL;
    (*stub.vm)->AttachCurrentThread(stub.vm, (void **) &env, NULL);

    return (int) (*env)->CallIntMethod(env, stub.comparator, stub.methodId, (jlong) a, (jlong) b);
}

void JNICALL Java_benchmark_QSortBenchmark_qsort(JNIEnv *env, jclass cls, jlong address, jlong elements, jobject comparator) {
    if (stub.needToInit == JNI_TRUE) {
        JavaVM *vm = NULL;
        if ((*env)->GetJavaVM(env, &vm) != 0) {
            fprintf(stderr, "Failed to get vm");
            exit(1);
        }

        jclass ccls = (*env)->GetObjectClass(env, comparator);
        if (ccls == NULL) {
            fprintf(stderr, "Failed to find class");
            exit(1);
        }

        jmethodID methodId = (*env)->GetMethodID(env, ccls, "invoke", "(JJ)I");
        if (methodId == NULL) {
            fprintf(stderr, "Failed to find method");
            exit(1);
        }


        stub.vm = vm;
        stub.comparator = (*env)->NewGlobalRef(env, comparator);
        stub.methodId = methodId;

        stub.needToInit = JNI_FALSE;
    }

    qsort((void *) address, elements, sizeof(jint), qsortCompare);
}

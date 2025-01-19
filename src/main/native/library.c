#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#include <sys/sysinfo.h>

#include "library.h"

// ========= noop =========

void ffi_benchmark_noop() {
    // do nothing
}

void JNICALL Java_benchmark_NoopBenchmark_noop(JNIEnv *env, jclass cls) {
    // do nothing
}

// ========= string convert =========

void ffi_benchmark_accept_string(const char *str) {
    // do nothing
}

static char *get_string_table[4096] = {0};

const char *ffi_benchmark_get_string(jint length) {
    if (length == 0) {
        return "";
    }

    char *res = get_string_table[length];
    if (res == NULL) {
        res = malloc(length + 1);
        for (int i = 0; i < length; ++i) {
            res[i] = (char) ((i % 26) + 'A');
        }
        res[length] = '\0';
        get_string_table[length] = res;
    }

    return res;
}

jstring Java_benchmark_StringConvertBenchmark_getString(JNIEnv *env, jclass cls, jint length) {
    const char *str = ffi_benchmark_get_string(length);
    return (*env)->NewStringUTF(env, str);
}

// ========= strlen =========

long ffi_benchmark_strlen(const char *str) {
    return strlen(str);
}

// ========= sysinfo =========

void ffi_benchmark_sysinfo(struct sysinfo *info) {
    sysinfo(info);
}

jint Java_benchmark_SysinfoBenchmark_getMemUnit(JNIEnv *env, jclass cls) {
    struct sysinfo info;
    sysinfo(&info);
    return (jint) info.mem_unit;
}


// ========= qsort =========

void ffi_benchmark_qsort(jint *base, jlong numElements, jint (*comparator)(const void *, const void *)) {
    qsort(base, numElements, sizeof(jint), comparator);
}

struct {
    JavaVM *vm;
    jclass  cls;
    jmethodID methodId;
} stub = {
    .vm = NULL,
    .cls = NULL,
    .methodId = NULL
};

static int qsortCompare(const void *a, const void *b) {
    JNIEnv *env = NULL;
    (*stub.vm)->AttachCurrentThread(stub.vm, (void **) &env, NULL);

    return (int) (*env)->CallStaticIntMethod(env, stub.cls, stub.methodId, (jlong) a, (jlong) b);
}

void JNICALL Java_benchmark_QSortBenchmark_qsort(JNIEnv *env, jclass cls, jlong address, jlong elements) {
    if (stub.vm == NULL) {
        JavaVM *vm = NULL;
        if ((*env)->GetJavaVM(env, &vm) != 0) {
            fprintf(stderr, "Failed to get vm");
            exit(1);
        }

        jmethodID methodId = (*env)->GetStaticMethodID(env, cls, "qsortCompare", "(JJ)I");
        if (methodId == NULL) {
            fprintf(stderr, "Failed to find method");
            exit(1);
        }

        stub.cls = (*env)->NewGlobalRef(env, cls);
        stub.methodId = methodId;

        stub.vm = vm;
    }

    qsort((void *) address, elements, sizeof(jint), qsortCompare);
}

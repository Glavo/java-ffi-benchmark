package benchmark;

import com.sun.jna.Library;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static benchmark.Helper.downcallHandle;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

@State(Scope.Benchmark)
public class StringConvertBenchmark {
    public interface NativeLib extends Library {
        void ffi_benchmark_accept_string(String str);

        String ffi_benchmark_get_string(int length);
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_accept_string(String str);

        public static native String ffi_benchmark_get_string(int length);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static native String getString(int length);

    private static final NativeLib JNA = Helper.loadJna(NativeLib.class);
    private static final NativeLib JNR = Helper.loadJnr(NativeLib.class);

    private static final MethodHandle acceptString = downcallHandle("ffi_benchmark_accept_string", FunctionDescriptor.ofVoid(ADDRESS), false);
    private static final MethodHandle acceptStringTrivial = downcallHandle("ffi_benchmark_accept_string", FunctionDescriptor.ofVoid(ADDRESS), true);

    private static final MethodHandle getString = downcallHandle("ffi_benchmark_get_string", FunctionDescriptor.of(ADDRESS, JAVA_INT), false);
    private static final MethodHandle getStringTrivial = downcallHandle("ffi_benchmark_get_string", FunctionDescriptor.of(ADDRESS, JAVA_INT), true);

    @Param({"0", "16", "64", "256", "1024", "4096"})
    int length;

    String testString;

    @Setup
    public void setup() {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ((i % 26) + 'A'));
        }
        testString = builder.toString();
    }

    @Benchmark
    public void passStringToNativeJna() {
        JNA.ffi_benchmark_accept_string(testString);
    }

    @Benchmark
    public void passStringToNativeJnaDirect() {
        JnaDirect.ffi_benchmark_accept_string(testString);
    }

    @Benchmark
    public void passStringToNativeJnr() {
        JNR.ffi_benchmark_accept_string(testString);
    }

    @Benchmark
    public void passStringToNativePanama() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            acceptString.invokeExact(arena.allocateUtf8String(testString));
        }
    }

    @Benchmark
    public void passStringToNativePanamaTrivial() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            acceptStringTrivial.invokeExact(arena.allocateUtf8String(testString));
        }
    }

    @Benchmark
    public String getStringFromNativeJni() {
        return getString(length);
    }

    @Benchmark
    public String getStringFromNativeJna() {
        return JNA.ffi_benchmark_get_string(length);
    }

    @Benchmark
    public String getStringFromNativeJnaDirect() {
        return JnaDirect.ffi_benchmark_get_string(length);
    }

    @Benchmark
    public String getStringFromNativeJnr() {
        return JNR.ffi_benchmark_get_string(length);
    }

    @Benchmark
    public String getStringFromNativePanama() throws Throwable {
        return ((MemorySegment) getString.invokeExact(length)).reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    @Benchmark
    public String getStringFromNativePanamaTrivial() throws Throwable {
        return ((MemorySegment) getStringTrivial.invokeExact(length)).reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }
}

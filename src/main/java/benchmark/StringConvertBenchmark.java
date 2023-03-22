package benchmark;

import com.sun.jna.Library;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.function.Consumer;

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

    private static String testStr(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ((i % 26) + 'A'));
        }
         return builder.toString();
    }

    @Setup
    public void setup() {
        testString = testStr(length);
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

    public static void main(String[] args) throws Throwable {
        int[] lengths = {0, 16, 64, 256, 1024, 4096};
        for (int length : lengths) {
            System.out.println("# length = " + length);

            StringConvertBenchmark benchmark = new StringConvertBenchmark();
            benchmark.length = length;

            benchmark.setup();

            System.out.println("=> Running passStringToNativeJna");
            benchmark.passStringToNativeJna();

            System.out.println("=> Running passStringToNativeJnaDirect");
            benchmark.passStringToNativeJnaDirect();

            System.out.println("=> Running passStringToNativeJnr");
            benchmark.passStringToNativeJnr();

            System.out.println("=> Running passStringToNativePanama");
            benchmark.passStringToNativePanama();

            System.out.println("=> Running passStringToNativePanamaTrivial");
            benchmark.passStringToNativePanamaTrivial();

            String expect = testStr(length);
            Consumer<String> checker = v -> {
                if (!expect.equals(v)) {
                    throw new AssertionError("expect: " + expect + ", actual: " + v);
                }
            };

            System.out.println("=> Running getStringFromNativeJni");
            checker.accept(benchmark.getStringFromNativeJni());

            System.out.println("=> Running getStringFromNativeJna");
            checker.accept(benchmark.getStringFromNativeJna());

            System.out.println("=> Running getStringFromNativeJnaDirect");
            checker.accept(benchmark.getStringFromNativeJnaDirect());

            System.out.println("=> Running getStringFromNativeJnr");
            checker.accept(benchmark.getStringFromNativeJnr());

            System.out.println("=> Running getStringFromNativePanama");
            checker.accept(benchmark.getStringFromNativePanama());

            System.out.println("=> Running getStringFromNativePanamaTrivial");
            checker.accept(benchmark.getStringFromNativePanamaTrivial());
        }
    }
}

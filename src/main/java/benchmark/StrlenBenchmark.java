package benchmark;

import com.sun.jna.Library;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Random;

import static benchmark.Helper.downcallHandle;

@State(Scope.Benchmark)
public class StrlenBenchmark {

    public interface NativeLib extends Library {
        long ffi_benchmark_strlen(String str);
    }

    public interface JnaLib extends Library {
        long ffi_benchmark_strlen(com.sun.jna.Pointer str);
    }

    public interface JnrLib {
        long ffi_benchmark_strlen(jnr.ffi.Pointer str);
    }

    private static final class JnaDirect {
        public static native long ffi_benchmark_strlen(String str);
    }

    private static final class JnaDirectNoAllocate {
        public static native long ffi_benchmark_strlen(com.sun.jna.Pointer str);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
        Helper.registerJnaDirect(JnaDirectNoAllocate.class);
    }

    private static final NativeLib JNA = Helper.loadJna(NativeLib.class);
    private static final NativeLib JNR = Helper.loadJnr(NativeLib.class);

    private static final JnaLib JNA_NO_ALLOCATE = Helper.loadJna(JnaLib.class);
    private static final JnrLib JNR_NO_ALLOCATE = Helper.loadJnr(JnrLib.class);

    private static final MethodHandle strlen = downcallHandle("ffi_benchmark_strlen", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), false);
    private static final MethodHandle strlenTrivial = downcallHandle("ffi_benchmark_strlen", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), true);

    @Param(value = {"0", "16", "64", "256", "4096"})
    int length;

    String testString;

    final Arena benchmarkArena = Arena.ofConfined();
    MemorySegment testStringSegment;
    long testStringAddress;
    com.sun.jna.Pointer testStringJnaPointer;
    jnr.ffi.Pointer testStringJnrPointer;

    @Setup
    public void setup() {
        Random random = new Random(0);

        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ((random.nextInt() % 26) + 'A'));
        }
        testString = builder.toString();
        testStringSegment = benchmarkArena.allocateUtf8String(testString);
        testStringAddress = testStringSegment.address();
        testStringJnaPointer = new com.sun.jna.Pointer(testStringAddress);
        testStringJnrPointer = jnr.ffi.Runtime.getSystemRuntime().getMemoryManager().newPointer(testStringAddress);
    }

    @TearDown
    public void cleanup() {
        benchmarkArena.close();

        testStringSegment = null;
        testStringAddress = 0L;
        testStringJnaPointer = null;
        testStringJnrPointer = null;
    }

    @Benchmark
    public long strlenJna() {
        return JNA.ffi_benchmark_strlen(testString);
    }

    @Benchmark
    public long strlenJnaNoAllocate() {
        return JNA_NO_ALLOCATE.ffi_benchmark_strlen(testStringJnaPointer);
    }

    @Benchmark
    public long strlenJnaDirect() {
        return JnaDirect.ffi_benchmark_strlen(testString);
    }

    @Benchmark
    public long strlenJnaDirectNoAllocate() {
        return JnaDirectNoAllocate.ffi_benchmark_strlen(testStringJnaPointer);
    }

    @Benchmark
    public long strlenJnr() {
        return JNR.ffi_benchmark_strlen(testString);
    }

    @Benchmark
    public long strlenJnrNoAllocate() {
        return JNR_NO_ALLOCATE.ffi_benchmark_strlen(testStringJnrPointer);
    }

    @Benchmark
    public long strlenPanama() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (long) strlen.invokeExact(arena.allocateUtf8String(testString));
        }
    }

    @Benchmark
    public long strlenPanamaNoAllocate() throws Throwable {
        return (long) strlen.invokeExact(testStringSegment);
    }

    @Benchmark
    public long strlenPanamaTrivial() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (long) strlenTrivial.invokeExact(arena.allocateUtf8String(testString));
        }
    }

    @Benchmark
    public long strlenPanamaTrivialNoAllocate() throws Throwable {
        return (long) strlenTrivial.invokeExact(testStringSegment);
    }
}

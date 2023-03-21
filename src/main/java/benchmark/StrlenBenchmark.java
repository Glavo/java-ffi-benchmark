package benchmark;

import com.sun.jna.Library;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import static benchmark.Helper.downcallHandle;

@State(Scope.Benchmark)
public class StrlenBenchmark {

    public interface NativeLib extends Library {
        long ffi_benchmark_strlen(String str);
    }

    private static final class JnaDirect {
        public static native long ffi_benchmark_strlen(String str);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static final String TEST_STRING = "Hello, world! Hello, Panama!";

    private static final NativeLib JNA = Helper.loadJna(NativeLib.class);
    private static final NativeLib JNR = Helper.loadJnr(NativeLib.class);

    private static final MethodHandle strlen = downcallHandle("ffi_benchmark_strlen", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), false);
    private static final MethodHandle strlenTrivial = downcallHandle("ffi_benchmark_strlen", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS), true);

    @Benchmark
    public long strlenJna() {
        return JNA.ffi_benchmark_strlen(TEST_STRING);
    }

    @Benchmark
    public long strlenJnaDirect() {
        return JnaDirect.ffi_benchmark_strlen(TEST_STRING);
    }

    @Benchmark
    public long strlenJnr() {
        return JNR.ffi_benchmark_strlen(TEST_STRING);
    }

    @Benchmark
    public long strlenPanama() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (long) strlen.invokeExact(arena.allocateUtf8String(TEST_STRING));
        }
    }

    @Benchmark
    public long strlenPanamaTrivial() throws Throwable {
        try (var arena = Arena.ofConfined()) {
            return (long) strlenTrivial.invokeExact(arena.allocateUtf8String(TEST_STRING));
        }
    }
}

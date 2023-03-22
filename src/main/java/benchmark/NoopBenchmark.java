package benchmark;

import com.sun.jna.Library;
import org.openjdk.jmh.annotations.Benchmark;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodHandle;

import static benchmark.Helper.*;

public class NoopBenchmark {

    public interface NativeLib extends Library {
        void ffi_benchmark_noop();
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_noop();
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static native void noop();
    private static native void noop_critical();

    private static final NativeLib JNA = Helper.loadJna(NativeLib.class);
    private static final NativeLib JNR = Helper.loadJnr(NativeLib.class);
    private static final NativeLib JNR_IGNORE_ERROR = Helper.loadJnrIgnoreError(NativeLib.class);

    private static final MethodHandle noop = downcallHandle("ffi_benchmark_noop", FunctionDescriptor.ofVoid(), false);
    private static final MethodHandle noopTrivial = downcallHandle("ffi_benchmark_noop", FunctionDescriptor.ofVoid(), true);

    @Benchmark
    public void noopJni() {
        noop();
    }

    // @Benchmark
    public void noopJniCritical() {
        noop_critical();
    }

    @Benchmark
    public void noopJna() {
        JNA.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopJnaDirect() {
        JnaDirect.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopJnr() {
        JNR.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopJnrIgnoreError() {
        JNR_IGNORE_ERROR.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopPanama() throws Throwable {
        noop.invokeExact();
    }

    @Benchmark
    public void noopPanamaTrivial() throws Throwable {
        noopTrivial.invokeExact();
    }

    public static void main(String[] args) throws Throwable {
        NoopBenchmark benchmark = new NoopBenchmark();

        System.out.println("=> Running noopJni");
        benchmark.noopJni();

        // benchmark.noopJniCritical();

        System.out.println("=> Running noopJna");
        benchmark.noopJna();

        System.out.println("=> Running noopJnaDirect");
        benchmark.noopJnaDirect();

        System.out.println("=> Running noopJnr");
        benchmark.noopJnr();

        System.out.println("=> Running noopJnrIgnoreError");
        benchmark.noopJnrIgnoreError();

        System.out.println("=> Running noopPanama");
        benchmark.noopPanama();

        System.out.println("=> Running noopPanamaTrivial");
        benchmark.noopPanamaTrivial();
    }
}

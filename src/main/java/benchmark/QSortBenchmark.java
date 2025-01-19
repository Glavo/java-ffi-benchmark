package benchmark;

import com.sun.jna.Callback;
import com.sun.jna.Library;

import jnr.ffi.annotations.Delegate;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static benchmark.Helper.UNSAFE;
import static benchmark.Helper.downcallHandle;

import static java.lang.foreign.ValueLayout.*;

@State(Scope.Benchmark)
public class QSortBenchmark {
    public interface JnaLib extends Library {
        interface QSortComparator extends Callback {
            QSortComparator INSTANCE = (a, b) -> Integer.compare(a.getInt(0), b.getInt(0));

            int invoke(com.sun.jna.Pointer a, com.sun.jna.Pointer b);
        }

        void ffi_benchmark_qsort(com.sun.jna.Pointer data, long elements, JnaLib.QSortComparator comparator);
    }

    public interface JnrLib {
        interface QSortComparator {
            QSortComparator INSTANCE = (a, b) -> Integer.compare(a.getInt(0), b.getInt(0));

            @Delegate
            int invoke(jnr.ffi.Pointer a, jnr.ffi.Pointer b);
        }

        void ffi_benchmark_qsort(jnr.ffi.Pointer data, long elements, JnrLib.QSortComparator comparator);
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_qsort(com.sun.jna.Pointer data, long elements, JnaLib.QSortComparator comparator);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static final JnaLib JNA = Helper.loadJna(JnaLib.class);
    private static final JnrLib JNR = Helper.loadJnr(JnrLib.class);
    private static final JnrLib JNR_IGNORE_ERROR = Helper.loadJnrIgnoreError(JnrLib.class);

    private static native void qsort(long address, long elements);

    private static int qsortCompare(long a, long b) {
        return Integer.compare(UNSAFE.getInt(a), UNSAFE.getInt(b));
    }

    private static int qsortCompare(MemorySegment elem1, MemorySegment elem2) {
        return Integer.compare(elem1.get(JAVA_INT, 0), elem2.get(JAVA_INT, 0));
    }

    private static final MethodHandle qsort = downcallHandle("ffi_benchmark_qsort",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, ADDRESS),
            false);

    private static final MemorySegment qsortComparator = qsortComparator(Arena.global());

    private static MemorySegment qsortComparator(Arena arena) {
        try {
            MethodHandle compareHandle = MethodHandles.lookup()
                    .findStatic(QSortBenchmark.class, "qsortCompare",
                            MethodType.methodType(int.class,
                                    MemorySegment.class,
                                    MemorySegment.class));

            return Helper.upcallStub(compareHandle, FunctionDescriptor.of(JAVA_INT,
                            ADDRESS.withTargetLayout(JAVA_INT),
                            ADDRESS.withTargetLayout(JAVA_INT)),
                    arena);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @Param({"0", "8", "16", "32", "64", "128"})
    long length;

    Arena benchmarkArena;
    MemorySegment segment;
    long address;
    com.sun.jna.Pointer jnaPointer;
    jnr.ffi.Pointer jnrPointer;

    @Setup
    public void setup() {
        benchmarkArena = Arena.ofConfined();

        segment = benchmarkArena.allocate(MemoryLayout.sequenceLayout(length, JAVA_INT));
        for (int i = 0; i < length; i++) {
            segment.set(JAVA_INT, i * 4L, i);
        }

        address = segment.address();
        jnaPointer = new com.sun.jna.Pointer(address);
        jnrPointer = jnr.ffi.Runtime.getSystemRuntime().getMemoryManager().newPointer(address);
    }

    @TearDown
    public void cleanup() {
        benchmarkArena.close();
        benchmarkArena = null;

        address = 0L;
        segment = null;
        jnaPointer = null;
        jnrPointer = null;
    }

    @Benchmark
    public void qsortJni() {
        qsort(address, length);
    }

    @Benchmark
    public void qsortJna() {
        JNA.ffi_benchmark_qsort(jnaPointer, length, JnaLib.QSortComparator.INSTANCE);
    }

    @Benchmark
    public void qsortJnaDirect() {
        JnaDirect.ffi_benchmark_qsort(jnaPointer, length, JnaLib.QSortComparator.INSTANCE);
    }

    @Benchmark
    public void qsortJnr() {
        JNR.ffi_benchmark_qsort(jnrPointer, length, JnrLib.QSortComparator.INSTANCE);
    }

    @Benchmark
    public void qsortJnrIgnoreError() {
        JNR_IGNORE_ERROR.ffi_benchmark_qsort(jnrPointer, length, JnrLib.QSortComparator.INSTANCE);
    }

    @Benchmark
    public void qsortPanama() throws Throwable {
        qsort.invokeExact(segment, length, qsortComparator);
    }

    // @Benchmark
    public void qsortPanamaSlow() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            qsort.invokeExact(segment, length, qsortComparator(arena));
        }
    }

    private void assertStatus() {
        for (int i = 0; i < length; i++) {
            if (segment.get(JAVA_INT, i * 4L) != i) {
                throw new AssertionError("Invalid value at index " + i);
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        int[] lengths = {0, 16, 32, 64};

        for (int length : lengths) {
            System.out.println("# length = " + length);

            QSortBenchmark benchmark = new QSortBenchmark();
            benchmark.length = length;
            benchmark.setup();

            try {
                System.out.println("=> Running qsortJni");
                benchmark.qsortJni();
                benchmark.assertStatus();

                System.out.println("=> Running qsortJna");
                benchmark.qsortJna();
                benchmark.assertStatus();

                System.out.println("=> Running qsortJnaDirect");
                benchmark.qsortJnaDirect();
                benchmark.assertStatus();

                System.out.println("=> Running qsortJnr");
                benchmark.qsortJnr();
                benchmark.assertStatus();

                System.out.println("=> Running qsortJnrIgnoreError");
                benchmark.qsortJnrIgnoreError();
                benchmark.assertStatus();

                System.out.println("=> Running qsortPanama");
                benchmark.qsortPanama();
                benchmark.assertStatus();

                System.out.println("=> Running qsortPanamaSlow");
                benchmark.qsortPanamaSlow();
                benchmark.assertStatus();
            } finally {
                benchmark.cleanup();
            }
        }
    }
}

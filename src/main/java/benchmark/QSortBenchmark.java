package benchmark;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import jnr.ffi.annotations.Delegate;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
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

    private interface JniComparator {
        int invoke(long a, long b);

        JniComparator INSTANCE = (a, b) -> Integer.compare(UNSAFE.getInt(a), UNSAFE.getInt(b));
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_qsort(com.sun.jna.Pointer data, long elements, JnaLib.QSortComparator comparator);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static final JnaLib JNA = Helper.loadJna(JnaLib.class);
    private static final JnrLib JNR = Helper.loadJnr(JnrLib.class);

    private static native void qsort(long address, long elements, JniComparator comparator);

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

    //@Param({"0", "32", "1024"})
    @Param({"0", "32"})
    long length;

    Arena benchmarkArena;
    MemorySegment segment;
    long address;
    com.sun.jna.Pointer jnaPointer;
    jnr.ffi.Pointer jnrPointer;

    @Setup
    public void setup() {
        benchmarkArena = Arena.ofConfined();

        segment = benchmarkArena.allocateArray(JAVA_INT, length);
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
        qsort(address, length, JniComparator.INSTANCE);
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
    public void qsortPanama() throws Throwable {
        qsort.invokeExact(segment, length, qsortComparator);
    }

    // @Benchmark
    public void qsortPanamaSlow() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            qsort.invokeExact(segment, length, qsortComparator(arena));
        }
    }
}
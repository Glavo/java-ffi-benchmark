package benchmark;

import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

@State(Scope.Benchmark)
public class MemoryFillBenchmark {
    private static final jdk.internal.misc.Unsafe UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();

    private static final MethodHandle memset = Linker.nativeLinker().downcallHandle(
            Linker.nativeLinker().defaultLookup().find("memset").get(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    );

    private final Arena arena = Arena.ofConfined();
    private MemorySegment segment;

    @Param({"0", "16", "32", "64", "128", "256", "512"})
    private long length;

    @Setup
    public void setup() {
        segment = arena.allocate(length);
    }

    @TearDown
    public void cleanup() {
        segment = null;
        arena.close();
    }

    @Benchmark
    public void fill() {
        segment.fill((byte) 0);
    }

    @Benchmark
    public void setMemory() {
        UNSAFE.setMemory(segment.address(), length, (byte) 0);
    }

    @Benchmark
    public void memset() throws Throwable {
        memset.invokeExact(segment, 0, length);
    }

    @Benchmark
    public void loop() throws Throwable {
        MemorySegment segment = this.segment;
        for (int i = 0; i < length; i++) {
            segment.setAtIndex(ValueLayout.JAVA_BYTE, i, (byte) 0);
        }
    }

    @Benchmark
    public void loopLong() throws Throwable {
        MemorySegment segment = this.segment;
        for (int i = 0, limit = (int) length / Long.BYTES; i < limit; i++) {
            segment.setAtIndex(ValueLayout.JAVA_LONG, i, 0L);
        }
    }
}

package benchmark.experimental;

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

    private Arena arena;
    private MemorySegment segment;

    @Param({"0", "16", "32", "64", "128", "256", "512"})
    private long length;

    @Setup
    public void setup() {
        arena = Arena.ofConfined();
        segment = arena.allocate(length);
    }

    @TearDown
    public void cleanup() {
        arena.close();
        arena = null;
        segment = null;
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
    public void loop() {
        MemorySegment segment = this.segment;
        for (int i = 0; i < length; i++) {
            segment.set(ValueLayout.JAVA_BYTE, i, (byte) 0);
        }
    }

    @Benchmark
    public void loopLong() {
        fill(segment, (byte) 0);
    }

    private static void fill(MemorySegment segment, byte value) {
        long length = segment.byteSize();

        long longValue = value & 0xff;
        longValue = longValue
                | (longValue << 8)
                | (longValue << 16)
                | (longValue << 24)
                | (longValue << 32)
                | (longValue << 40)
                | (longValue << 48)
                | (longValue << 56);

        long baseOffset = 0;
//        if (segment.address() % Long.BYTES == 0) {
//            baseOffset = 0;
//        } else {
//            baseOffset = Long.BYTES - (segment.address() % Long.BYTES);
//            for (long offset = 0; offset < baseOffset; offset++) {
//                segment.set(ValueLayout.JAVA_BYTE, offset, (byte) 0);
//            }
//        }

        // long end = length - ((length - baseOffset) % Long.BYTES);
        long end = length;

        long offset = baseOffset;
        for (; offset < end; offset += Long.BYTES) {
            segment.set(ValueLayout.JAVA_LONG, offset, 0L);
        }

        while (offset < length) {
            segment.set(ValueLayout.JAVA_BYTE, offset, (byte) 0);
            offset++;
        }
    }
}

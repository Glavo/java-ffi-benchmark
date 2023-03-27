package benchmark.experimental;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Cleaner;
import java.util.Arrays;

public final class NativeStack implements SegmentAllocator, AutoCloseable {

    private static final long STACK_SIZE = Long.getLong("stackSize", 1024 * 1024);

    private static final ThreadLocal<NativeStack> threadStack = ThreadLocal.withInitial(NativeStack::new);
    private static final Cleaner CLEANER = Cleaner.create();

    private static final MethodHandle memset = Linker.nativeLinker().downcallHandle(
            Linker.nativeLinker().defaultLookup().find("memset").get(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG)
    );

    private static void memset(MemorySegment segment, int c, long n) {
        try {
            memset.invokeExact(segment, c, n);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }


    @SuppressWarnings("FieldCanBeLocal")
    private final Arena arena;
    private final Thread thread;

    private final MemorySegment segment;
    private final long base;
    private long offset = 0L;
    private long[] offsetRecord = new long[16];
    private int offsetRecordIndex = 0;

    private NativeStack() {
        arena = Arena.ofConfined();
        CLEANER.register(this, arena::close);

        thread = Thread.currentThread();
        segment = arena.allocate(STACK_SIZE);
        base = segment.address();
    }

    public static NativeStack getStack() {
        return threadStack.get();
    }

    public static NativeStack pushStack() {
        return threadStack.get().push();
    }

    private void checkThread() {
        if (Thread.currentThread() != thread) {
            throw new IllegalStateException("Not on the thread of the native stack");
        }
    }

    public NativeStack push() {
        checkThread();

        if (offsetRecordIndex == offsetRecord.length) {
            offsetRecord = Arrays.copyOf(offsetRecord, offsetRecordIndex * 2);
        }

        offsetRecord[offsetRecordIndex++] = offset;
        return this;
    }

    @Override
    public void close() {
        checkThread();

        if (offsetRecordIndex == 0) {
            throw new IllegalStateException("Stack is empty");
        }

        long prevOffset = offsetRecord[--offsetRecordIndex];
        // Do we need to clean up?
        // segment.asSlice(prevOffset, offset - prevOffset).fill((byte) 0);
        // or
        // memset(segment.asSlice(prevOffset), 0, offset - prevOffset);

        offset = prevOffset;
    }

    private static void checkAllocationSizeAndAlign(long byteSize, long byteAlignment) {
        // size should be >= 0
        if (byteSize < 0) {
            throw new IllegalArgumentException("Invalid allocation size : " + byteSize);
        }

        // alignment should be > 0, and power of two
        if (byteAlignment <= 0 || ((byteAlignment & (byteAlignment - 1)) != 0L)) {
            throw new IllegalArgumentException("Invalid alignment constraint : " + byteAlignment);
        }
    }

    private static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        checkThread();
        checkAllocationSizeAndAlign(byteSize, byteAlignment);

        long start = alignUp(base + offset, byteAlignment) - base;
        MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
        offset = start + byteSize;
        return slice;
    }
}

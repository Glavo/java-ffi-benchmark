package benchmark;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Cleaner;
import java.util.Arrays;

public final class NativeStack implements SegmentAllocator, AutoCloseable {

    private static final int STACK_SIZE = 1024 * 1024;

    private static final ThreadLocal<NativeStack> threadStack = ThreadLocal.withInitial(NativeStack::new);
    private static final Cleaner CLEANER = Cleaner.create();

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
        CLEANER.register(arena, arena::close);

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

    public NativeStack push() {
        if (offsetRecordIndex == offsetRecord.length) {
            offsetRecord = Arrays.copyOf(offsetRecord, offsetRecordIndex * 2);
        }

        offsetRecord[offsetRecordIndex++] = offset;
        return this;
    }

    @Override
    public void close() {
        if (offsetRecordIndex == 0) {
            throw new IllegalStateException("Stack is empty");
        }

        offset = offsetRecord[--offsetRecordIndex];
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
        checkAllocationSizeAndAlign(byteSize, byteAlignment);

        long start = alignUp(base + offset, byteAlignment) - base;
        MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
        offset = start + byteSize;
        return slice;
    }
}

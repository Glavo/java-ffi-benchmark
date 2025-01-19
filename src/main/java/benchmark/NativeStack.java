package benchmark;

import java.lang.foreign.*;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public final class NativeStack implements SegmentAllocator, AutoCloseable {

    private static final long STACK_SIZE = Long.getLong("NativeStack.stackSize", 1024 * 1024);
    private static final int CACHE_LIMIT = Integer.getInteger("NativeStack.cacheLimit", Integer.max(Runtime.getRuntime().availableProcessors(), 8));

    private static final ThreadLocal<NativeStack> threadStack = new ThreadLocal<>();
    private static final Cleaner CLEANER = Cleaner.create();

    /*
     * Cache native stacks for virtual threads
     */
    private static final ReentrantLock cachePoolLock = new ReentrantLock();
    private static final ArrayList<NativeStack> cachePool = new ArrayList<>(CACHE_LIMIT);
    private static final Arena sharedArena = Arena.ofAuto();

    private final boolean shared;
    private Thread owner;

    private final MemorySegment segment;
    private long offset = 0L;
    private long[] frames = new long[8];
    private int frameIndex = 0;

    private NativeStack(Thread owner, MemorySegment segment, boolean shared) {
        this.owner = owner;
        this.segment = segment;
        this.shared = shared;
    }

    public static NativeStack getStack() {
        NativeStack stack = threadStack.get();
        if (stack != null) {
            return stack;
        }

        Thread thread = Thread.currentThread();
        if (thread.isVirtual()) {
            cachePoolLock.lock();
            try {
                if (cachePool.isEmpty()) {
                    stack = new NativeStack(thread, sharedArena.allocate(STACK_SIZE), true);
                } else {
                    stack = cachePool.removeLast();
                    stack.changeOwner(thread);
                }
            } finally {
                cachePoolLock.unlock();
            }
        } else {
            //noinspection resource
            Arena arena = Arena.ofConfined();
            stack = new NativeStack(thread, arena.allocate(STACK_SIZE), false);
            CLEANER.register(stack, arena::close);
        }

        threadStack.set(stack);
        return stack;
    }

    public static NativeStack pushStack() {
        return getStack().push();
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) {
            throw new WrongThreadException("Not on the thread of the native stack");
        }
    }

    private void changeOwner(Thread owner) {
        assert shared;
        this.owner = owner;
    }

    public NativeStack push() {
        checkThread();

        if (frameIndex == frames.length) {
            frames = Arrays.copyOf(frames, frameIndex * 2);
        }

        frames[frameIndex++] = offset;
        return this;
    }

    @Override
    public void close() {
        checkThread();

        int prevIndex = frameIndex - 1;
        if (prevIndex < 0) {
            throw new IllegalStateException("Stack is empty");
        }

        offset = frames[prevIndex];
        frameIndex = prevIndex;

        if (prevIndex == 0 && shared) {
            threadStack.set(null);
            this.owner = null;
            cachePoolLock.lock();
            try {
                if (cachePool.size() < CACHE_LIMIT) {
                    cachePool.addLast(this);
                }
            } finally {
                cachePoolLock.unlock();
            }
        }
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

        long address = segment.address();
        long start = alignUp(address + offset, byteAlignment) - address;
        MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
        offset = start + byteSize;
        return slice;
    }
}

package benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.lang.foreign.Arena;

@State(Scope.Benchmark)
public class NativeStackBenchmark {

    private final NativeStack threadStack = NativeStack.getStack();

    @Benchmark
    public void confinedArena() {
        try (Arena arena = Arena.ofConfined()) {
            arena.allocate(32);
        }
    }

    @Benchmark
    public void nativeStack() {
        try (NativeStack stack = NativeStack.pushStack()) {
            stack.allocate(32);
        }
    }

    @Benchmark
    public void cachedNativeStack() {
        try (NativeStack stack = threadStack.push()) {
            stack.allocate(32);
        }
    }
}

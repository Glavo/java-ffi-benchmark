package benchmark;

import com.sun.jna.Library;
import com.sun.jna.platform.linux.LibC;
import jnr.ffi.annotations.Out;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

@State(Scope.Benchmark)
public class SysinfoBenchmark {

    private static final MemoryLayout sysinfoLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("uptime"),
            MemoryLayout.sequenceLayout(3, ValueLayout.JAVA_LONG).withName("loads"),
            ValueLayout.JAVA_LONG.withName("totalram"),
            ValueLayout.JAVA_LONG.withName("freeram"),
            ValueLayout.JAVA_LONG.withName("sharedram"),
            ValueLayout.JAVA_LONG.withName("bufferram"),
            ValueLayout.JAVA_LONG.withName("totalswap"),
            ValueLayout.JAVA_LONG.withName("freeswap"),
            ValueLayout.JAVA_SHORT.withName("procs").withBitAlignment(64),
            MemoryLayout.paddingLayout(48),
            ValueLayout.JAVA_LONG.withName("totalhigh").withBitAlignment(64),
            ValueLayout.JAVA_LONG.withName("freehigh"),
            ValueLayout.JAVA_INT.withName("mem_unit"),
            MemoryLayout.paddingLayout(32).withName("_f")
    ).withName("sysinfo");

    public static final class JnrSysInfo extends jnr.ffi.Struct {
        public final Signed64 uptime = new Signed64();
        public final Unsigned64[] loads = array(new Unsigned64[3]);
        public final Unsigned64 totalram = new Unsigned64();
        public final Unsigned64 freeram = new Unsigned64();
        public final Unsigned64 sharedram = new Unsigned64();
        public final Unsigned64 bufferram = new Unsigned64();
        public final Unsigned64 totalswap = new Unsigned64();
        public final Unsigned64 freeswap = new Unsigned64();
        public final Unsigned16 procs = new Unsigned16();
        public final Unsigned64 totalhigh = new Unsigned64();
        public final Unsigned64 freehigh = new Unsigned64();
        public final Unsigned32 mem_unit = new Unsigned32();

        JnrSysInfo(jnr.ffi.Runtime runtime) {
            super(runtime, new Alignment(8));
        }
    }

    public interface JnaLib extends Library {
        void ffi_benchmark_sysinfo(com.sun.jna.platform.linux.LibC.Sysinfo info);
    }

    public interface JnrLib extends Library {
        void ffi_benchmark_sysinfo(@Out JnrSysInfo info);
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_sysinfo(com.sun.jna.platform.linux.LibC.Sysinfo info);
    }

    static {
        Helper.registerJnaDirect(JnaDirect.class);
    }

    private static native int getMemUnit();

    private static final JnaLib JNA = Helper.loadJna(JnaLib.class);
    private static final JnrLib JNR = Helper.loadJnr(JnrLib.class);
    private static final JnrLib JNR_IGNORE_ERROR = Helper.loadJnrIgnoreError(JnrLib.class);

    private static final MethodHandle getMemUnit =
            Helper.downcallHandle("ffi_benchmark_sysinfo", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS.withTargetLayout(sysinfoLayout)), false);
    private static final MethodHandle getMemUnitTrivial =
            Helper.downcallHandle("ffi_benchmark_sysinfo", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS.withTargetLayout(sysinfoLayout)), true);

    private static final VarHandle memUnitHandle = sysinfoLayout.varHandle(MemoryLayout.PathElement.groupElement("mem_unit"));

    private Arena sharedArena;
    private MemorySegment info;

    @Setup
    public void setup() {
        sharedArena = Arena.ofConfined();
        info = sharedArena.allocate(sysinfoLayout);
    }

    @TearDown
    public void cleanup() {
        sharedArena.close();
        sharedArena = null;
        info = null;
    }

    @Benchmark
    public int getMemUnitJni() {
        return getMemUnit();
    }

    @Benchmark
    public int getMemUnitJna() {
        var info = new com.sun.jna.platform.linux.LibC.Sysinfo();
        JNA.ffi_benchmark_sysinfo(info);
        return info.mem_unit;
    }

    @Benchmark
    public int getMemUnitJnaDirect() {
        var info = new com.sun.jna.platform.linux.LibC.Sysinfo();
        JnaDirect.ffi_benchmark_sysinfo(info);
        return info.mem_unit;
    }

    @Benchmark
    public int getMemUnitJnr() {
        var info = new JnrSysInfo(jnr.ffi.Runtime.getSystemRuntime());
        JNR.ffi_benchmark_sysinfo(info);
        return info.mem_unit.intValue();
    }

    @Benchmark
    public int getMemUnitJnrIgnoreError() {
        var info = new JnrSysInfo(jnr.ffi.Runtime.getSystemRuntime());
        JNR_IGNORE_ERROR.ffi_benchmark_sysinfo(info);
        return info.mem_unit.intValue();
    }

    @Benchmark
    public int getMemUnitPanama() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(sysinfoLayout);
            getMemUnit.invokeExact(info);
            return (int) memUnitHandle.get(info);
        }
    }

    @Benchmark
    public int getMemUnitPanamaTrivial() throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(sysinfoLayout);
            getMemUnitTrivial.invokeExact(info);
            return (int) memUnitHandle.get(info);
        }
    }

    @Benchmark
    public int getMemUnitPanamaNoAllocate() throws Throwable {
        getMemUnit.invokeExact(info);
        return (int) memUnitHandle.get(info);
    }

    @Benchmark
    public int getMemUnitPanamaTrivialNoAllocate() throws Throwable {
        getMemUnitTrivial.invokeExact(info);
        return (int) memUnitHandle.get(info);
    }

    public static void main(String[] args) throws Throwable {
        var info = new com.sun.jna.platform.linux.LibC.Sysinfo();
        LibC.INSTANCE.sysinfo(info);

        int memUnit = info.mem_unit;
        Consumer<Integer> checker = v -> {
            if (v != memUnit) {
                throw new AssertionError("expect: " + memUnit + ", actual: " + v);
            }
        };

        SysinfoBenchmark benchmark = new SysinfoBenchmark();
        benchmark.setup();

        try {
            System.out.println("=> Running getMemUnitJni");
            checker.accept(benchmark.getMemUnitJni());

            System.out.println("=> Running getMemUnitJna");
            checker.accept(benchmark.getMemUnitJna());

            System.out.println("=> Running getMemUnitJnaDirect");
            checker.accept(benchmark.getMemUnitJnaDirect());

            System.out.println("=> Running getMemUnitJnr");
            checker.accept(benchmark.getMemUnitJnr());

            System.out.println("=> Running getMemUnitJnrIgnoreError");
            checker.accept(benchmark.getMemUnitJnrIgnoreError());

            System.out.println("=> Running getMemUnitPanama");
            checker.accept(benchmark.getMemUnitPanama());

            System.out.println("=> Running getMemUnitPanamaTrivial");
            checker.accept(benchmark.getMemUnitPanamaTrivial());

            System.out.println("=> Running getMemUnitPanamaNoAllocate");
            checker.accept(benchmark.getMemUnitPanamaNoAllocate());

            System.out.println("=> Running getMemUnitPanamaTrivialNoAllocate");
            checker.accept(benchmark.getMemUnitPanamaTrivialNoAllocate());
        } finally {
            benchmark.cleanup();
        }
    }
}

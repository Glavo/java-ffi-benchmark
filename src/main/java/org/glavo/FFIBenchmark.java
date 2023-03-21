/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.glavo;

import com.sun.jna.Native;
import jnr.ffi.LibraryLoader;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 3, time = 3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class FFIBenchmark {

    public interface NativeLib extends com.sun.jna.Library {
        void ffi_benchmark_noop();
    }

    private static final class JnaDirect {
        public static native void ffi_benchmark_noop();
    }

    private static final NativeLib jna;
    private static final NativeLib jnr;

    static {
        var libpath = System.getProperty("org.glavo.benchmark.libpath");

        System.load(libpath);

        jna = Native.load(libpath, NativeLib.class);
        jnr = LibraryLoader.create(NativeLib.class).load(libpath);
        Native.register(JnaDirect.class, "ffi-benchmark");
    }

    private static final Linker.Option[] TRIVIAL = {Linker.Option.isTrivial()};
    private static final Linker.Option[] NOT_TRIVIAL = {};

    private static MethodHandle downcallHandle(String name, FunctionDescriptor fd, boolean trivial) {
        MemorySegment address = SymbolLookup.loaderLookup()
                .find(name)
                .orElseThrow(() -> new AssertionError(name + " not found"));

        return Linker.nativeLinker().downcallHandle(address, fd, trivial ? TRIVIAL : NOT_TRIVIAL);
    }

    // ========= noop =========

    private static native void noop();
    private static native void noop_critical();

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
        jna.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopJnaDirect() {
        JnaDirect.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopJnr() {
        jnr.ffi_benchmark_noop();
    }

    @Benchmark
    public void noopPanama() throws Throwable {
        noop.invokeExact();
    }

    @Benchmark
    public void noopPanamaTrivial() throws Throwable {
        noopTrivial.invokeExact();
    }

    // ========= gettimeofday =========

}

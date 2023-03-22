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

package benchmark;

import com.sun.jna.Native;
import jnr.ffi.LibraryLoader;
import jnr.ffi.LibraryOption;
import sun.misc.Unsafe;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.Map;

public class Helper {

    private static final String libpath = System.getProperty("org.glavo.benchmark.libpath");

    static final Unsafe UNSAFE;

    static {
        System.load(libpath);

        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    private static final Linker.Option[] TRIVIAL = {Linker.Option.isTrivial()};
    private static final Linker.Option[] NOT_TRIVIAL = {};

    static MethodHandle downcallHandle(String name, FunctionDescriptor fd, boolean trivial) {
        MemorySegment address = SymbolLookup.loaderLookup()
                .find(name)
                .orElseThrow(() -> new AssertionError(name + " not found"));

        return Linker.nativeLinker().downcallHandle(address, fd, trivial ? TRIVIAL : NOT_TRIVIAL);
    }

    static MemorySegment upcallStub(MethodHandle target, FunctionDescriptor function, Arena arena) {
        return Linker.nativeLinker().upcallStub(target, function, arena);
    }

    static <L extends com.sun.jna.Library> L loadJna(Class<L> clazz) {
        return Native.load(libpath, clazz);
    }

    static <L> L loadJnr(Class<L> clazz) {
        return LibraryLoader.create(clazz).load(libpath);
    }

    static <L> L loadJnrIgnoreError(Class<L> clazz) {
        return LibraryLoader.loadLibrary(
                clazz,
                Map.of(LibraryOption.IgnoreError, true),
                libpath
        );
    }

    static void registerJnaDirect(Class<?> clazz) {
        Native.register(clazz, libpath);
    }
}

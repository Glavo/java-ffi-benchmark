package benchmark.experimental;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

@State(Scope.Benchmark)
public class GetStringUTF8Benchmark {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;
    private static final int SPECIES_LENGTH = SPECIES.length();
    private static final MethodHandle STRING_CONSTRUCTOR;
    private static final byte LATIN1_CODER = 0;

    static {
        try {
            STRING_CONSTRUCTOR = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup())
                    .findConstructor(String.class, MethodType.methodType(void.class, byte[].class, byte.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String newAsciiString(byte[] array) {
        try {
            // assert String.COMPACT_STRINGS;
            return (String) STRING_CONSTRUCTOR.invokeExact(array, LATIN1_CODER);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    // Returns a negative value representing a non ASCII string
    private static int strlen(MemorySegment segment, int length) {
        boolean ascii = true;
        int upperBound = SPECIES.loopBound(length);

        int offset = 0;
        for (; offset < upperBound; offset += SPECIES_LENGTH) {
            int idx = ByteVector.fromMemorySegment(SPECIES, segment, offset, ByteOrder.nativeOrder())
                    .compare(VectorOperators.LE, 0)
                    .firstTrue();

            if (idx < SPECIES_LENGTH) {
                byte b = segment.get(ValueLayout.JAVA_BYTE, offset + idx);
                if (b == 0) {
                    return offset + idx;
                } else { // b < 0
                    ascii = false;
                    break;
                }
            }
        }

        if (offset < upperBound) { // Non ASCII String
            for (; offset < upperBound; offset += SPECIES_LENGTH) {
                int idx = ByteVector.fromMemorySegment(SPECIES, segment, offset, ByteOrder.nativeOrder())
                        .compare(VectorOperators.EQ, 0)
                        .firstTrue();

                if (idx < SPECIES_LENGTH) {
                    return -(offset + idx);
                }
            }
        }

        for (; offset < length; offset++) {
            byte b = segment.get(ValueLayout.JAVA_BYTE, offset);
            if (b == 0) {
                return ascii ? offset : -offset;
            } else if (b < 0) {
                ascii = false;
            }
        }

        throw new IllegalArgumentException("String too large");
    }

    public static String getUtf8String(MemorySegment segment) {
        long length = segment.byteSize();
        if (segment.address() % SPECIES_LENGTH != 0) { // For simplicity, do not handle these situations
            return segment.getString(0, StandardCharsets.UTF_8);
        }

        int len = strlen(segment, (int) Long.min(length, Integer.MAX_VALUE));
        boolean ascii = true;

        if (len == 0) {
            return "";
        }

        if (len < 0) {
            len = -len;
            ascii = false;
        }


        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, bytes, 0, len);
        return ascii ? newAsciiString(bytes) : new String(bytes, StandardCharsets.UTF_8);
    }

    private static MemorySegment allocateAlignmentString(Arena arena, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

        MemorySegment heapSegment = MemorySegment.ofArray(bytes);
        MemorySegment res = arena.allocate(MemoryLayout.sequenceLayout(bytes.length + 1, ValueLayout.JAVA_BYTE)
                .withByteAlignment((long) SPECIES_LENGTH));

        if (res.address() % SPECIES_LENGTH != 0) {
            throw new AssertionError();
        }

        res.copyFrom(heapSegment);
        res.set(ValueLayout.JAVA_BYTE, bytes.length, (byte) 0);

        return res;
    }

    @Param({"4", "8", "16", "24", "32", "48", "64", "128", "256", "512", "1024", "2048", "4096"})
    public int length;

    @Param({"false", "true"})
    public boolean utf8;

    private Arena arena;
    private MemorySegment segment;
    private MemorySegment segmentNoLimit;

    @Setup
    public void setup() {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (utf8 && i == length - 1) {
                builder.append('\u4f60');
            } else {
                builder.append((char) ((i % 26) + 'A'));
            }
        }
        String testString = builder.toString();

        arena = Arena.ofConfined();
        segment = allocateAlignmentString(arena, testString);
        segmentNoLimit = segment.reinterpret(Long.MAX_VALUE);
    }

    @TearDown
    public void cleanup() {
        arena.close();
        arena = null;
        segment = null;
        segmentNoLimit = null;
    }

    //@Benchmark
    public String panama() {
        return segment.getString(0, StandardCharsets.UTF_8);
    }

    //@Benchmark
    public String vector() {
        return getUtf8String(segment);
    }

    //@Benchmark
    public String vectorNoLimit() {
        return getUtf8String(segmentNoLimit);
    }

    public static void main(String[] args) {
        try (Arena arena = Arena.ofConfined()) {
            String[] strings = {
                    "ABCD",
                    "ABCD\u4f60",
                    "ABCDEFGHIJKLMNOP",                             // length = 16
                    "ABCDEFGHIJKLMNOPQ",                            // length = 17
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ",                   // length = 26
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdef",             // length = 32
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg",            // length = 33
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefgh",           // length = 34

                    // UTF-8
                    "\u4f60BCDEFGHIJKLMNOP",
                    "ABCDEFGH\u4f60JKLMNOP",
                    "ABCDEFGHIJKLMNO\u4f60",

                    "\u4f60BCDEFGHIJKLMNOPQ",
                    "ABCDEFGH\u4f60JKLMNOPQ",
                    "ABCDEFGHIJKLMNO\u4f60Q",
                    "ABCDEFGHIJKLMNOP\u4f60",
                    "ABCDEFGHIJKLMNOP\u4f60R",
                    "ABCDEFGHIJKLMNOPQ\u4f60",

                    "\u4f60BCDEFGHIJKLMNOPQRSTUVWXYZabcdef",
                    "ABCDEFGH\u4f60JKLMNOPQRSTUVWXYZabcdef",
                    "ABCDEFGHIJKLMNO\u4f60QRSTUVWXYZabcdef",
                    "ABCDEFGHIJKLMNOP\u4f60RSTUVWXYZabcdef",
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcde\u4f60",
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcd\u4f60f",
            };

            MemorySegment[] arr = Arrays.stream(strings)
                    .map(str -> GetStringUTF8Benchmark.allocateAlignmentString(arena, str))
                    .toArray(MemorySegment[]::new);

            for (int i = 0; i < strings.length; i++) {
                if (!strings[i].equals(getUtf8String(arr[i]))) {
                    throw new AssertionError(strings[i]);
                }

                if (!strings[i].equals(getUtf8String(arr[i].reinterpret(Long.MAX_VALUE)))) {
                    throw new AssertionError(strings[i]);
                }
            }
        }

        System.out.println("OK!");
    }
}

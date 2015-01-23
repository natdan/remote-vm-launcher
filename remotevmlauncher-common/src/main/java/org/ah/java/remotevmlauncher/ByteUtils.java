package org.ah.java.remotevmlauncher;

public class ByteUtils {

    public static long toLong(byte[] buf, int pos) {
        return ((long)toUnsignedByte(buf, pos + 0)) << 56 
             + ((long)toUnsignedByte(buf, pos + 1)) << 48 
             + ((long)toUnsignedByte(buf, pos + 2)) << 40 
             + ((long)toUnsignedByte(buf, pos + 3)) << 32 
             + ((long)toUnsignedByte(buf, pos + 4)) << 24 
             + ((long)toUnsignedByte(buf, pos + 5)) << 16 
             + ((long)toUnsignedByte(buf, pos + 6)) << 8 
             + ((long)toUnsignedByte(buf, pos + 7));
    }

    public static int toInt(byte[] buf, int pos) {
        return toUnsignedByte(buf, pos) << 24 
                + toUnsignedByte(buf, pos + 1) << 16 
                + toUnsignedByte(buf, pos + 2) << 8 
                + toUnsignedByte(buf, pos + 3);
    }

    public static short toShort(byte[] buf, int pos) {
        return (short)(toUnsignedByte(buf, pos) << 8 + toUnsignedByte(buf, pos + 1));
    }

    public static int toUnsignedByte(byte[] buf, int pos) {
        int b = buf[pos];
        if (b < 0) { b = 256 + b; }
        return b;
    }

    public static int fromShort(byte[] buffer, int pos, short s) {
        buffer[pos + 0] = (byte)((s >> 8) & 255);
        buffer[pos + 1] = (byte)(s & 255);
        return pos + 2;
    }

    public static int fromInt(byte[] buffer, int pos, int i) {
        buffer[pos] = (byte)(i >> 24);
        buffer[pos + 1] = (byte)((i >> 16) & 255);
        buffer[pos + 2] = (byte)((i >> 8) & 255);
        buffer[pos + 3] = (byte)(i & 255);
        return pos + 4;
    }

    public static int fromLong(byte[] buffer, int pos, long l) {
        buffer[pos] = (byte)((l >> 56) & 255);
        buffer[pos + 1] = (byte)((l >> 48) & 255);
        buffer[pos + 2] = (byte)((l >> 40) & 255);
        buffer[pos + 3] = (byte)((l >> 32) & 255);
        buffer[pos + 4] = (byte)((l >> 24) & 255);
        buffer[pos + 5] = (byte)((l >> 16) & 255);
        buffer[pos + 6] = (byte)((l >> 8) & 255);
        buffer[pos + 7] = (byte)(l & 255);
        return pos + 8;
    }

    public static int toBytes(byte[] buffer, int pos, String string) {
        byte[] strBytes = string.getBytes();
        System.arraycopy(strBytes, 0, buffer, pos, strBytes.length);
        return pos + strBytes.length;
    }

    public static int toBytesWithLength(byte[] buffer, int pos, String string) {
        byte[] strBytes = string.getBytes();
        if (strBytes.length > 32767) {
            throw new RuntimeException("Payload is too long; " + strBytes.length);
        }
        buffer[pos] = (byte)(strBytes.length / 256);
        buffer[pos + 1] = (byte)(strBytes.length & 255);
        System.arraycopy(strBytes, 0, buffer, pos + 2, strBytes.length);
        return pos + strBytes.length + 2;
    }

    public static int toBytesWithLength(byte[] buffer, int pos, byte[] bytes) {
        if (bytes.length > 32767) {
            throw new RuntimeException("Payload is too long; " + bytes.length);
        }
        buffer[pos] = (byte)(bytes.length / 256);
        buffer[pos + 1] = (byte)(bytes.length & 255);
        System.arraycopy(bytes, 0, buffer, pos + 2, bytes.length);
        return pos + bytes.length + 2;
    }

    public static int toBytesWithLengthLong(byte[] buffer, int pos, byte[] bytes) {
        buffer[pos] = (byte)(bytes.length >> 24);
        buffer[pos + 1] = (byte)((bytes.length >> 16) & 255);
        buffer[pos + 2] = (byte)((bytes.length >> 8) & 255);
        buffer[pos + 3] = (byte)(bytes.length & 255);
        System.arraycopy(bytes, 0, buffer, pos + 4, bytes.length);
        return pos + bytes.length + 4;
    }
}

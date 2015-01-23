package org.ah.java.remotevmlauncher.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class BufferToInputStream extends InputStream {

    byte[] firstBuffer = new byte[12];
    byte[] secondBuffer = null;
    int firstLen = 0;
    int secondLen = 0;
    int pos = 0;
    boolean readingSecond = false;

    public synchronized void process(ByteBuffer byteBuffer) {
        if (secondBuffer == null) {
            processFirstBuffer(byteBuffer);
            if (byteBuffer.remaining() > 0) {
                processSecondBuffer(byteBuffer);
            }
        } else {
            processSecondBuffer(byteBuffer);
        }
        notifyAll();
    }

    protected void processFirstBuffer(ByteBuffer byteBuffer) {
        int rem = byteBuffer.remaining();
        if (rem >= firstBuffer.length - firstLen) {
            byteBuffer.get(firstBuffer, firstLen, firstBuffer.length - firstLen);
            firstLen = firstBuffer.length;
        } else {
            byteBuffer.get(firstBuffer, firstLen, rem);
            firstLen = firstLen + rem;
        }
        if (firstLen == firstBuffer.length) {
            // We read all
            int ch1 = firstBuffer[10];
            int ch2 = firstBuffer[11];
            int secondSize = ((ch1 << 8) + (ch2 << 0));
            secondBuffer = new byte[secondSize];
        }
    }

    protected void processSecondBuffer(ByteBuffer byteBuffer) {
        if (secondLen < secondBuffer.length) {
            int rem = byteBuffer.remaining();
            if (rem >= secondBuffer.length - secondLen) {
                byteBuffer.get(secondBuffer, secondLen, secondBuffer.length - secondLen);
                secondLen = secondBuffer.length;
            } else {
                byteBuffer.get(secondBuffer, secondLen, rem);
                secondLen = secondLen + rem;
            }
        } else {
            // Nothing to process - wait for other thread to sort out things
            try {
                wait();
            } catch (InterruptedException ignore) {
            }
        }
    }

    @Override
    public synchronized int read() throws IOException {
        if (!checkAvailable()) {
            return -1;
        }
        byte[] source;
        if (readingSecond) {
            source = secondBuffer;
        } else {
            source = firstBuffer;
        }
        int r = source[pos];
        if (r < 0) {
            r = 256 + r;
        }
        pos++;
        if (!readingSecond && pos == firstBuffer.length) {
            readingSecond = true;
            pos = 0;
        }
        return r;
    }


    @Override
    public synchronized int read(byte[] buffer, int off, int l) throws IOException {
        if (!checkAvailable()) {
            return -1;
        }
        byte[] source;
        int sourceLen;
        if (readingSecond) {
            source = secondBuffer;
            sourceLen = secondLen;
        } else {
            source = firstBuffer;
            sourceLen = firstLen;
        }

        int rem = sourceLen - pos;
        if (l > rem) {
            l = rem;
        }
        System.arraycopy(source, pos, buffer, off, l);
        pos = pos + l;

        if (!readingSecond && pos == firstBuffer.length) {
            readingSecond = true;
            pos = 0;
        }
        return l;
    }

    protected boolean checkAvailable() {
        byte[] source;
        int sourceLen;
        if (readingSecond) {
            source = secondBuffer;
            sourceLen = secondLen;
        } else {
            source = firstBuffer;
            sourceLen = firstLen;
        }
        if (pos >= source.length) {
            return false;
        }
        if (pos >= sourceLen) {
            try {
                wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return true;
    }
}

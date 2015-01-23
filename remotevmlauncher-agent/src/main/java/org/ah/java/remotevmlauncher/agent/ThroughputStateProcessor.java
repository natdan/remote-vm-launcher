package org.ah.java.remotevmlauncher.agent;

import java.nio.ByteBuffer;

import org.ah.java.remotevmlauncher.protocol.ReadyProcessor;
import org.ah.java.remotevmlauncher.protocol.ResourceRequestProcessor;

public class ThroughputStateProcessor {

    public static final int IDLE = 0;
    public static final int FIRST_BYTE = 1;
    public static final int SECOND_BYTE = 2;
    public static final int ID_BYTES = 3;
    public static final int TWO_FIRST_BYTE = 4;
    public static final int TWO_SECOND_BYTE = 5;
    public static final int TWO_ID_BYTES = 6;
    public static final int THREE_FIRST_BYTE = 7;
    public static final int THREE_SECOND_BYTE = 8;
    public static final int THREE_ID_BYTES = 9;

    private int state = IDLE;

    private int len = 0;
    private int ch1;
    private int ch2;

    public boolean isIdle() {
        return state == IDLE;
    }

    public void process(ByteBuffer buffer) {
        buffer.mark();
        int rem = buffer.remaining();
        while (rem > 0) {
            if (state == IDLE) {
                if (rem > 1) {
                    len = buffer.getShort();
                    if (len < 0) {
                        len = 65536 + len;
                    }
                    if (len != 2) {
                        throw new IllegalStateException("Cannot process launcher to client stream. ID longer than 2 bytes! Got " + len);
                    }
                    state = SECOND_BYTE;
                } else {
                    ch1 = buffer.get();
                    state = FIRST_BYTE;
                }
            } else if (state == FIRST_BYTE) {
                ch2 = buffer.get();
                len = (ch1 << 8) + (ch2 << 0);
                if (len != 2) {
                    throw new IllegalStateException("Cannot process launcher to client stream. ID longer than 2 bytes!");
                }
                state = SECOND_BYTE;
            } else if (state == SECOND_BYTE) {
                ch1 = buffer.get();
                len--;
                state = ID_BYTES;
            } else if (state == ID_BYTES) {
                ch2 = buffer.get();
                len--;
                if (len != 0) {
                    throw new RuntimeException("Program error. Fix this!");
                }
                String id = Character.toString((char)ch1) + Character.toString((char)ch2);
                if (ReadyProcessor.ID.equals(id)) {
                    state = IDLE;
                } else if (ResourceRequestProcessor.ID.equals(id)) {
                    state = TWO_FIRST_BYTE;
                } else {
                    throw new IllegalStateException("Unknown processor ID; " + id);
                }
            } else if (state == TWO_FIRST_BYTE) {
                if (rem > 1) {
                    len = buffer.getShort();
                    if (len < 0) {
                        len = 65536 + len;
                    }
                    if (len > 0) {
                        state = TWO_ID_BYTES;
                    } else {
                        state = THREE_FIRST_BYTE;
                    }
                } else {
                    ch1 = buffer.get();
                    state = TWO_SECOND_BYTE;
                }
            } else if (state == TWO_SECOND_BYTE) {
                ch2 = buffer.get();
                len = (ch1 << 8) + (ch2 << 0);
                if (len > 0) {
                    state = TWO_ID_BYTES;
                } else {
                    state = THREE_FIRST_BYTE;
                }
            } else if (state == TWO_ID_BYTES) {
                if (len < rem) {
                    buffer.position(buffer.position() + len);
                    state = THREE_FIRST_BYTE;
                } else {
                    buffer.position(buffer.position() + rem);
                    len = len - rem;
                }
            } else if (state == THREE_FIRST_BYTE) {
                if (rem > 1) {
                    len = buffer.getShort();
                    if (len < 0) {
                        len = 65536 + len;
                    }
                    if (len > 0) {
                        state = THREE_ID_BYTES;
                    } else {
                        state = IDLE;
                    }
                } else {
                    ch1 = buffer.get();
                    state = THREE_SECOND_BYTE;
                }
            } else if (state == THREE_SECOND_BYTE) {
                ch2 = buffer.get();
                len = (ch1 << 8) + (ch2 << 0);
                if (len > 0) {
                    state = THREE_ID_BYTES;
                } else {
                    state = IDLE;
                }
            } else if (state == THREE_ID_BYTES) {
                if (len <= rem) {
                    buffer.position(buffer.position() + len);
                    state = IDLE;
                } else {
                    buffer.position(buffer.position() + rem);
                    len = len - rem;
                }
            }
            rem = buffer.remaining();
        }
        buffer.reset();
    }
}

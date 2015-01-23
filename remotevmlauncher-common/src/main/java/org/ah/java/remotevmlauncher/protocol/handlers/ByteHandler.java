package org.ah.java.remotevmlauncher.protocol.handlers;

import java.nio.ByteBuffer;

public abstract class ByteHandler extends DataHandler {

    public abstract void storeByte(byte b);
    public abstract byte readByte();

    protected void storeImpl(ByteBuffer buffer) {
    }

    protected abstract void readImpl(ByteBuffer buffer);
}

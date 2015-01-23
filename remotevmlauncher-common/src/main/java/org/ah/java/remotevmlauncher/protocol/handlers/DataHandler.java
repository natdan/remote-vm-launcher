package org.ah.java.remotevmlauncher.protocol.handlers;

import java.nio.ByteBuffer;

public abstract class DataHandler {

    public void store(ByteBuffer buffer) {
        storeImpl(buffer);
    }

    public void read(ByteBuffer buffer) {
        readImpl(buffer);
    }

    protected abstract void storeImpl(ByteBuffer buffer);
    protected abstract void readImpl(ByteBuffer buffer);
}

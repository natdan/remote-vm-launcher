package org.ah.java.remotevmlauncher.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public abstract class AbstractProcessor implements ProtocolProcessor {

    private String id;

    protected AbstractProcessor(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    protected void receiveStringArray(DataInputStream dis, List<String> array) throws IOException {
        array.clear();
        int len = dis.readShort();
        for (int i = 0; i < len; i++) {
            array.add(dis.readUTF());
        }
    }

    public void send(DataOutputStream dos) throws IOException {
        dos.writeUTF(getId());
        dos.flush();
    }

    protected void sendString(DataOutputStream dos, String s) throws IOException {
        dos.writeUTF(getId());
        dos.writeUTF(s);
        dos.flush();
    }

    protected void sendStringArray(DataOutputStream dos, List<String> list) throws IOException {
        dos.writeUTF(getId());
        dos.writeShort(list.size());
        for (String arg : list) {
            dos.writeUTF(arg);
        }
        dos.flush();
    }
}

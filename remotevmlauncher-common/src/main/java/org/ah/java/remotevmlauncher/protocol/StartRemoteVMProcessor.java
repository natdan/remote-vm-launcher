/*
 * Copyright (c) 2013 Creative Sphere Limited.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *
 *   Creative Sphere - initial API and implementation
 *
 */
package org.ah.java.remotevmlauncher.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 *
 *
 * @author Daniel Sendula
 */
public class StartRemoteVMProcessor extends AbstractProcessor {

    public static final String ID = "VM";

    public static final boolean IMPLEMENTED = true;

    private int remoteDebugPort = 0;
    private boolean remoteDebugSuspendAtStart = false;
    private boolean stopVMonDisconnect = true;

    private List<String> vmArgs = new ArrayList<String>();
    private List<String> launcherArgs = new ArrayList<String>();

    public StartRemoteVMProcessor() {
        super(ID);
    }

    public int getRemoteDebugPort() {
        return remoteDebugPort;
    }

    public void setRemoteDebugPort(int remoteDebugPort) {
        this.remoteDebugPort = remoteDebugPort;
    }

    public boolean isRemoteDebugSuspendAtStart() {
        return remoteDebugSuspendAtStart;
    }

    public void setRemoteDebugSuspendAtStart(boolean remoteDebugSuspendAtStart) {
        this.remoteDebugSuspendAtStart = remoteDebugSuspendAtStart;
    }

    public List<String> getVmArgs() {
        return vmArgs;
    }

    public List<String> getLauncherArgs() {
        return launcherArgs;
    }

    public boolean isStopVMonDisconnect() {
        return stopVMonDisconnect;
    }

    public void setStopVMonDisconnect(boolean stopVMonDisconnect) {
        this.stopVMonDisconnect = stopVMonDisconnect;
    }

    public void receive(DataInputStream dis) throws IOException{
        vmArgs.clear();
        launcherArgs.clear();
        remoteDebugPort = dis.readInt();
        remoteDebugSuspendAtStart = dis.readBoolean();
        stopVMonDisconnect = dis.readBoolean();
        /*int totalSize = */dis.readShort(); // doing nothing with it
        int argsLen = dis.readShort();
        for (int i = 0; i < argsLen; i++) {
            vmArgs.add(dis.readUTF());
        }
        argsLen = dis.readShort();
        for (int i = 0; i < argsLen; i++) {
            launcherArgs.add(dis.readUTF());
        }

        startVM();
    }

    public void startVM() throws IOException {
    }

    public void send(DataOutputStream dos) throws IOException {
        dos.writeUTF(getId());
        dos.writeInt(remoteDebugPort);
        dos.writeBoolean(remoteDebugSuspendAtStart);
        dos.writeBoolean(stopVMonDisconnect);

        ByteArrayOutputStream cache = new ByteArrayOutputStream();
        DataOutputStream dosCache = new DataOutputStream(cache);
        dosCache.writeShort(vmArgs.size());
        for (String arg : vmArgs) {
            dosCache.writeUTF(arg);
        }
        dosCache.writeShort(launcherArgs.size());
        for (String arg : launcherArgs) {
            dosCache.writeUTF(arg);
        }
        dosCache.flush();

        byte[] cacheBytes = cache.toByteArray();
        if (cacheBytes.length > 32767) {
            throw new RuntimeException("StartRemoteVM arguments longer than 32767 (" + cacheBytes.length +")");
        }
        dos.writeShort(cacheBytes.length);
        dos.write(cacheBytes);
        dos.flush();
    }
//
//    public int readInt(InputStream in) throws IOException {
//        int ch1 = in.read();
//        int ch2 = in.read();
//        int ch3 = in.read();
//        int ch4 = in.read();
//        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
//    }
//
//    public final short readShort(InputStream in) throws IOException {
//        int ch1 = in.read();
//        int ch2 = in.read();
//        return (short)((ch1 << 8) + (ch2 << 0));
//    }
}

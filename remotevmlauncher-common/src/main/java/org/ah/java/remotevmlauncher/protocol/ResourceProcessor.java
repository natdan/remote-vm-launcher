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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * 
 *
 * @author Daniel Sendula
 */
public class ResourceProcessor extends AbstractProcessor {

    public static final String ID = "R!";

    public ResourceProcessor() {
        super(ID);
    }

    public void receive(DataInputStream dis) throws IOException {
        int size = dis.readInt();
        loadResource(dis, size);
    }

    public void loadResource(DataInputStream dis, int size) throws IOException {
    }

    public void send(DataOutputStream dos, InputStream is, int size) throws IOException {
        dos.writeUTF(getId());
        dos.writeInt(size);
        if (size > 0) {
            byte[] buffer = new byte[10240];
            while (size > 0) {
                int s = size;
                if (s > buffer.length) { s = buffer.length; }
                int r = is.read(buffer, 0, s);
                if (r <= 0) {
                    size = 0;
                } else {
                    size = size - r;
                }
                dos.write(buffer, 0, r);
            }
        }
        dos.flush();
    }
}

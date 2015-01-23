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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protocol processor for local classpath
 *
 * @author Daniel Sendula
 */
public class LocalClasspathProcessor extends AbstractProcessor {

    public static final String ID = "LC";

    // pathId -> localClasspath
    private Map<String, String> localClasspath = new LinkedHashMap<String, String>();

    public LocalClasspathProcessor() {
        super(ID);
    }

    public Map<String, String> getLocalClasspath() {
        return localClasspath;
    }

    public void receive(DataInputStream dis) throws IOException {
        localClasspath.clear();
        int size = dis.readShort();
        for (int i = 0; i < size; i++) {
            String pathId = dis.readUTF();
            String classpath = dis.readUTF();
            localClasspath.put(pathId, classpath);
        }
    }

    public void send(DataOutputStream dos) throws IOException {
        dos.writeUTF(ID);
        dos.writeShort(localClasspath.size());
        for (Map.Entry<String, String> entry : localClasspath.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeUTF(entry.getValue());
        }
        dos.flush();
    }
}


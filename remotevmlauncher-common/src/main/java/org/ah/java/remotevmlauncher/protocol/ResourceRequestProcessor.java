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

/**
 * 
 * <b>WARNING: this cannot be used to send files larger than 2GB!</b>
 *
 * @author Daniel Sendula
 */
public class ResourceRequestProcessor extends AbstractProcessor {

    public static final String ID = "RR";

    private String pathId;
    private String resourceName;

    public ResourceRequestProcessor() {
        super(ID);
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public void receive(DataInputStream dis) throws IOException {
        pathId = dis.readUTF();
        resourceName = dis.readUTF();
        processRequest(pathId, resourceName);
    }

    public void processRequest(String pathId, String resourceName) throws IOException {
    }

    public void send(DataOutputStream dos) throws IOException {
        dos.writeUTF(getId());
        dos.writeUTF(pathId);
        dos.writeUTF(resourceName);
        dos.flush();
    }
}

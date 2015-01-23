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


/**
 * 
 *
 * @author Daniel Sendula
 */
public class StartApplicationProcessor extends AbstractProcessor {

    public static final String ID = "ST";

    private boolean start = false;

    public StartApplicationProcessor() {
        super(ID);
    }

    public boolean isStart() {
        return start;
    }

    public void reset() {
        start = false;
    }

    public void receive(DataInputStream dis) {
        start = true;
        synchronized (this) {
            notifyAll();
        }
    }
}

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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class describes a protocol state machine. It is to be fed with a byte buffer and amount of data in it.
 *
 * @author Daniel Sendula
 */
public class ProtocolStateMachine {

    private Map<String, ProtocolProcessor> processorMap = new HashMap<String, ProtocolProcessor>();
    private Logger logger = null;

    public ProtocolStateMachine() {
    }

    public ProtocolStateMachine(Logger logger) {
        this.logger = logger;
    }

    public Map<String, ProtocolProcessor> getProcessorMap() {
        return processorMap;
    }

    public void register(ProtocolProcessor protolProcessor) {
        this.processorMap.put(protolProcessor.getId(), protolProcessor);
    }

    public void processInput(DataInputStream dis) throws IOException {
        String id = dis.readUTF();
        if (logger != null && logger.isLoggable(Level.FINEST))  {
            logger.finest("Received packet with id: " + id);
        }
        ProtocolProcessor processor = processorMap.get(id);

        if (processor == null) {
            throw new RuntimeException("Unknown playload with id '" + id + "'");
        }
        processor.receive(dis);
    }
}

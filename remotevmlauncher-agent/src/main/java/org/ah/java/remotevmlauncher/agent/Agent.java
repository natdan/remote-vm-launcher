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
package org.ah.java.remotevmlauncher.agent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.ah.java.remotevmlauncher.JavaLoggingUtils;

/**
 * Main class - agent daemon.
 *
 * @author Daniel Sendula
 */
public class Agent implements ChannelProcessor {

    public static Logger LOGGER = Logger.getLogger("");

    private InetSocketAddress listeningSocketAddress;
    private String javaExecutablePathString = "java";
    private boolean stop = false;
    private Selector selector;
    private ServerSocketChannel commandServerSocket;
    private static Agent currentAgent;

    protected static int nextSessionId = 1;

    @SuppressWarnings("unused")
    private int debugLevel;

    public Agent() {
        this.listeningSocketAddress = new InetSocketAddress(8999);
    }

    public void setDebugLevel(int debugLevel) {
        this.debugLevel = debugLevel;
    }
    
    public InetSocketAddress getListeningSocketAddress() {
        return listeningSocketAddress;
    }

    public void setListeningSocketAddress(InetSocketAddress listeningSocketAddress) {
        this.listeningSocketAddress = listeningSocketAddress;
    }

    public String getJavaExecutablePathString() {
        return javaExecutablePathString;
    }

    public void setJavaExecutablePathString(String javaExecutablePathString) {
        this.javaExecutablePathString = javaExecutablePathString;
    }

    public void setup() throws IOException {

        LOGGER.info("Starting agent at " + listeningSocketAddress.getAddress() + ":" + listeningSocketAddress.getPort());

        commandServerSocket = ServerSocketChannel.open();
        commandServerSocket.configureBlocking(false);
        ServerSocket ss = commandServerSocket.socket();
        ss.bind(listeningSocketAddress);

        SelectionKey serverCommandKey = commandServerSocket.register(selector, SelectionKey.OP_ACCEPT);
        serverCommandKey.attach(this);
    }

    public void start() throws IOException {
        selector = Selector.open();
        try {
            setup();

            int count = 0;
            while (!stop) {
                int readyKeys = selector.select(250); // 1/4 second
                if (readyKeys > 0) {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator(); 
                    while (iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        processKey(key);
                        iterator.remove();
                    }
                    count = 0;
                } else {
                    // We are here probably because one side of the proxy has data waiting to be read
                    // but we are not ready to read them yet (waiting for Launcher to connect back to us.
                    count++;
                    if (count > 1) { // This is to let two goes before we go sleeping.
                        try {
                            Thread.sleep(10); // Randomly picked number
                        } catch (InterruptedException ignore) { }
                        count = 0;
                    }
                }
            }
        } finally {
            commandServerSocket.close();
        }
    }

    protected void processKey(SelectionKey key) throws IOException {
        ChannelProcessor processor = (ChannelProcessor)key.attachment();
        processor.process(key);
    }

    public void stop() {
        stop = true;
    }
    
    public void process(SelectionKey key) throws IOException {
        if (key.isConnectable()) {
            ((SocketChannel) key.channel()).finishConnect();
        }
        if (key.isAcceptable()) {
            SocketChannel clientChannel = ((ServerSocketChannel)key.channel()).accept();
            if (clientChannel != null) {
                try {
                    int sessionId = 0;
                    synchronized (this) {
                        sessionId = nextSessionId;
                        nextSessionId = nextSessionId + 1;
                    }
                    ClientSession session = new ClientSession(sessionId, clientChannel, javaExecutablePathString);
                    session.setup(selector);
                    session.start();
                } catch (IOException e) {
                    writeErrorBack(clientChannel, e);
                    clientChannel.close();
                }
            } else {
                // This is strange, again. Why channel is acceptable without anything to accept?!
            }
        }
        if (key.isReadable()) {
            // ...read messages...
        }
        if (key.isWritable()) {
            // ...write messages...
        }
    }


    public static void writeErrorBack(SocketChannel clientChannel, Throwable t) {
        t.printStackTrace();
    }

    public static void stopCurrentAgent() {
        if (currentAgent != null) {
            currentAgent.stop();
            currentAgent = null;
        }
    }

    public static void main(String[] args) throws Exception {
        int debugLevel = 1;
        Agent agent = new Agent();

        boolean debugSwitch = false;
        boolean listeningPortSwitch = false;
        boolean executableSwitch = false;
        boolean allRead = false;
        for (String arg : args) {
            if (!allRead) {
                if (listeningPortSwitch) {
                    int i = arg.indexOf(':');
                    if (i < 0) {
                        agent.setListeningSocketAddress(new InetSocketAddress(Integer.parseInt(arg)));
                    } else {
                        agent.setListeningSocketAddress(new InetSocketAddress(arg.substring(0, i), Integer.parseInt(arg.substring(i + 1))));
                    }
                    listeningPortSwitch = false;
                } else if (executableSwitch) {
                    agent.setJavaExecutablePathString(arg);
                    executableSwitch = false;
                } else if (debugSwitch) {
                    debugLevel = Integer.parseInt(arg);
                    debugSwitch = false;
                } else if ("-l".equals(arg) || "--listen".equals(arg)) {
                    listeningPortSwitch = true;
                } else if ("-d".equals(arg) || "--debug".equals(arg)) {
                    debugSwitch = true;
                } else if ("-e".equals(arg) || "--executable".equals(arg)) {
                    executableSwitch = true;
                } else if ("-h".equals(arg) || "?".equals(arg) || "--help".equals(arg)) {
                    printHelp();
                    System.exit(0);
                } else {
                    System.out.println("ERROR: unknown argument '" + arg + "'");
                    System.out.println();
                    printHelp();
                    System.exit(1);
                }
            } else {
                System.out.println("ERROR: too many arguments.");
                System.out.println();
                printHelp();
                System.exit(1);
            }
        }

        JavaLoggingUtils.setupSimpleConsoleLogging(debugLevel);

        currentAgent = agent;
        agent.setDebugLevel(debugLevel);
        agent.start();
    }

    public static void printHelp() {
        System.out.println("Remote VM Launch Agent usage:");
        System.out.println("");
        System.out.println("java -jar remotevmlauncher-agent.jar {options}");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("");
        System.out.println("  -l|--listen [address:]port  listen on defined address and port. Default: 0.0.0.0:8999");
        System.out.println("  -e|--executable path        path to java executable. Default: java (it must be in path)");
        System.out.println("  -d|--debug level            debug level from 0 to 4. Default: 1");
        System.out.println("  -h|--help                   this help.");
        System.out.println("");
    }
}

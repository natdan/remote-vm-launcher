/*
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * Copyright (c) 2013 Creative Sphere Limited.
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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Channel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ah.java.remotevmlauncher.ClasspathUtils;
import org.ah.java.remotevmlauncher.DebugUtils;
import org.ah.java.remotevmlauncher.JavaLoggingUtils;
import org.ah.java.remotevmlauncher.launcher.Launcher;
import org.ah.java.remotevmlauncher.protocol.ProtocolStateMachine;
import org.ah.java.remotevmlauncher.protocol.StartRemoteVMProcessor;

/**
 * Class that handles command session
 *
 * @author Daniel Sendula
 */
public class ClientSession implements Runnable {

    public Logger AGENT_LOGGER;
    public Logger CLIENT_LOGGER;
    public Logger LAUNCHER_LOGGER;

    private int sessionId;
    private long closeTimeout = 10000; // 10 seconds

    private String javaExecutablePathString;

    private boolean stop = false;
    private boolean clientSideClosed = false;
    private boolean clientSideCloseScheduled = false;
    private boolean launcherSideClosed = false;
    private long closeTimeoutExpires = -1;
    private boolean vmIsStarted = !StartRemoteVMProcessor.IMPLEMENTED;

    @SuppressWarnings("unused")
    private Selector selector;

    private SocketChannel clientSocketChannel;
    private SelectionKey clientSocketChannelKey;

    private ServerSocketChannel launcherServerSocketChannel;
    private SelectionKey launcherServerSocketChannelKey;

    private SocketChannel launcherSocketChannel;
    private SelectionKey launcherSocketChannelKey;

    private int launcherServerSocketPort;

    private Process process;
    private InputStream inputStream;
    private OutputStream outputStream;

    private ByteBuffer processInputBuffer;
    private ByteBuffer launcherToClientBuffer;
    private ByteBuffer launcherToClientCurrentBuffer;
    private boolean clientHasOutgoingTransmission = false;
    private boolean processInputBufferReady = false;
    private boolean launcherToClientBufferReady = false;

    private ByteBuffer clientToLauncherBuffer;

    private ProtocolStateMachine stateMachine;

    private StartRemoteVMProcessor startRemoteVMProcessor;
    private BufferToInputStream bufferToInputStream = new BufferToInputStream();

    private ThroughputStateProcessor throughputStateProcessor = new ThroughputStateProcessor();

    public ClientSession(int sessionId, SocketChannel commandChannel, String javaExecutablePathString) {
        this.sessionId = sessionId;
        this.clientSocketChannel = commandChannel;
        this.javaExecutablePathString = javaExecutablePathString;
        
        AGENT_LOGGER = Logger.getLogger("[" + sessionId + "] !!");
        CLIENT_LOGGER = Logger.getLogger("[" + sessionId + "] >>");
        LAUNCHER_LOGGER = Logger.getLogger("[" + sessionId + "] <<");
        
        InetSocketAddress remoteAddress = (InetSocketAddress)commandChannel.socket().getRemoteSocketAddress();
        
        AGENT_LOGGER.info("Got client from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort() + " address.");
    }

    public void setup(final Selector selector) throws IOException {
        this.selector = selector;

        stateMachine = new ProtocolStateMachine(AGENT_LOGGER);

        if (StartRemoteVMProcessor.IMPLEMENTED) {
            startRemoteVMProcessor = new StartRemoteVMProcessor() {
                @Override public void startVM() throws IOException {
                    startRemoteVM();
                }
            };
            stateMachine.register(startRemoteVMProcessor);

            final DataInputStream startupDataInputStream = new DataInputStream(bufferToInputStream);

            Thread startupThread = new Thread(new Runnable() {
                public void run() {
                    // Needed only for StartRemoveVMProcessor
                    try {
                        stateMachine.processInput(startupDataInputStream);
                    } catch (IOException e) {
                        AGENT_LOGGER.log(Level.SEVERE, "State machine processing exception", e);
                        System.exit(1);
                    }
                }
            });
            startupThread.start();
        }
        processInputBuffer = ByteBuffer.allocateDirect(1024);
        launcherToClientBuffer = ByteBuffer.allocateDirect(1024);
        clientToLauncherBuffer = ByteBuffer.allocateDirect(1024);

        clientSocketChannel.configureBlocking(false);
        clientSocketChannel.socket().setTcpNoDelay(true);
        clientSocketChannelKey = clientSocketChannel.register(selector, 0);
        if (StartRemoteVMProcessor.IMPLEMENTED) {
            clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_READ);
        }
        clientSocketChannelKey.attach(new ChannelProcessor() {
            public void process(SelectionKey key) throws IOException {
                try {
                    if (key.isReadable() || key.readyOps() == 0) {
                        // This is funny thing. No ready ops and yet read works and puts key away from coming back.
                        // Also, sorts out problem where proxy (agent, this session class) hangs and does not read
                        // what client has sent!

                        int r = clientSocketChannel.read(clientToLauncherBuffer);
                        if (r > 0) {
                            clientToLauncherBuffer.flip();
                            DebugUtils.debug(CLIENT_LOGGER, clientToLauncherBuffer);
                            if (vmIsStarted) {
                                clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() & ~SelectionKey.OP_READ); 
                                launcherSocketChannelKey.interestOps(launcherSocketChannelKey.interestOps() | SelectionKey.OP_WRITE);
                            } else {
                                bufferToInputStream.process(clientToLauncherBuffer);
                                if (clientToLauncherBuffer.remaining() > 0) {
                                    clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() & ~SelectionKey.OP_READ); 
                                    launcherSocketChannelKey.interestOps(launcherSocketChannelKey.interestOps() | SelectionKey.OP_WRITE);
                                } else {
                                    clientToLauncherBuffer.clear();
                                }
                            }
                            if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Client send " + r + " bytes to launcher"); }
                        } else if (r < 0) {
                            if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Closing all as client closed the connection"); }
                            closeClientSide();
                            if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Client has closed channel (output)."); }
                        }
                    }
                    if (key.isWritable()) {
                        if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Got writable for client channel"); }
                        if (launcherToClientCurrentBuffer == null) {
                            if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Selecting new buffer to send"); }
                            if (processInputBufferReady && throughputStateProcessor.isIdle()) {
                                launcherToClientCurrentBuffer = processInputBuffer;
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Selected stream buffer"); }
                            } else if (launcherToClientBufferReady) {
                                launcherToClientCurrentBuffer = launcherToClientBuffer;
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Selected client buffer"); }
                            } else {
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Selected new buffers; streamBuffer ready=" + processInputBufferReady + ", throughput ready=" + throughputStateProcessor.isIdle() + ", launcherToClientBufferReady=" + launcherToClientBufferReady); }
                            }
                            if (launcherToClientCurrentBuffer != null) {
                                DebugUtils.debug(LAUNCHER_LOGGER, launcherToClientCurrentBuffer);
                            }
                        }
                        if (launcherToClientCurrentBuffer == null) {
                            if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Removing WRITE from client channel"); }
                            clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() & ~SelectionKey.OP_WRITE);
                            clientHasOutgoingTransmission = false;
                            if (clientSideCloseScheduled) {
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Client side close scheduled, so we closing now"); }
                                closeClientSide();
                            }
                        } else {
                            if (launcherToClientCurrentBuffer.remaining() == 0) {
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("Buffer is completely sent. Finishing it..."); }
                                launcherToClientCurrentBuffer.clear();
                                if (launcherToClientCurrentBuffer == processInputBuffer) {
                                    synchronized (processInputBuffer) {
                                        processInputBufferReady = false;
                                        processInputBuffer.notifyAll();
                                        if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("It was stream - notifying it..."); }
                                    }
                                } else if (launcherToClientCurrentBuffer == launcherToClientBuffer) {
                                    if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) { LAUNCHER_LOGGER.finest("It was client - enabling client READ"); }
                                    launcherSocketChannelKey.interestOps(launcherSocketChannelKey.interestOps() | SelectionKey.OP_READ);
                                    launcherToClientBufferReady = false;
                                }
                                launcherToClientCurrentBuffer = null;
                            } else {
                                if (launcherToClientCurrentBuffer == launcherToClientBuffer) {
                                    throughputStateProcessor.process(launcherToClientBuffer);
                                }

                                int w = clientSocketChannel.write(launcherToClientCurrentBuffer);
                                if (w < 0) {
                                    clientSocketChannel.close();
                                    clientSocketChannelKey.cancel();
                                    if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Client has closed channel (input)."); }
                                }
                            }
                        }
                    }
                    if (!key.isValid()) {
                        if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Time to close the session " + sessionId); }
                    }
                } catch (CancelledKeyException e) {
                    if (CLIENT_LOGGER.isLoggable(Level.FINER)) {  CLIENT_LOGGER.info("Client disconnected (cancelled key)"); }
                    closeClientSide();
                    closeLauncherSide();
                } catch (IOException e) {
                    if ("Connection reset by peer".equals(e.getMessage())) {
                        closeClientSide();
                    } else if (!clientSideClosed) {
                        if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Client exception: (" + clientSideClosed +")"); }
                        CLIENT_LOGGER.log(Level.SEVERE, "Exception", e);
                    }
                }
            }
        });

        launcherServerSocketChannel = ServerSocketChannel.open();
        launcherServerSocketChannel.configureBlocking(false);
        ServerSocket ss = launcherServerSocketChannel.socket();
        ss.bind(null);

        launcherServerSocketPort = ss.getLocalPort();

        launcherServerSocketChannelKey = launcherServerSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        launcherServerSocketChannelKey.attach(new ChannelProcessor() {

            public void process(SelectionKey key) throws IOException {
                if (key.isConnectable()) {
                    if (((SocketChannel) key.channel()).finishConnect()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
                        key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                    } else {
                        key.cancel();
                    }
                }
                try {
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            SocketChannel newClientChannel = ((ServerSocketChannel)key.channel()).accept();
                            if (launcherSocketChannel == null) {
                                newClientChannel.configureBlocking(false);
                                launcherSocketChannelKey = newClientChannel.register(selector, SelectionKey.OP_READ);
                                launcherSocketChannelKey.attach(this);
                                launcherSocketChannel = newClientChannel;
                                clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_READ);
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Launcher connected back to us"); }

                                // We have already received connection from the launcher - so we don't care about any others.
                                closeChannel(launcherServerSocketChannel);

                            } else {
                                // We already have one open - we don't need any more!
                                newClientChannel.close();
                            }
                        }
                        if (key.isReadable()) {
                            int r = launcherSocketChannel.read(launcherToClientBuffer);
                            if (r > 0) {
                                launcherToClientBuffer.flip();
                                launcherSocketChannelKey.interestOps(launcherSocketChannelKey.interestOps() & ~SelectionKey.OP_READ); 
                                clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_WRITE);
                                launcherToClientBufferReady = true;
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Launcher sent " + r + " bytes to client "); }
                            } else if (r < 0) {
                                if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Launcher has closed channel (output)."); }
                                closeLauncherSide();
                            }
                        }
                        if (key.isWritable()) {
                            if (clientToLauncherBuffer.remaining() == 0) {
                                clientToLauncherBuffer.clear();
                                launcherSocketChannelKey.interestOps(launcherSocketChannelKey.interestOps() & ~SelectionKey.OP_WRITE);
                                clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_READ);
                            } else {
                                int w = launcherSocketChannel.write(clientToLauncherBuffer);
                                if (w < 0) {
                                    launcherSocketChannel.close();
                                    launcherSocketChannelKey.cancel();
                                    if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Launcher has closed channel (input)."); }
                                }
                            }
                        }
                    } else {
                        if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Launcher disconnected (key not valid)"); }
                    }
                } catch (CancelledKeyException e) {
                    // LAUNCHER_LOGGER.finer("Launcher disconnected (cancelled key)");
                } catch (IOException e) {
                    if ("Connection reset by peer".equals(e.getMessage())) {
                        closeLauncherSide();
                    } else if (!launcherSideClosed) {
                        if (LAUNCHER_LOGGER.isLoggable(Level.SEVERE)) { LAUNCHER_LOGGER.severe("Launcher exception: (" + launcherSideClosed +")"); }
                        LAUNCHER_LOGGER.log(Level.SEVERE, "Exception", e);
                    }
                }
            }
        });
    }

    public void start() throws IOException {
        if (!StartRemoteVMProcessor.IMPLEMENTED) {
            startRemoteVM();
        }
    }

    public void startRemoteVM() throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        ArrayList<String> command = new ArrayList<String>();
        command.add(javaExecutablePathString);
        if (startRemoteVMProcessor.getRemoteDebugPort() > 0) {
            command.add("-Xdebug");
            command.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=" + (startRemoteVMProcessor.isRemoteDebugSuspendAtStart() ? "y" : "n") + ",address=" + startRemoteVMProcessor.getRemoteDebugPort());
        }
        
        boolean debugLevelFlag = false;
        int clientDebugLevel = -1;
        
        String initialClasspath = ClasspathUtils.obtainInitialPath();
        if (AGENT_LOGGER.isLoggable(Level.INFO)) { AGENT_LOGGER.info("Launching with classpath: " + initialClasspath); }
        command.add("-cp");
        command.add(initialClasspath);
        for (String remoteVMArg : startRemoteVMProcessor.getVmArgs()) {
            if (debugLevelFlag) { 
                clientDebugLevel = Integer.parseInt(remoteVMArg); 
                debugLevelFlag = false;
            } else if ("-d".equals(remoteVMArg)) { 
                debugLevelFlag = true;
            }
            command.add(remoteVMArg);
        }
        command.add(Launcher.class.getName());
        for (String remoteArg : startRemoteVMProcessor.getLauncherArgs()) {
            if (debugLevelFlag) { 
                clientDebugLevel = Integer.parseInt(remoteArg); 
                debugLevelFlag = false;
            } else if ("-d".equals(remoteArg)) { 
                debugLevelFlag = true;
            }
            command.add(remoteArg);
        }
        command.add(Integer.toString(launcherServerSocketPort));

        String commandString = createCommandString(command);
        
        if (AGENT_LOGGER.isLoggable(Level.INFO)) { AGENT_LOGGER.info("Launching command: " + commandString); }
        
        if (clientDebugLevel >= 2) {
            sendLogStringBackToClient(new SimpleDateFormat(JavaLoggingUtils.TIME_FORMATTER).format(System.currentTimeMillis()) + "  Agent: Starting new VM with " + commandString);
        } else if (clientDebugLevel > 0) {
            sendLogStringBackToClient("Agent: Starting new VM with " + commandString);
        }
        
        processBuilder.command(command);
        processBuilder.redirectErrorStream(true);
        process = processBuilder.start();

        inputStream = process.getInputStream();
        outputStream = process.getOutputStream();

        Thread processThread = new Thread(this);
        processThread.start();
        vmIsStarted = true;
    }

    public void closeClientSide() throws IOException {
        if (!clientSideClosed) {
            clientSideClosed = true;
            if (CLIENT_LOGGER.isLoggable(Level.FINER)) { CLIENT_LOGGER.finer("Closing client side"); }
            closeChannel(clientSocketChannel);
        }
    }

    public void closeLauncherSide() throws IOException {
        if (!launcherSideClosed) {
            closeTimeoutExpires = System.currentTimeMillis() + closeTimeout;
            launcherSideClosed = true;
            if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Closing launcher side"); }
            closeChannel(launcherSocketChannel);
            closeChannel(launcherServerSocketChannel);
        }
    }

    public void stop() throws IOException {
        closeStream(inputStream);
        closeStream(outputStream);
        stop = true;
        if (clientHasOutgoingTransmission) {
            clientSideCloseScheduled = true;
        } else {
            closeClientSide();
        }
    }

    protected void closeChannel(Channel channel) throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    protected void closeStream(InputStream stream) throws IOException {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignore) {}
        }
    }

    protected void closeStream(OutputStream stream) throws IOException {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignore) {}
        }
    }

    public SocketChannel getCommandChannel() {
        return clientSocketChannel;
    }

    private String createCommandString(List<String> params) {
        StringBuilder command = new StringBuilder();
        
        boolean first = false;
        for (String p : params) {
            p = p.replaceAll("\\\\", "\\\\");
            p = p.replaceAll("\"", "\\\"");
            if (p.indexOf(' ') >= 0) {
                p = "\"" + p + "\"";
            }
            if (first) { first = false; }  else { command.append(" "); }
            command.append(p);
        }
        
        return command.toString();
    }

    private void sendLogStringBackToClient(String str) {
        try {
            processInputBuffer.clear();
            processInputBuffer.putShort((short) 2);
            processInputBuffer.put("S0".getBytes());
            processInputBuffer.putShort((short) str.length());
            processInputBuffer.put(str.getBytes(), 0, str.length());
            processInputBuffer.flip();
            synchronized (processInputBuffer) {
                clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_WRITE);
                processInputBufferReady = true;
                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) {
                    LAUNCHER_LOGGER.finest("Waiting on stream data to be sent back...");
                }
                processInputBuffer.wait();
                if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) {
                    LAUNCHER_LOGGER.finest("Waiting on stream data was sent back.");
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
        
    public void run() {
        byte[] buffer = new byte[1000];
        while (!stop) {
            try {
                processInputBuffer.clear();
                processInputBuffer.putShort((short) 2);
                processInputBuffer.put("S0".getBytes());
                int size = inputStream.read(buffer, 0, 1);
                if (size > 0) {
                    int available = inputStream.available();
                    if (available > 0) {
                        if (available > buffer.length - 1) {
                            available = buffer.length - 1;
                        }
                        int more = inputStream.read(buffer, 1, available);
                        if (more >= 0) {
                            size = size + more;
                        }
                    }
                    if (!clientSideClosed) {
                        processInputBuffer.putShort((short) size);
                        processInputBuffer.put(buffer, 0, size);
                        processInputBuffer.flip();
                        if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) {
                            LAUNCHER_LOGGER.finer("Waiting to send back stream data:\n" + new String(buffer, 0, size));
                        }
                        synchronized (processInputBuffer) {
                            clientSocketChannelKey.interestOps(clientSocketChannelKey.interestOps() | SelectionKey.OP_WRITE);
                            processInputBufferReady = true;
                            if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) {
                                LAUNCHER_LOGGER.finest("Waiting on stream data to be sent back...");
                            }
                            processInputBuffer.wait();
                            if (LAUNCHER_LOGGER.isLoggable(Level.FINEST)) {
                                LAUNCHER_LOGGER.finest("Waiting on stream data was sent back.");
                            }
                        }
                    }
                    if (clientSideClosed || (launcherSideClosed && closeTimeoutExpires <= System.currentTimeMillis())) {
                        if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Sent last packet from std io thread. Scheduling close of client side."); }
                        stop();
                    }
                } else {
                    if (LAUNCHER_LOGGER.isLoggable(Level.FINER)) { LAUNCHER_LOGGER.finer("Closing all from input std io thread"); }
                    stop();
                }
            } catch (IOException ioe) {
                LAUNCHER_LOGGER.log(Level.SEVERE, "Exception", ioe);
            } catch (Exception e) {
                LAUNCHER_LOGGER.log(Level.SEVERE, "Exception", e);
            }
        }
    }
}

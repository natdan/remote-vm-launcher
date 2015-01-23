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
package org.ah.java.remotevmlauncher.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ah.java.remotevmlauncher.ClassLoaderType;
import org.ah.java.remotevmlauncher.ClasspathUtils;
import org.ah.java.remotevmlauncher.JavaLoggingUtils;
import org.ah.java.remotevmlauncher.PathUtils;
import org.ah.java.remotevmlauncher.protocol.ArgumentsProcessor;
import org.ah.java.remotevmlauncher.protocol.CacheResponseProcessor;
import org.ah.java.remotevmlauncher.protocol.CacheResponseProcessor.ResourceResponse;
import org.ah.java.remotevmlauncher.protocol.LocalClasspathProcessor;
import org.ah.java.remotevmlauncher.protocol.MainClassProcessor;
import org.ah.java.remotevmlauncher.protocol.ProtocolStateMachine;
import org.ah.java.remotevmlauncher.protocol.ReadyProcessor;
import org.ah.java.remotevmlauncher.protocol.RemoteClasspathProcessor;
import org.ah.java.remotevmlauncher.protocol.ResourceProcessor;
import org.ah.java.remotevmlauncher.protocol.ResourceRequestProcessor;
import org.ah.java.remotevmlauncher.protocol.StartApplicationProcessor;
import org.ah.java.remotevmlauncher.protocol.StartRemoteVMProcessor;
import org.ah.java.remotevmlauncher.protocol.StreamProcessor;

/**
 * Main class to launch remote application on a machine with running agent.
 *
 * @author Daniel Sendula
 */
public class LaunchRemote {

    public static Logger LOGGER = Logger.getLogger("");
    public static Logger CLIENT_LOGGER = Logger.getLogger("> Client: ");

    protected static StreamProcessor DEFAULT_STREAM_PROCESSOR = new StreamProcessor() {
        @Override public void invoke() {
            System.out.print(getReceived());
        }
    };

    private InetSocketAddress remoteAgentSocketAddress;

    @SuppressWarnings("unused")
    private ClassLoaderType classLoaderType = ClassLoaderType.CACHED_RESOURCES;

    private Socket socket;
    private InputStream inputStream;
    private DataInputStream dataInputStream;
    private OutputStream outputStream;
    private DataOutputStream dataOutputStream;
    private ProtocolStateMachine stateMachine;
    private ReadyProcessor readyProcessor;

    private ResourceRequestProcessor resourceRequestProcessor;
    private StreamProcessor streamProcessor = DEFAULT_STREAM_PROCESSOR;
    private ResourceProcessor resourceProcessor = new ResourceProcessor();
    private LocalClasspathProcessor localClasspathProcessor = new LocalClasspathProcessor();
    private CacheResponseProcessor cacheResponseProcessor = new CacheResponseProcessor();
    private ArgumentsProcessor argumentsProcessor = new ArgumentsProcessor();
    private RemoteClasspathProcessor remoteClasspathProcessor = new RemoteClasspathProcessor();
    private MainClassProcessor mainClassProcessor = new MainClassProcessor();
    private StartApplicationProcessor startApplicationProcessor = new StartApplicationProcessor();
    private StartRemoteVMProcessor startRemoteVMProcessor = new StartRemoteVMProcessor();
    
    private Set<String> excludeClassPath = new LinkedHashSet<String>();

    public LaunchRemote() {
    }

    public InetSocketAddress getRemoteAgentSocketAddress() {
        return remoteAgentSocketAddress;
    }

    public void setRemoteAgentSocketAddress(InetSocketAddress remoteAgentSocketAddress) {
        this.remoteAgentSocketAddress = remoteAgentSocketAddress;
    }

    public void setup() throws IOException {
        try {
            socket = new Socket(remoteAgentSocketAddress.getAddress(), remoteAgentSocketAddress.getPort());
        } catch (ConnectException e) {
            System.err.println("Cannot connect to " + remoteAgentSocketAddress.getAddress() + ":" + remoteAgentSocketAddress.getPort());
            System.err.println("The Agent is probably not running on the remote machine.");
            System.err.println("Get agent.jar and run it with: ");
            System.err.println("  java -jar agent.jar ");
            if (remoteAgentSocketAddress.getPort() == 8999) {
                System.err.println("for default port 8999. Or if you want to run it at alternative port run it with:");
                System.err.println("  java -jar agent.jar -l <new-port>");
                System.err.println("But then you need to run this side launcher with that port as well.");
            } else {
                System.err.println("for default port 8999. Or if you want to run it at alternative port (as specified here) run it with:");
                System.err.println("  java -jar agent.jar -l " + remoteAgentSocketAddress.getPort());
            }
            System.exit(1);
        }

        inputStream = socket.getInputStream();
        dataInputStream = new DataInputStream(inputStream);

        outputStream = socket.getOutputStream();
        dataOutputStream = new DataOutputStream(outputStream);

        stateMachine = new ProtocolStateMachine(CLIENT_LOGGER);

        readyProcessor = new ReadyProcessor();
        stateMachine.getProcessorMap().put(ReadyProcessor.ID, readyProcessor);

        stateMachine.getProcessorMap().put("S0", streamProcessor);

        resourceRequestProcessor = new ResourceRequestProcessor() {
            @Override public void processRequest(String pathId, String resourceName) throws IOException {
                String pathString = localClasspathProcessor.getLocalClasspath().get(pathId);
                File path = new File(pathString);
                File resource = new File(path, resourceName);
                if (!resource.exists()) {
                    resourceProcessor.send(dataOutputStream, null, -1);
                } else {
                    FileInputStream fis = new FileInputStream(resource);
                    try {
                        resourceProcessor.send(dataOutputStream, fis, (int)resource.length());
                    } finally {
                        fis.close();
                    }
                }
            }
        };
        stateMachine.getProcessorMap().put(ResourceRequestProcessor.ID, resourceRequestProcessor);
    }

    public void start() throws IOException {
        LOGGER.info("Collecting classpath...");
        collectClasspath();
        LOGGER.info("Collecting resources...");
        collectResources();
        LOGGER.info("Starting remote VM...");
        if (StartRemoteVMProcessor.IMPLEMENTED) {
            startRemoteVMProcessor.send(dataOutputStream);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("  With remote VM arguments:");
                for (String a : startRemoteVMProcessor.getVmArgs()) {
                    LOGGER.fine("    " + a);
                }
                LOGGER.fine("  With launcher arguments:");
                for (String a : startRemoteVMProcessor.getLauncherArgs()) {
                    LOGGER.fine("    " + a);
                }
            }
        }
        LOGGER.info("Waiting for remote client to start...");
        synchronized (readyProcessor) {
            while (!readyProcessor.isReady()) {
                stateMachine.processInput(dataInputStream);
            }
        }
        LOGGER.info("Remote client started.");

        LOGGER.info("Sending remote classpath...");
        remoteClasspathProcessor.send(dataOutputStream);

        LOGGER.info("Sending classpath...");
        localClasspathProcessor.send(dataOutputStream);

        LOGGER.info("Sending resource details...");
        cacheResponseProcessor.send(dataOutputStream);

        LOGGER.info("Sending arguments...");
        argumentsProcessor.send(dataOutputStream);
        if (LOGGER.isLoggable(Level.FINE)) {
            for (String a : argumentsProcessor.getArguments()) {
                LOGGER.fine("    " + a);
            }
        }

        LOGGER.info("Sending main class \"" + mainClassProcessor.getMainClass() + "\"...");
        mainClassProcessor.send(dataOutputStream);

        LOGGER.info("Sending signal for client to start application...");
        startApplicationProcessor.send(dataOutputStream);

        LOGGER.info("Output form the client:");
        LOGGER.info("----------------------------------------------------------------------------------------------");

        try {
            while (true) {
                stateMachine.processInput(dataInputStream);
            }
        } catch (IOException e) {
        }

        LOGGER.info("----------------------------------------------------------------------------------------------");
        LOGGER.info("Finished.");
    }

    protected void collectClasspath() throws IOException {
        int nextId = 1;
        File hereDir = new File("");
        String herePath = hereDir.getAbsolutePath().replace("\\", "/");
//        localClasspathProcessor.getLocalClasspath().put("HOME", herePath);
        String[] herePathComponents = herePath.split("/");
        String launchersPath = ClasspathUtils.obtainPathOfClass(getClass());
        if (!launchersPath.endsWith(".jar")) {
            launchersPath = null;
        }
        ClassLoader classLoader = LaunchRemote.class.getClassLoader();
        if (classLoader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
            URL[] urls = urlClassLoader.getURLs();
            for (URL url : urls) {
                if (!url.getProtocol().equals("file")) {
                    throw new UnsupportedOperationException("Only file: protocol URLs in classpath are implemented at this point in time.");
                }
                File file = new File(url.getFile());
                String filePath = file.getAbsolutePath().replace("\\", "/");
                // Don't sent remotevm-client*.jar over the wire.
                if (!filePath.equals(launchersPath)) {
                    String[] filePathComponents = filePath.split("/");
                    String relativisedPath = PathUtils.relativisePath(herePathComponents, filePathComponents);
                    
                    if (!excludeClassPath.contains(relativisedPath) && !excludeClassPath.contains(filePath)) {
                        String pathId = localClasspathProcessor.getLocalClasspath().get(relativisedPath);
                        if (pathId == null) {
                            pathId = Integer.toString(nextId);
                            localClasspathProcessor.getLocalClasspath().put(pathId, relativisedPath);
                            nextId = nextId + 1;
                        }
                    }
                }
            }
            if (LOGGER.isLoggable(Level.FINE)) {
                for (Map.Entry<String, String> cpe : localClasspathProcessor.getLocalClasspath().entrySet()) {
                    LOGGER.fine("    " + cpe.getKey() + ": " + cpe.getValue());
                }
            }
        } else {
            throw new UnsupportedOperationException("Class loader is not of URLClassLoader type. This type of class loader is not yet supported.");
        }
    }

    protected void collectResources() {
        for (Map.Entry<String, String> entry : localClasspathProcessor.getLocalClasspath().entrySet()) {
            collectResources(entry.getKey(), new File(entry.getValue()), "");
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            for (ResourceResponse r : cacheResponseProcessor.getResources()) {
                LOGGER.finer("    " + r.pathId + ": (" + r.hash + ", " + r.length + ") " + r.name);
            }
        }
    }

    protected void collectResources(String pathId, File path, String resource) {
        File file = new File(path, resource);
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (!f.equals(".") && !f.equals("..")) {
                    File resourceFile;
                    if (resource.equals("")) {
                        resourceFile = new File(f.getName());
                    } else {
                        resourceFile =  new File(new File(resource), f.getName());
                    }
                    collectResources(pathId, path, resourceFile.getPath());
                }
            }
        } else {
            ResourceResponse rr = new ResourceResponse();
            rr.pathId = pathId;
            rr.name = resource.replace("\\", "/");
            rr.hash = file.lastModified();
            rr.length = file.length();
            cacheResponseProcessor.getResources().add(rr);
        }
    }

    public static void main(String[] args) throws Exception {
        LaunchRemote client = new LaunchRemote();

        int debugLevel = 0;

        boolean remoteClasspath = false;
        boolean excludeClasspath = false;
        boolean addressAndPort = true;
        boolean mainClass = true;
        boolean arguments = false;
        boolean allRead = false;
        boolean debugLevelFlag = false;
        boolean remoteDebugPortFlag = false;
        boolean remoteVMarg = false;
        for (String arg : args) {
            if (!arguments) {
                if (remoteClasspath) {
                    client.remoteClasspathProcessor.getRemoteClasspath().add(arg);
                    remoteClasspath = false;
                } else if (excludeClasspath) {
                    client.excludeClassPath.add(arg);
                    excludeClasspath = false;
                } else if (debugLevelFlag) {
                    debugLevel = Integer.parseInt(arg);
                    client.startRemoteVMProcessor.getLauncherArgs().add("-d");
                    client.startRemoteVMProcessor.getLauncherArgs().add(Integer.toString(Integer.parseInt(arg)));
                    debugLevelFlag = false;
                } else if (remoteDebugPortFlag) {
                    client.startRemoteVMProcessor.setRemoteDebugPort(Integer.parseInt(arg));
                    remoteDebugPortFlag = false;
                } else if (remoteVMarg) {
                    client.startRemoteVMProcessor.getVmArgs().add(arg);
                    remoteVMarg = false;
                } else if ("-rcp".equals(arg) || "--remote-classpath".equals(arg)) {
                    remoteClasspath = true;
                } else if ("-ecp".equals(arg) || "--exclude-classpath".equals(arg)) {
                    excludeClasspath = true;
                } else if ("-d".equals(arg) || "--debug".equals(arg)) {
                    debugLevelFlag = true;
                } else if ("-rdp".equals(arg) || "--remote-debug-port".equals(arg)) {
                    remoteDebugPortFlag = true;
                } else if ("-rds".equals(arg) || "--remote-debug-suspend".equals(arg)) {
                    client.startRemoteVMProcessor.setRemoteDebugSuspendAtStart(true);
                } else if ("-rvma".equals(arg) || "--remote-VM-argment".equals(arg)) {
                    remoteVMarg = true;
                } else if ("-h".equals(arg) || "--help".equals(arg)) {
                    printHelp();
                    System.exit(0);
                } else if (addressAndPort) {
                    int i = arg.indexOf(':');
                    if (i < 0) {
                        client.setRemoteAgentSocketAddress(new InetSocketAddress(Integer.parseInt(arg)));
                    } else {
                        client.setRemoteAgentSocketAddress(new InetSocketAddress(arg.substring(0, i), Integer.parseInt(arg.substring(i + 1))));
                    }
                    addressAndPort = false;
                    mainClass = true;
                } else if (mainClass) {
                    client.mainClassProcessor.setMainClass(arg);
                    mainClass = false;
                    allRead = true;
                } else if (allRead) {
                    if ("--".equals(arg)) {
                        allRead = false;
                        arguments = true;
                    } else {
                        System.out.println("ERROR: unknown argument '" + arg + "'");
                        System.out.println();
                        printHelp();
                        System.exit(1);
                    }
                } else {
                    System.out.println("ERROR: unknown argument '" + arg + "'");
                    System.out.println();
                    printHelp();
                    System.exit(1);
                }
            } else {
                client.argumentsProcessor.getArguments().add(arg);
            }
        }
        if (addressAndPort) {
            System.out.println("ERROR: missing address and port argument.");
            System.out.println();
            printHelp();
            System.exit(1);
        }
        if (mainClass) {
            System.out.println("ERROR: missing main class argument.");
            System.out.println();
            printHelp();
            System.exit(1);
        }

        JavaLoggingUtils.setupSimpleConsoleLogging(debugLevel);

        client.setup();

        client.start();
    }

    public static void printHelp() {
        System.out.println("Remote VM Launcher usage:");
        System.out.println("");
        System.out.println("java -jar remotevmlauncher-client.jar {options} [address:]port mainClass");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("");
        System.out.println("  -rcp|--remote-classpath classpath-entry");
        System.out.println("                      classpath entry as seen from remote machine");
        System.out.println("                      where agent is running.");
        System.out.println("  -ecp|--exclude-classpath classpath-entry");
        System.out.println("                      classpath entry not to be send to the remote");
        System.out.println("                      machine where agent is running.");
        System.out.println("  -d|--debug level    debug level from 0 to 4. Default: 0");
        System.out.println("  -rdp|--remote-debug-port port ");
        System.out.println("                      If port is specified remote VM will be launched");
        System.out.println("                      in debug mode at the specified port.");
        System.out.println("  -rds|--remote-debug-suspend ");
        System.out.println("                      If port is specified and if this flag as well");
        System.out.println("                      remote VM will be suspended.");
        System.out.println("  -h|--help           this help.");
        System.out.println("");
        System.out.println("If launcher is used from an IDE, and ");
        System.out.println("   org.ah.java.remotevmlauncher.client.LaunchRemote class is directly used as");
        System.out.println("main class, then supplied classpath will automatically used (provided that it");
        System.out.println("is defined as URLClassLoader or descendant class) on the remote side.");
        System.out.println("");
        System.out.println("Note: if remote debug port is specified launcher's VM is started with:");
        System.out.println("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=(y|n),address=<port>");
        System.out.println("depending on port and if suspend is specified or not.");
   }

}

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
package org.ah.java.remotevmlauncher.launcher;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.ah.java.remotevmlauncher.AppClassLoader;
import org.ah.java.remotevmlauncher.ClassLoaderType;
import org.ah.java.remotevmlauncher.JavaLoggingUtils;
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

/**
 * The Launcher.
 *
 * It is in 'common' part of the project as it is easier for testing. When it was in agent and depending to common, it was
 * hard to collect all needed classpath for launching remote VM. Now being in common it can access all other parts of common
 * project classpath. When deployed in uber jar (maven-shade-plugin) it doesn't matter.
 * 
 * @author Daniel Sendula
 */
public class Launcher {

    private static final String UNDEFINED = "---";
    public static Logger LOGGER = Logger.getLogger("");

    private int port;
    private int debugLevel = 0;

    private Socket socket;
    private InputStream inputStream;
    private DataInputStream dataInputStream;
    private OutputStream outputStream;
    private DataOutputStream dataOutputStream;
    private ProtocolStateMachine stateMachine;
    private ClassLoaderType classLoaderType = ClassLoaderType.CACHED_RESOURCES;

    private StartApplicationProcessor startProcessor;
    private MainClassProcessor mainClassProcessor;
    private ArgumentsProcessor argumentsProcessor;
    private LocalClasspathProcessor localClasspathProcessor;
    private RemoteClasspathProcessor remoteClasspathProcessor;
    private CacheResponseProcessor cacheResponseProcessor;
    private ResourceProcessor resourceProcessor;
    private ReadyProcessor readyProcessor;
    private ResourceRequestProcessor resourceRequestProcessor;

    private Thread clientResponseProcessorThread;
    private Thread appThread;
    private Class<?> mainClass;
    private ClassLoader contextClassLoader;
    private File globalCacheDir;
    private File cacheDir;

    // TODO there must be better way of sorting this out.
    private FileOutputStream sideEffectFileOutputStream;

    // RemotePath -> LocalId(LocalPath)
    private Map<String, String> localPathTranslation = new LinkedHashMap<String, String>();

    private boolean applicationStarted = false;

    private Logger logger = Logger.getLogger("< Launcher: ");

    private Statistics statistics = new Statistics();

    public Launcher() {
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDebugLevel() {
        return debugLevel;
    }

    public void setDebugLevel(int debugLevel) {
        this.debugLevel = debugLevel;
    }

    public void setup() throws IOException {
        socket = new Socket("127.0.0.1", port);

        inputStream = socket.getInputStream();
        dataInputStream = new DataInputStream(inputStream);

        outputStream = socket.getOutputStream();
        dataOutputStream = new DataOutputStream(outputStream);

        if (debugLevel > 0) {
            JavaLoggingUtils.setupSimpleConsoleLogging(debugLevel);

            stateMachine = new ProtocolStateMachine(logger);
        } else {
            stateMachine = new ProtocolStateMachine();
        }

        resourceRequestProcessor = new ResourceRequestProcessor();
        readyProcessor = new ReadyProcessor();
        cacheResponseProcessor = new CacheResponseProcessor();
        mainClassProcessor = new  MainClassProcessor();
        argumentsProcessor = new ArgumentsProcessor();
        remoteClasspathProcessor = new RemoteClasspathProcessor();
        localClasspathProcessor = new LocalClasspathProcessor();
        resourceProcessor = new ResourceProcessor() {
            @Override public void loadResource(DataInputStream dis, int size) throws IOException {
                byte[] buffer = new byte[10240];
                while (size > 0) {
                    int s = size;
                    if (size > buffer.length) { s = buffer.length; }
                    int r = dis.read(buffer, 0, s);
                    if (r > 0) {
                        size = size - r;
                    } else {
                        // TODO add more to this error!!!
                        throw new RuntimeException("Premature end of file ");
                    }
                    sideEffectFileOutputStream.write(buffer, 0, r);
                }
                synchronized (sideEffectFileOutputStream) {
                    sideEffectFileOutputStream.notifyAll();
                }
            }
        };

        startProcessor = new StartApplicationProcessor() { 
            @Override public void receive(DataInputStream dis) { 
                super.receive(dis); 
                synchronized (startProcessor) {
                    startProcessor.notifyAll();
                }
            }
        };

        globalCacheDir = gloabalCacheDir();

        stateMachine.getProcessorMap().put(CacheResponseProcessor.ID, cacheResponseProcessor);
        stateMachine.getProcessorMap().put(StartApplicationProcessor.ID, startProcessor);
        stateMachine.getProcessorMap().put(MainClassProcessor.ID, mainClassProcessor);
        stateMachine.getProcessorMap().put(ArgumentsProcessor.ID, argumentsProcessor);
        stateMachine.getProcessorMap().put(RemoteClasspathProcessor.ID, remoteClasspathProcessor);
        stateMachine.getProcessorMap().put(LocalClasspathProcessor.ID, localClasspathProcessor);
        stateMachine.getProcessorMap().put(ResourceProcessor.ID, resourceProcessor);

        clientResponseProcessorThread = new Thread(new Runnable() { public void run() { processStarterLoop(); }});
        clientResponseProcessorThread.setDaemon(true);
    }

    private File gloabalCacheDir() {
        File dir = new File(".remotevm");
        if (dir.exists() && dir.isFile()) {
            if (!dir.delete()) {
                throw new RuntimeException("Cannot delete file " + dir.getAbsolutePath());
            }
        }
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Cannot create dir " + dir.getAbsolutePath());
            }
        }
        return dir;
    }

    private File cacheDir() {
        File dir = new File(globalCacheDir, mainClassProcessor.getMainClass());
        if (dir.exists() && dir.isFile()) {
            if (!dir.delete()) {
                throw new RuntimeException("Cannot delete file " + dir.getAbsolutePath());
            }
        }
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("Cannot create dir " + dir.getAbsolutePath());
            }
        }
        return dir;
    }

    public void start() {
        clientResponseProcessorThread.start();
        synchronized (startProcessor) {
            try {
                startProcessor.wait();
            } catch (InterruptedException ignore) { }
        }
        prepareForStartApplication();
        synchronized (this) {
            try {
                while (!applicationStarted) {
                    wait();
                }
            } catch (InterruptedException ignore) { }
        }
    }

    public void processStarterLoop() {
        try {
            readyProcessor.send(dataOutputStream);

            while (true) {
                stateMachine.processInput(dataInputStream);
            }
//        } catch (EOFException e) {
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void prepareForStartApplication() {
        try {
            if (debugLevel > 1) {
                LOGGER.info("Ready to start application");
                LOGGER.info("Remote class path: " + remoteClasspathProcessor.getRemoteClasspath());
                LOGGER.info("Arguments: " + argumentsProcessor.getArguments());
                LOGGER.info("Main class: " + mainClassProcessor.getMainClass());
            }

            if (classLoaderType == ClassLoaderType.CACHED_RESOURCES) {
                cacheResources();
            } else if (classLoaderType == ClassLoaderType.CACHE_REMOTE_RESOURCES) {
            } else if (classLoaderType == ClassLoaderType.REMOTE_RESOURCES) {
                AppClassLoader appClassLoader = new AppClassLoader(remoteClasspathProcessor.getRemoteClasspath());
                contextClassLoader = appClassLoader;
            }

            mainClass = contextClassLoader.loadClass(mainClassProcessor.getMainClass());

            appThread = new Thread(new Runnable() { public void run() { startApplication(); }});
            appThread.setName("StartedThread");
            appThread.setDaemon(false);
            appThread.setContextClassLoader(contextClassLoader);
            appThread.start();
        } catch (Throwable t) {
            t.printStackTrace();
//            System.exit(1);
        }
    }

    private void cacheResources() throws IOException {
        cacheDir = cacheDir();
        loadLocalDirectoryTranslation();
        removeOrphanClasspaths();
        defineLocalTranslations();
        saveLocalDirectoryTranslation();
        removeOldFiles();
        updateExistingFiles();
        createClassLoader();
    }

    @SuppressWarnings("resource")
    private void loadLocalDirectoryTranslation() {
        File pathTranslation = new File(cacheDir, ".dirs");
        if (pathTranslation.exists()) {
            try {
                FileReader reader = new FileReader(pathTranslation);
                try {
                    BufferedReader lineReader = new BufferedReader(reader);
                    String line = lineReader.readLine();
                    while (line != null) {
                        int i = line.indexOf(',');
                        if (i < 0) {
                            throw new RuntimeException("Bad local directory translation file. Line: " + line);
                        }
                        String localId = line.substring(0, i);
                        String remotePath = line.substring(i + 1);
                        localPathTranslation.put(remotePath, localId);
                        line = lineReader.readLine();
                    }
                } finally {
                    reader.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void saveLocalDirectoryTranslation() {
        File pathTranslation = new File(cacheDir, ".dirs");
        try {
            FileWriter writer = new FileWriter(pathTranslation);
            try {
                PrintWriter out = new PrintWriter(writer);
                for (Map.Entry<String, String> entry : localPathTranslation.entrySet()) {
                    out.println(entry.getValue() + "," + entry.getKey());
                }
                out.close();
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeOrphanClasspaths() {
        // First remove all that we didn't get this time
        Set<String> allRecordedRemotePaths = new HashSet<String>(localPathTranslation.keySet());
        for (String remotePath : localClasspathProcessor.getLocalClasspath().values()) {
            allRecordedRemotePaths.remove(remotePath);
        }
        localPathTranslation.keySet().removeAll(allRecordedRemotePaths);

        // Now add new translations
        for (String remotePath : localClasspathProcessor.getLocalClasspath().values()) {
            if (!localPathTranslation.containsKey(remotePath)) {
                localPathTranslation.put(remotePath, UNDEFINED);
            }
        }
    }

    private void defineLocalTranslations() {
        for (ResourceResponse response : cacheResponseProcessor.getResources()) {
            if ("".equals(response.name)) {
                String remotePathId = response.pathId;
                String remotePath = localClasspathProcessor.getLocalClasspath().get(remotePathId);
                String translation = localPathTranslation.get(remotePath);
                if (translation == null || translation == UNDEFINED) {
                    createEntryFromName(remotePath);
                }
            }
        }
        for (Map.Entry<String, String> entry : localPathTranslation.entrySet()) {
            String remotePath = entry.getKey();
            String localId = entry.getValue();
            if (localId == UNDEFINED) {
                createEntryFromName(remotePath);
            }
        }
    }

    private void createEntryFromName(String remotePath) {
        String fileName;
        int i = remotePath.lastIndexOf('/');
        if (i >= 0) {
            fileName = remotePath.substring(i + 1);
        } else {
            fileName = remotePath;
        }
        String translation = fileName;
        i = 1;
        while (localPathTranslation.values().contains(translation)) {
            translation = i + "_" + fileName;
            i++;
        }
        localPathTranslation.put(remotePath, translation);
    }

    private void createClassLoader() {
        URL[] urls = new URL[remoteClasspathProcessor.getRemoteClasspath().size() + localClasspathProcessor.getLocalClasspath().size()];
        int i = 0;
        for (String remoteClasspath : remoteClasspathProcessor.getRemoteClasspath()) {
            try {
                urls[i] = new URL(remoteClasspath);
            } catch (MalformedURLException e) {
                try {
                    urls[i] = new File(remoteClasspath).toURI().toURL();
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            }
            i++;
        }
        for (String remotePathId : localClasspathProcessor.getLocalClasspath().keySet()) {
            String remotePath = localClasspathProcessor.getLocalClasspath().get(remotePathId);
            String localPath = localPathTranslation.get(remotePath);
            try {
                urls[i] = new File(cacheDir, localPath).toURI().toURL();
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
            i++;
        }
        contextClassLoader = new URLClassLoader(urls);
    }

    private void removeOldFiles() {
        // TODO this does not remove old directories or orphaned, empty directories. Fix it!
        Set<File> existingFiles = new HashSet<File>();
        collectFiles(existingFiles);
        for (ResourceResponse r : cacheResponseProcessor.getResources()) {
            String remotePathId = r.pathId;
            String remotePath = localClasspathProcessor.getLocalClasspath().get(remotePathId);
            String localPath = localPathTranslation.get(remotePath);
            File localFile = new File(localPath);
            File file = new File(localFile, r.name);
            existingFiles.remove(file);
        }
    }

    private void collectFiles(Set<File> existingFiles) {
        for (String path : localPathTranslation.values()) {
            File file = new File(path);
            collectFiles(existingFiles, file);
        }
    }

    private void collectFiles(Set<File> existingFiles, File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                collectFiles(existingFiles, f);
            }
        } else {
            existingFiles.add(file);
        }
    }

    private void updateExistingFiles() throws IOException {
        statistics.totalResorces = cacheResponseProcessor.getResources().size();
        for (ResourceResponse r : cacheResponseProcessor.getResources()) {
            String remotePathId = r.pathId;
            String remotePath = localClasspathProcessor.getLocalClasspath().get(remotePathId);
            String localPath = localPathTranslation.get(remotePath);
            File localFile = new File(cacheDir, localPath);
            File file = new File(localFile, r.name);
            // Small hack to reduce precision to 1s as windows doesn't go to millis
            long ourHash = (file.lastModified() / 1000) * 1000;
            long theirHash = (r.hash / 1000) * 1000;
            if (!file.exists() || file.length() != r.length || ourHash != theirHash) {
                logger.fine("Updating resource " + file.toString() + ", len(" + file.length() + "/" + r.length + ") hash(" + file.lastModified() + "/" + r.hash + ")");
                fetchResource(file, remotePathId, r.name, r.length, r.hash);
                statistics.updatedResources = statistics.updatedResources + 1;
                if (debugLevel > 2) {
                    logger.fine("Updated resource " + file.toString());
                }
            }
        }
    }

    private void fetchResource(File file, String remotePathId, String name, long length, long lastModified) throws IOException {
        File dir = file.getParentFile();
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("Cannot create dir " + dir.getAbsolutePath());
            }
        }
        sideEffectFileOutputStream = new FileOutputStream(file);
        try {
            synchronized (sideEffectFileOutputStream) {
                resourceRequestProcessor.setPathId(remotePathId);
                resourceRequestProcessor.setResourceName(name);
                resourceRequestProcessor.send(dataOutputStream);
                // TODO this needs better inter-thread communication/synchronisation
                try {
                    sideEffectFileOutputStream.wait();
                } catch (InterruptedException ignore) {}
            }
        } finally {
            sideEffectFileOutputStream.close();
            file.setLastModified(lastModified);
        }
    }

    public void startApplication() {
        synchronized (this) {
            applicationStarted = true;
            notifyAll();
        }

        if (debugLevel > 0) {
            LOGGER.info(String.format("Removed old files/dirs (%s, %s), updated/total resources (%s/%s)", statistics.deletedFiles, statistics.deletedDirs, statistics.updatedResources, statistics.totalResorces));
            LOGGER.info("Starting " + mainClass + ":");
        }
        try {
            Method mainMethod = mainClass.getMethod("main", new String[0].getClass());
            Object[] args = new Object[]{argumentsProcessor.getArguments().toArray(new String[0])};
            mainMethod.invoke(null, args);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();

        boolean debugLevelFlag = false;

        for (String arg : args) {
            if (debugLevelFlag) {
                launcher.setDebugLevel(Integer.parseInt(arg));
                debugLevelFlag = false;
            } else if ("-d".equals(arg)) {
                debugLevelFlag = true;
            } else {
                launcher.setPort(Integer.parseInt(arg));
            }
        }

        launcher.setup();
        launcher.start();
    }

}

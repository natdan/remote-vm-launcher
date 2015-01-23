package org.ah.java.remotevmlauncher;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class AppClassLoader extends ClassLoader {

    private URLClassLoader contextClassLoader;

    public AppClassLoader(List<String> remoteClasspath) throws MalformedURLException {
        super(createContextClassLoader(remoteClasspath));
        contextClassLoader = (URLClassLoader)getParent();
    }

    public ClassLoader getContextClassLoader() throws MalformedURLException {
        return contextClassLoader;
    }
    
    public static ClassLoader createContextClassLoader(List<String> remoteClasspath) throws MalformedURLException {
        URL[] urls = new URL[remoteClasspath.size()];
        for (int i = 0 ; i < urls.length; i++) {
            try {
                urls[i] = new URL(remoteClasspath.get(i));
            } catch (MalformedURLException e) {
                urls[i] = new File(remoteClasspath.get(i)).toURI().toURL();
            }
        }

        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

}

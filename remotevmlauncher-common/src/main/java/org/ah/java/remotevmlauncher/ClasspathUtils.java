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
package org.ah.java.remotevmlauncher;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;

/**
 * Classpath utilities
 *
 * @author Daniel Sendula
 */
public class ClasspathUtils {

    public static String obtainInitialPath() throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader)contextClassLoader).getURLs();
            StringBuilder sb = new StringBuilder();
            for (URL url : urls) {
                if ("file".equals(url.getProtocol())) {
                    if (sb.length() > 0) {
                        sb.append(File.pathSeparator);
                    }
                    sb.append(url.getPath().replace("\\", "/"));
                } else {
                    throw new RuntimeException("Cannot handle protocol " + url.getProtocol());
                }
            }
            return sb.toString();
        } else {
            throw new RuntimeException("Class is loaded from a file, but classloader is not URLClassLoader; " + contextClassLoader.getClass().getName());
            // It is probably wrong but that's the only fallback we have...
            // return new File(root.getFile()).getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath();
        }
    }

    public static String obtainPathOfClass(Class<?> cls) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        String className = cls.getName().replace('.', '/') + ".class";
        URL root = contextClassLoader.getResource(className);
        if ("jar".equals(root.getProtocol())) {
            String file = URLDecoder.decode(root.getFile(), "UTF-8").replace("\\", "/");
            URL url = new URL(file);
            if (!url.getProtocol().startsWith("file")) {
                throw new RuntimeException("Cannot handle protocol from where this jar is loaded; " + root);
            }
            File f = new File(url.getFile());
            file = f.getAbsolutePath().replace("\\", "/");
            //            file = url.getPath();
            int i = file.lastIndexOf('!');
            file = file.substring(0, i);
            return file;
        } else if ("file".equals(root.getProtocol())) {
            return new File(root.getPath()).getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getAbsolutePath();
        } else {
            throw new RuntimeException("Cannot find classpath for this class; class=" + cls.getName() + "; url=" + root.toString());
        }
    }
}

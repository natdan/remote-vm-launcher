package org.ah.java.remotevmlauncher;

public enum ClassLoaderType {

    CACHED_RESOURCES, // Caches all resources (and jars) in local cache
    REMOTE_RESOURCES, // Reads all resources remotely.
    CACHE_REMOTE_RESOURCES // Tries to cache on the fly.

}

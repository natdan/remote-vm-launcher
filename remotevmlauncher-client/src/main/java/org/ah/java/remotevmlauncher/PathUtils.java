package org.ah.java.remotevmlauncher;

public class PathUtils {

    public static String relativisePath(String base, String file) {
        return relativisePath(base.split("/"), file.split("/"));
    }

    public static String relativisePath(String[] base, String[] file) {
        int skip = 0;
        while (skip < base.length 
                && skip < file.length
                && base[skip].equals(file[skip])) {
            skip = skip + 1;
        }

        StringBuilder res = new StringBuilder();

        if ("".equals(file[0]) && skip <= 1) {
            res.append("/");
        } else {
            for (int i = skip; i < base.length; i++) {
                res.append("../");
            }
        }

        boolean first = true;
        for (int i = skip; i < file.length; i++) {
            if (first) { first = false; } else { res.append("/"); }
            res.append(file[i]);
        }

        return res.toString();
    }

}

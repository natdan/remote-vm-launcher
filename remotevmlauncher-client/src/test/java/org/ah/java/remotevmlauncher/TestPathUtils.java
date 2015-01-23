package org.ah.java.remotevmlauncher;

import org.junit.Assert;
import org.junit.Test;

public class TestPathUtils {

    @Test
    public void testAbsolutePathSubDir() {
        String basePath = "/just/here";
        String subPath = "/just/here/subdir/2";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("subdir/2", actual);
    }

    @Test
    public void testRelativePathSubDir() {
        String basePath = "just/here";
        String subPath = "just/here/subdir/2";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("subdir/2", actual);
    }

    @Test
    public void testRelativeShortPathSubDir() {
        String basePath = "here";
        String subPath = "here/subdir/2";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("subdir/2", actual);
    }

    @Test
    public void testAbsoluteSameLevel() {
        String basePath = "/just/here";
        String subPath = "/just/there";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("../there", actual);
    }

    @Test
    public void testRelativeSameLevel() {
        String basePath = "just/here";
        String subPath = "just/there";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("../there", actual);
    }

    @Test
    public void testAbsoluteSecondLevel() {
        String basePath = "/just/here/base";
        String subPath = "/just/there/base";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("../../there/base", actual);
    }

    @Test
    public void testRelativeSecondLevel() {
        String basePath = "just/here/base";
        String subPath = "just/there/base";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("../../there/base", actual);
    }

    @Test
    public void testAbsoluteDifferentPaths() {
        String basePath = "/just/here";
        String subPath = "/over/there";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("/over/there", actual);
    }

    @Test
    public void testRelativeDifferentPaths() {
        String basePath = "just/here";
        String subPath = "over/there";

        String actual = PathUtils.relativisePath(basePath, subPath);

        Assert.assertEquals("../../over/there", actual);
    }
}

package com.luxoft.fabric.utils;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests for MiscUtils utility class
 */
public class MiscUtilsTest {

    @Test
    public void testResolveFilePositive() {
        assertTrue(MiscUtils.resolveFile("README.md", ".").contains("README.md"));
        assertTrue(MiscUtils.resolveFile("*.md", ".").contains("README.md"));
        assertTrue(MiscUtils.resolveFile("fabric-connector/*.md", "..").contains("README.md"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveFileByFileNameNegative() {
        MiscUtils.resolveFile("README.md2", ".");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveFileByGlobNegative() {
        MiscUtils.resolveFile("*.md2", ".");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testResolveFileByGlobMoreThen1Negative() {
        MiscUtils.resolveFile("*", ".");
    }
}
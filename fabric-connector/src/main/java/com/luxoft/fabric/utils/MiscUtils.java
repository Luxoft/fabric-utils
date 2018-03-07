package com.luxoft.fabric.utils;

import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by osesov on 13.09.17
 */
public class MiscUtils
{
    public static File combinePath(List<String> paths)
    {
        final Iterator<String> iterator = paths.iterator();
        if (!iterator.hasNext())
            return new File(".");
        File file = new File(iterator.next());

        while(iterator.hasNext()) {
            file = new File(file, iterator.next());
        }

        return file/*.getPath()*/;
    }

    /**
     * Resolve file path by name and directory. Supports absolute and relatives paths.
     * Also supports globs pattern in file name:
     * <pre>
     *      String filePath = MiscUtils.resolveFile("*.java", dir)
     * <pre>
     *
     * @param fileName file name
     * @param topDir file dir
     *
     * @return string with file path
     * @throws IllegalArgumentException if file not found
     *
     * @see Files#newDirectoryStream(java.nio.file.Path, java.lang.String)
     */
    public static String resolveFile(String fileName, String topDir) {
        Path path;
        if (Paths.get(fileName).isAbsolute()) {
            path = Paths.get(fileName);
        } else {
            path = Paths.get(topDir, fileName);
        }

        if (!Files.exists(path)) {
            // Trying to resolve file using glob pattern
            List<Path> paths = getDirectoryList(path.getParent(), path.getFileName().toString());

            if (paths.size() == 1) {
                path = paths.get(0);

            } else if (paths.size() > 1) {
                throw new IllegalArgumentException("Found more than 1 file, name: " + fileName + " dir:" + topDir);

            } else {
                throw new IllegalArgumentException("File does not exist: " + path.toUri().getPath());
            }
        }

        return path.toUri().getPath();
    }

    /**
     * Wrapper of {@link Files#newDirectoryStream(java.nio.file.Path, java.lang.String)} that returns list and not
     * throws exception.
     *
     * @param dir dir
     * @param glob glob
     * @return list of paths
     */
    public static List<Path> getDirectoryList(Path dir, String glob) {
        try {
            return Lists.newArrayList(Files.newDirectoryStream(dir, glob));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}

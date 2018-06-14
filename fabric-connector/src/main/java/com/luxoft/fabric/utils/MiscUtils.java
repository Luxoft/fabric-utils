package com.luxoft.fabric.utils;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        File file = new File(fileName);
        if (!file.isAbsolute()) {
            file = new File(topDir, fileName);
        }
        if (!file.exists()) {
            // Trying to resolve file using glob pattern
            List<File> files = getDirectoryList(file.getParentFile(), file.getName());

            if (files.size() == 1) {
                file = files.get(0);

            } else if (files.size() > 1) {
                throw new IllegalArgumentException("Found more than 1 file, name: " + fileName + " dir:" + topDir);

            } else {
                throw new IllegalArgumentException("File does not exist: " + file.getAbsolutePath());
            }
        }
        return file.getAbsoluteFile().getAbsolutePath();
    }

    /**
     * Wrapper of {@link Files#newDirectoryStream(java.nio.file.Path, java.lang.String)} that returns list and not
     * throws exception.
     *
     * @param dir dir
     * @param glob glob
     * @return list of paths
     */
    public static List<File> getDirectoryList(File dir, String glob) {
        try {
            return StreamSupport.stream(Files.newDirectoryStream(Paths.get(dir.toURI()), glob).spliterator(), false)
                    .map(Path::toFile).collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}

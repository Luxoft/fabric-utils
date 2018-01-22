package com.luxoft.fabric.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public static String resolveFile(String fileName, String topDir) {
        Path path;
        if (Paths.get(fileName).isAbsolute()) {
            path = Paths.get(fileName);
        } else {
            path = Paths.get(topDir, fileName);
        }

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File does not exist: " + path.toUri().getPath());
        }

        return path.toUri().getPath();
    }
}

package com.luxoft.fabric.utils;

import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

    public static String resolveFile(String fileName, Map<String, String> context) {
        final Path path = Paths.get(fileName);
        List<String> finalPath = new ArrayList<>();
        for (int j = 0; j < path.getNameCount(); ++j) {
            String sub = path.subpath(j, j + 1).toString();

            if (context != null && context.containsKey(sub))
                sub = context.get(sub);
            else {
                File directory = combinePath(finalPath);
                final FileFilter fileFilter = new WildcardFileFilter(sub);

                File[] matches = directory.listFiles(fileFilter);

                if (null == matches || matches.length == 0) {
                    throw new RuntimeException(String.format("Unable to match '%s' in '%s'", sub, directory.getAbsoluteFile().getPath()));
                }

                if (matches.length != 1) {
                    throw new RuntimeException(String.format("Single file should match '%s' in %s' (found %d)", sub, directory.getAbsoluteFile().getPath(), matches.length));
                }

                sub = matches[0].getName();
            }

            finalPath.add(sub);
        }

        return combinePath(finalPath).getPath();
    }
}

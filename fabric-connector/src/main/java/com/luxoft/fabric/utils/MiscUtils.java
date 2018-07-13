package com.luxoft.fabric.utils;

import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.ChaincodeID;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
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

    public static boolean equals(ChaincodeID chaincodeID, Query.ChaincodeInfo chaincodeInfo) {
        return Objects.equals(chaincodeID.getName(), chaincodeInfo.getName()) &&
                Objects.equals(chaincodeID.getVersion(), chaincodeInfo.getVersion());
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static <T> T runWithRetries(int maxRetries, int delaySec, ThrowingSupplier<T> t) throws InterruptedException {
        int count = 0;
        RuntimeException ex = new RuntimeException("Failed to get in " + maxRetries + " times with delay " + delaySec);
        if (maxRetries < 0)
            maxRetries = 0;
        while (count++ <= maxRetries) {
            try {
                return t.get();
            } catch (Exception e) {
                ex.addSuppressed(new RuntimeException("Failed to get " + count + " time", e));
                Thread.sleep(delaySec * 1000);
            }
        }
        throw ex;
    }
}

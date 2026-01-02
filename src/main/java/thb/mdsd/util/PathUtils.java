package thb.mdsd.util;

import lombok.NonNull;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class PathUtils {

    /**
     * Checks if a given path exists and represents a directory.
     * @param path Path to check
     * @return True if the path exists and represents a directory, otherwise false
     */
    public static boolean isPath(@NonNull String path) {
        final File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    /**
     * Extracts all .java-files recursively
     * @param path Path to start directory
     * @return A list of file references
     * @throws RuntimeException if path is not a valid path or a directory
     */
    public static List<File> extractRecursively(@NonNull String path) {
        final List<File> files = new LinkedList<>();
        final File currentDir = new File(path);

        if(!currentDir.exists()) {
            throw new RuntimeException(path + " is not a valid path.");
        }

        if(!currentDir.isDirectory()) {
            throw new RuntimeException(path + " is not a directory.");
        }

        for (File item : Objects.requireNonNull(currentDir.listFiles())) {
            if(item.isDirectory()) {
                files.addAll(extractRecursively(item.getAbsolutePath()));
            } else {
                if(item.getName().endsWith(".java")) {
                    files.addLast(item);
                }
            }
        }

        return files;
    }
}
package be.uliege.info0027.dedup.util;

import be.uliege.info0027.deduplication.VirtualFileInfo;
import be.uliege.info0027.deduplication.VirtualFileSystem;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Recursive traversal helper for the {@link VirtualFileSystem}.
 * <p>
 * The VFS API only exposes a single-level {@code listContent} per directory,
 * so collecting every regular file under a starting path requires an explicit
 * walk. This is centralised here so engines never re-implement traversal.
 */
public final class VfsWalker {

    private VfsWalker() {}

    /**
     * Collects every regular (non-directory) file under {@code rootPath}
     * for the given user, using an iterative depth-first walk.
     *
     * @param vfs      the virtual file system
     * @param userId   the user whose tree is walked
     * @param rootPath the starting directory (e.g. "/" or "/images")
     * @return all regular files found under {@code rootPath}
     */
    public static List<VirtualFileInfo> collectRegularFiles(
            VirtualFileSystem vfs, String userId, String rootPath) {

        List<VirtualFileInfo> result = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(rootPath);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            List<VirtualFileInfo> entries = vfs.listContent(userId, current);
            if (entries == null) continue;

            for (VirtualFileInfo entry : entries) {
                if (Boolean.TRUE.equals(entry.isDirectory())) {
                    stack.push(entry.virtualPath());
                } else {
                    result.add(entry);
                }
            }
        }
        return result;
    }
}

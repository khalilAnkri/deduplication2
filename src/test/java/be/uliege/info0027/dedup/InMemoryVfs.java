package be.uliege.info0027.dedup;

import be.uliege.info0027.deduplication.VirtualFileInfo;
import be.uliege.info0027.deduplication.VirtualFileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixture: an in-memory VFS backed by real on-disk files (so engines
 * can actually hash bytes / decode images). Files are added via
 * {@link #addFile(String, String, byte[])}.
 */
final class InMemoryVfs implements VirtualFileSystem {

    private final Path root;
    /** userId -> virtualDirPath -> direct entries. */
    private final Map<String, Map<String, List<VirtualFileInfo>>> tree = new LinkedHashMap<>();

    InMemoryVfs(Path root) { this.root = root; }

    void addFile(String userId, String virtualPath, byte[] content) throws IOException {
        Path physical = root.resolve(userId + virtualPath.substring(1)).normalize();
        Files.createDirectories(physical.getParent());
        Files.write(physical, content);
        VirtualFileInfo info = new VirtualFileInfo(
                virtualPath, false, content.length, userId, physical.toString());
        registerFile(userId, virtualPath, info);
    }

    private void registerFile(String userId, String virtualPath, VirtualFileInfo info) {
        Map<String, List<VirtualFileInfo>> userTree =
                tree.computeIfAbsent(userId, k -> new LinkedHashMap<>());
        // Ensure user root exists.
        userTree.computeIfAbsent("/", k -> new ArrayList<>());

        // Walk parent directories, creating directory entries as needed.
        String parent = parentOf(virtualPath);
        ensureDirChain(userId, parent);
        userTree.get(parent).add(info);
    }

    private void ensureDirChain(String userId, String dirPath) {
        Map<String, List<VirtualFileInfo>> userTree = tree.get(userId);
        if (userTree.containsKey(dirPath)) return;
        userTree.put(dirPath, new ArrayList<>());
        if (!"/".equals(dirPath)) {
            String parent = parentOf(dirPath);
            ensureDirChain(userId, parent);
            // Add directory entry to its parent.
            VirtualFileInfo dirInfo = new VirtualFileInfo(dirPath, true, 0, userId, "");
            userTree.get(parent).add(dirInfo);
        }
    }

    private static String parentOf(String path) {
        int idx = path.lastIndexOf('/');
        if (idx <= 0) return "/";
        return path.substring(0, idx);
    }

    @Override
    public List<VirtualFileInfo> listContent(String userId, String virtualPath) {
        Map<String, List<VirtualFileInfo>> userTree = tree.get(userId);
        if (userTree == null) return List.of();
        return userTree.getOrDefault(virtualPath, List.of());
    }

    @Override
    public List<VirtualFileInfo> listContent() {
        List<VirtualFileInfo> roots = new ArrayList<>();
        for (String userId : tree.keySet()) {
            roots.add(new VirtualFileInfo("/", true, 0, userId, ""));
        }
        return roots;
    }
}

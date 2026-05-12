package be.uliege.info0027.dedup.engine;

import be.uliege.info0027.deduplication.VirtualFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java engine that groups byte-identical files.
 * <p>
 * Two-stage hashing: files are first bucketed by size (cheap, free from the
 * VFS metadata), and only buckets containing at least two files are hashed
 * with SHA-256. This means a tree where most files have unique sizes does
 * essentially zero IO.
 */
public final class ExactDeduplicationEngine implements DeduplicationEngine {

    private static final int BUFFER_SIZE = 64 * 1024;

    @Override
    public List<List<VirtualFileInfo>> findDuplicateGroups(List<VirtualFileInfo> files) {
        // Stage 1: bucket by size. Order of insertion preserved for determinism.
        Map<Long, List<VirtualFileInfo>> bySize = new LinkedHashMap<>();
        for (VirtualFileInfo f : files) {
            bySize.computeIfAbsent(f.size(), k -> new ArrayList<>()).add(f);
        }

        // Stage 2: hash only collision-prone buckets.
        Map<String, List<VirtualFileInfo>> byHash = new LinkedHashMap<>();
        for (List<VirtualFileInfo> bucket : bySize.values()) {
            if (bucket.size() < 2) continue;
            for (VirtualFileInfo f : bucket) {
                String hash = sha256(f.physicalPath());
                if (hash == null) continue; // unreadable file — skip silently
                byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
            }
        }

        List<List<VirtualFileInfo>> groups = new ArrayList<>();
        for (List<VirtualFileInfo> g : byHash.values()) {
            if (g.size() >= 2) groups.add(g);
        }
        return groups;
    }

    /**
     * Streams SHA-256 over the file content. Returns {@code null} on IO error
     * so a single bad file doesn't fail the whole scan.
     * <p>
     * Public so other internal components ({@code StorageCheckerImpl}) can
     * use the same hash function without re-implementing it.
     */
    public static String sha256(String physicalPath) {
        if (physicalPath == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(Path.of(physicalPath))) {
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }
}

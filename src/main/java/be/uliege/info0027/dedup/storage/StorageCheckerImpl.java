package be.uliege.info0027.dedup.storage;

import be.uliege.info0027.dedup.engine.ExactDeduplicationEngine;
import be.uliege.info0027.dedup.util.VfsWalker;
import be.uliege.info0027.deduplication.StorageChecker;
import be.uliege.info0027.deduplication.VirtualFileInfo;
import be.uliege.info0027.deduplication.VirtualFileSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * On-the-fly duplicate check across the entire storage space.
 * <p>
 * Used by the Storage team during uploads: given the physical {@link Path}
 * of an incoming file, this returns an existing copy anywhere in the VFS
 * (across all users) if the new bytes are already stored.
 * <p>
 * Strategy: filter candidates by size (free), then SHA-256 the survivors.
 * Reuses the same hash function as {@link ExactDeduplicationEngine} so the
 * two stay consistent.
 * <p>
 * The candidate at the same physical path as the input is always skipped:
 * a file is never considered a duplicate of itself, even if the input path
 * happens to point at a file already registered in the VFS.
 */
public final class StorageCheckerImpl implements StorageChecker {

    private final VirtualFileSystem vfs;

    public StorageCheckerImpl(VirtualFileSystem vfs) {
        this.vfs = vfs;
    }

    @Override
    public VirtualFileInfo findDuplicate(Path file) {
        if (file == null) return null;

        long incomingSize;
        try {
            incomingSize = Files.size(file);
        } catch (IOException e) {
            return null;
        }
        String incomingHash = ExactDeduplicationEngine.sha256(file.toString());
        if (incomingHash == null) return null;

        Path incomingNormalized = normalize(file);

        // Walk every user's tree.
        for (VirtualFileInfo userRoot : vfs.listContent()) {
            String userId = userRoot.userId();
            String rootPath = userRoot.virtualPath();
            for (VirtualFileInfo candidate : VfsWalker.collectRegularFiles(vfs, userId, rootPath)) {
                if (candidate.size() != incomingSize) continue;
                if (isSameFile(incomingNormalized, candidate.physicalPath())) continue;
                String candHash = ExactDeduplicationEngine.sha256(candidate.physicalPath());
                if (incomingHash.equals(candHash)) return candidate;
            }
        }
        return null;
    }

    /**
     * Returns true when {@code candidatePhysicalPath} refers to the same
     * physical file as {@code incoming}. Compared via {@link Path}
     * normalisation so trailing separators or "/./" do not cause false
     * negatives. {@link Files#isSameFile} would be more rigorous but
     * requires both paths to exist on disk; comparing normalised paths
     * is sufficient because the VFS stores absolute physical paths.
     */
    private static boolean isSameFile(Path incoming, String candidatePhysicalPath) {
        if (candidatePhysicalPath == null || candidatePhysicalPath.isEmpty()) return false;
        Path cand = normalize(Paths.get(candidatePhysicalPath));
        return cand.equals(incoming);
    }

    private static Path normalize(Path p) {
        try {
            return p.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return p.normalize();
        }
    }
}

package be.uliege.info0027.dedup.core;

import be.uliege.info0027.dedup.engine.DeduplicationEngine;
import be.uliege.info0027.dedup.util.VfsWalker;
import be.uliege.info0027.deduplication.VirtualFileInfo;
import be.uliege.info0027.deduplication.VirtualFileSystem;

import java.util.List;
import java.util.stream.Stream;

/**
 * Core orchestrator. Walks the {@link VirtualFileSystem} for a user/path,
 * then delegates the actual grouping to a {@link DeduplicationEngine}.
 * <p>
 * This separation means engines never know about the VFS and the frontend
 * never knows about hashing — two axes of change handled independently.
 */
public final class DeduplicationService {

    private final VirtualFileSystem vfs;

    public DeduplicationService(VirtualFileSystem vfs) {
        this.vfs = vfs;
    }

    /** Batch scan: collect files, then group them. */
    public List<List<VirtualFileInfo>> scan(
            String userId, String path, DeduplicationEngine engine) {
        List<VirtualFileInfo> files = VfsWalker.collectRegularFiles(vfs, userId, path);
        return engine.findDuplicateGroups(files);
    }

    /** Streaming scan: same collection, but emits groups lazily. */
    public Stream<List<VirtualFileInfo>> scanStream(
            String userId, String path, DeduplicationEngine engine) {
        List<VirtualFileInfo> files = VfsWalker.collectRegularFiles(vfs, userId, path);
        return engine.streamDuplicateGroups(files);
    }

    public VirtualFileSystem vfs() { return vfs; }
}

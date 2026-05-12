package be.uliege.info0027.dedup.engine;

import be.uliege.info0027.deduplication.VirtualFileInfo;

import java.util.List;
import java.util.stream.Stream;

/**
 * Strategy contract for grouping files that should be considered duplicates
 * under some equivalence relation (byte-equal, perceptually-similar, ...).
 * <p>
 * Engines work on a pre-collected list of files so the traversal logic
 * lives in {@link be.uliege.info0027.dedup.util.VfsWalker} and is not
 * duplicated across implementations.
 */
public interface DeduplicationEngine {

    /**
     * Computes all duplicate groups among the given files. Singletons
     * (files with no duplicate) are not returned.
     *
     * @param files the candidate files to compare
     * @return the duplicate groups, each containing 2+ files
     */
    List<List<VirtualFileInfo>> findDuplicateGroups(List<VirtualFileInfo> files);

    /**
     * Streaming variant: emits each duplicate group as soon as it is known
     * to be complete. Default implementation falls back to the batch API;
     * engines may override for genuine incremental behaviour.
     *
     * @param files the candidate files to compare
     * @return a stream of duplicate groups
     */
    default Stream<List<VirtualFileInfo>> streamDuplicateGroups(List<VirtualFileInfo> files) {
        return findDuplicateGroups(files).stream();
    }
}

package be.uliege.info0027.dedup;

import be.uliege.info0027.dedup.core.DeduplicationService;
import be.uliege.info0027.dedup.engine.DeduplicationEngineFactory;
import be.uliege.info0027.dedup.frontend.FrontendGateImpl;
import be.uliege.info0027.dedup.storage.StorageCheckerImpl;
import be.uliege.info0027.deduplication.FileDeduplicationBootstrap;
import be.uliege.info0027.deduplication.FrontendGate;
import be.uliege.info0027.deduplication.StorageChecker;
import be.uliege.info0027.deduplication.VirtualFileSystem;

/**
 * Composition root, discovered by the test harness via {@link java.util.ServiceLoader}.
 * <p>
 * Holds no logic itself — it injects the {@link VirtualFileSystem} into
 * a single {@link DeduplicationService} and exposes the two adapters
 * (frontend gate, storage checker) that share it.
 */
public final class DeduplicationBootstrapImpl implements FileDeduplicationBootstrap {

    private FrontendGate frontendGate;
    private StorageChecker storageChecker;

    /** Required public no-arg constructor for ServiceLoader. */
    public DeduplicationBootstrapImpl() {}

    @Override
    public void initialize(VirtualFileSystem fileSystem) {
        DeduplicationService service = new DeduplicationService(fileSystem);
        DeduplicationEngineFactory factory = new DeduplicationEngineFactory();
        this.frontendGate = new FrontendGateImpl(service, factory);
        this.storageChecker = new StorageCheckerImpl(fileSystem);
    }

    @Override
    public FrontendGate getFrontendGate() {
        if (frontendGate == null) {
            throw new IllegalStateException("Bootstrap not initialized");
        }
        return frontendGate;
    }

    @Override
    public StorageChecker getStorageChecker() {
        if (storageChecker == null) {
            throw new IllegalStateException("Bootstrap not initialized");
        }
        return storageChecker;
    }
}

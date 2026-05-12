package be.uliege.info0027.dedup.engine;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry / factory that resolves a {@link DeduplicationEngine} by its
 * {@code scan_type} string. New engines can be registered without modifying
 * the frontend or the bootstrap, keeping this module open for extension and
 * closed for modification.
 */
public final class DeduplicationEngineFactory {

    private final Map<String, Supplier<DeduplicationEngine>> registry = new ConcurrentHashMap<>();

    public DeduplicationEngineFactory() {
        register("exact", ExactDeduplicationEngine::new);
        register("similar", SimilarDeduplicationEngine::new);
    }

    /** Registers a new engine factory under {@code scanType}. */
    public void register(String scanType, Supplier<DeduplicationEngine> factory) {
        registry.put(scanType, factory);
    }

    /** Resolves the engine for a {@code scanType}, or empty if unknown. */
    public Optional<DeduplicationEngine> create(String scanType) {
        Supplier<DeduplicationEngine> sup = registry.get(scanType);
        return sup == null ? Optional.empty() : Optional.of(sup.get());
    }

    /** Whether a given scan_type is supported. */
    public boolean supports(String scanType) {
        return registry.containsKey(scanType);
    }
}

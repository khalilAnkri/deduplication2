package be.uliege.info0027.dedup.frontend;

import be.uliege.info0027.dedup.core.DeduplicationService;
import be.uliege.info0027.dedup.engine.DeduplicationEngine;
import be.uliege.info0027.dedup.engine.DeduplicationEngineFactory;
import be.uliege.info0027.dedup.util.Json;
import be.uliege.info0027.deduplication.FrontendGate;
import be.uliege.info0027.deduplication.VirtualFileInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JSON-string adapter that fulfills the {@link FrontendGate} contract.
 * <p>
 * Implements the Adapter pattern: it parses a JSON request into a typed
 * call against {@link DeduplicationService} and serialises the result.
 * No business logic lives here — only protocol translation and validation.
 * <p>
 * Uses the project-internal {@link Json} utility so the module has zero
 * runtime dependencies and ships as a thin JAR with no shading required.
 */
public final class FrontendGateImpl implements FrontendGate {

    private static final String ACTION_SCAN = "scan_duplicates";

    private final DeduplicationService service;
    private final DeduplicationEngineFactory engineFactory;

    public FrontendGateImpl(DeduplicationService service, DeduplicationEngineFactory engineFactory) {
        this.service = service;
        this.engineFactory = engineFactory;
    }

    @Override
    public String accept(String jsonRequest) {
        ParsedRequest req;
        try {
            req = parse(jsonRequest);
        } catch (RequestException e) {
            return error(e.getMessage());
        }

        if (!ACTION_SCAN.equals(req.action)) {
            return error("Unsupported action: " + req.action);
        }

        DeduplicationEngine engine = engineFactory.create(req.scanType).orElse(null);
        if (engine == null) {
            return error("Unsupported scan_type: " + req.scanType);
        }

        List<List<VirtualFileInfo>> groups = service.scan(req.user, req.path, engine);
        return successResponse(groups);
    }

    @Override
    public Stream<String> acceptStream(String jsonRequest) {
        ParsedRequest req;
        try {
            req = parse(jsonRequest);
        } catch (RequestException e) {
            return Stream.of(error(e.getMessage()));
        }
        if (!ACTION_SCAN.equals(req.action)) {
            return Stream.of(error("Unsupported action: " + req.action));
        }
        DeduplicationEngine engine = engineFactory.create(req.scanType).orElse(null);
        if (engine == null) {
            return Stream.of(error("Unsupported scan_type: " + req.scanType));
        }
        return service.scanStream(req.user, req.path, engine)
                .map(group -> {
                    List<String> paths = new ArrayList<>(group.size());
                    for (VirtualFileInfo f : group) paths.add(f.virtualPath());
                    return Json.stringListToJsonArray(paths);
                });
    }

    /* ------------------------------------------------------------------ */

    private static String successResponse(List<List<VirtualFileInfo>> groups) {
        List<List<String>> asPaths = new ArrayList<>(groups.size());
        for (List<VirtualFileInfo> g : groups) {
            List<String> paths = new ArrayList<>(g.size());
            for (VirtualFileInfo f : g) paths.add(f.virtualPath());
            asPaths.add(paths);
        }
        return "{\"status\":\"success\",\"groups\":" + Json.groupsToJsonArray(asPaths) + "}";
    }

    private static String error(String message) {
        return new Json.Writer()
                .put("status", "error")
                .put("message", message)
                .toString();
    }

    /** Parsed request fields, validated. Path defaults to root if absent. */
    private record ParsedRequest(String action, String scanType, String path, String user) {}

    private static ParsedRequest parse(String jsonRequest) throws RequestException {
        Map<String, Object> obj;
        try {
            obj = Json.parseObject(jsonRequest);
        } catch (Json.JsonException e) {
            throw new RequestException("Invalid JSON request");
        }
        String action = strOrNull(obj.get("action"));
        if (action == null || action.isBlank()) throw new RequestException("Missing action");
        String scanType = strOrNull(obj.get("scan_type"));
        if (scanType == null || scanType.isBlank()) throw new RequestException("Missing scan_type");
        String user = strOrNull(obj.get("user"));
        if (user == null || user.isBlank()) throw new RequestException("Missing user");
        String path = strOrNull(obj.get("path"));
        if (path == null || path.isBlank()) path = "/";
        return new ParsedRequest(action, scanType, path, user);
    }

    private static String strOrNull(Object v) {
        return v == null ? null : v.toString();
    }

    private static final class RequestException extends Exception {
        RequestException(String msg) { super(msg); }
    }
}

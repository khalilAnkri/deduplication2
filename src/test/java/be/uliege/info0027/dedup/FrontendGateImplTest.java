package be.uliege.info0027.dedup;

import be.uliege.info0027.dedup.util.Json;
import be.uliege.info0027.deduplication.FileDeduplicationBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class FrontendGateImplTest {

    @TempDir Path tempDir;
    private InMemoryVfs vfs;
    private FileDeduplicationBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        vfs = new InMemoryVfs(tempDir);
        bootstrap = ServiceLoader.load(FileDeduplicationBootstrap.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Bootstrap not registered"));
        bootstrap.initialize(vfs);
    }

    @Test
    void exact_groupsByteIdenticalFiles() throws Exception {
        byte[] hello = "hello world".getBytes();
        byte[] other = "different".getBytes();
        vfs.addFile("alice", "/a.txt", hello);
        vfs.addFile("alice", "/dir/b.txt", hello);
        vfs.addFile("alice", "/c.txt", other);

        String response = bootstrap.getFrontendGate().accept("""
                {"action":"scan_duplicates","scan_type":"exact","path":"/","user":"alice"}
                """);

        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("success", obj.get("status"));
        @SuppressWarnings("unchecked")
        List<Object> groups = (List<Object>) obj.get("groups");
        assertEquals(1, groups.size());
        @SuppressWarnings("unchecked")
        List<Object> group = (List<Object>) groups.get(0);
        assertEquals(2, group.size());
    }

    @Test
    void exact_returnsEmptyWhenNoDuplicates() throws Exception {
        vfs.addFile("alice", "/a.txt", "one".getBytes());
        vfs.addFile("alice", "/b.txt", "two".getBytes());

        String response = bootstrap.getFrontendGate().accept("""
                {"action":"scan_duplicates","scan_type":"exact","path":"/","user":"alice"}
                """);

        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("success", obj.get("status"));
        @SuppressWarnings("unchecked")
        List<Object> groups = (List<Object>) obj.get("groups");
        assertEquals(0, groups.size());
    }

    @Test
    void exact_scopedToSubdirectory() throws Exception {
        byte[] data = "x".getBytes();
        vfs.addFile("alice", "/docs/a.txt", data);
        vfs.addFile("alice", "/other/b.txt", data); // same content, outside /docs

        String response = bootstrap.getFrontendGate().accept("""
                {"action":"scan_duplicates","scan_type":"exact","path":"/docs","user":"alice"}
                """);

        Map<String, Object> obj = Json.parseObject(response);
        @SuppressWarnings("unchecked")
        List<Object> groups = (List<Object>) obj.get("groups");
        assertEquals(0, groups.size(), "duplicates outside the requested path must be excluded");
    }

    @Test
    void invalidJson_returnsError() {
        String response = bootstrap.getFrontendGate().accept("not json");
        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("error", obj.get("status"));
        assertEquals("Invalid JSON request", obj.get("message"));
    }

    @Test
    void missingAction_returnsError() {
        String response = bootstrap.getFrontendGate().accept("""
                {"scan_type":"exact","user":"alice"}""");
        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("error", obj.get("status"));
        assertEquals("Missing action", obj.get("message"));
    }

    @Test
    void missingScanType_returnsError() {
        String response = bootstrap.getFrontendGate().accept("""
                {"action":"scan_duplicates","user":"alice"}""");
        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("Missing scan_type", obj.get("message"));
    }

    @Test
    void missingUser_returnsError() {
        String response = bootstrap.getFrontendGate().accept("""
                {"action":"scan_duplicates","scan_type":"exact"}""");
        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("Missing user", obj.get("message"));
    }

    @Test
    void unsupportedAction_returnsError() {
        String response = bootstrap.getFrontendGate().accept("""
                {"action":"FOO","scan_type":"exact","user":"alice"}""");
        Map<String, Object> obj = Json.parseObject(response);
        assertEquals("error", obj.get("status"));
        assertTrue(((String) obj.get("message")).startsWith("Unsupported action"));
    }

    @Test
    void streaming_emitsEachGroupAsJsonArray() throws Exception {
        byte[] a = "aaa".getBytes();
        byte[] b = "bbb".getBytes();
        vfs.addFile("alice", "/a1", a);
        vfs.addFile("alice", "/a2", a);
        vfs.addFile("alice", "/b1", b);
        vfs.addFile("alice", "/b2", b);

        List<String> items = bootstrap.getFrontendGate().acceptStream("""
                {"action":"scan_duplicates","scan_type":"exact","path":"/","user":"alice"}
                """).toList();

        assertEquals(2, items.size());
        for (String item : items) {
            // Each item is a JSON array — wrap in object to use parseObject.
            String wrapped = "{\"a\":" + item + "}";
            @SuppressWarnings("unchecked")
            List<Object> arr = (List<Object>) Json.parseObject(wrapped).get("a");
            assertEquals(2, arr.size());
        }
    }
}

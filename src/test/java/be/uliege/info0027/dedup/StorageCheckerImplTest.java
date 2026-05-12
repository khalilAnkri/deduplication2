package be.uliege.info0027.dedup;

import be.uliege.info0027.dedup.storage.StorageCheckerImpl;
import be.uliege.info0027.deduplication.VirtualFileInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageCheckerImplTest {

    @TempDir Path tempDir;

    @Test
    void findsDuplicateAcrossUsers() throws Exception {
        InMemoryVfs vfs = new InMemoryVfs(tempDir);
        byte[] data = "shared photo bytes".getBytes();
        vfs.addFile("alice", "/photo.jpg", data);
        vfs.addFile("bob", "/album/photo.jpg", data);

        // The "incoming" file: same bytes, different location.
        Path incoming = tempDir.resolve("incoming.jpg");
        Files.write(incoming, data);

        VirtualFileInfo dup = new StorageCheckerImpl(vfs).findDuplicate(incoming);
        assertNotNull(dup, "should find a stored copy");
    }

    @Test
    void returnsNullWhenNoDuplicate() throws Exception {
        InMemoryVfs vfs = new InMemoryVfs(tempDir);
        vfs.addFile("alice", "/photo.jpg", "alice's bytes".getBytes());

        Path incoming = tempDir.resolve("incoming.jpg");
        Files.write(incoming, "completely different".getBytes());

        assertNull(new StorageCheckerImpl(vfs).findDuplicate(incoming));
    }
}

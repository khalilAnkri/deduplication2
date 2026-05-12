package be.uliege.info0027.dedup.engine;

import be.uliege.info0027.deduplication.VirtualFileInfo;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Engine that groups perceptually-similar images using a 64-bit average hash
 * (aHash) and a Hamming-distance threshold.
 * <p>
 * The pipeline mirrors the OpenCV recipe in the brief but uses only the JDK:
 * decode the image, downscale to 8x8, convert to grayscale, then build a
 * 64-bit fingerprint from "pixel brighter than the mean?". Two images are
 * considered similar if their fingerprints differ in at most
 * {@link #DEFAULT_HAMMING_THRESHOLD} bits.
 * <p>
 * Grouping uses single-link clustering via union-find: any two files within
 * threshold land in the same group, even transitively. This matches the
 * intuitive "these all look like the same photo" expectation.
 */
public final class SimilarDeduplicationEngine implements DeduplicationEngine {

    /** Hamming distance below which two hashes are considered similar. */
    public static final int DEFAULT_HAMMING_THRESHOLD = 5;

    private static final int HASH_DIM = 8; // 8x8 = 64 bits

    private final int threshold;

    public SimilarDeduplicationEngine() {
        this(DEFAULT_HAMMING_THRESHOLD);
    }

    public SimilarDeduplicationEngine(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public List<List<VirtualFileInfo>> findDuplicateGroups(List<VirtualFileInfo> files) {
        // Compute hashes; skip files we can't decode as images.
        List<VirtualFileInfo> hashable = new ArrayList<>();
        List<Long> hashes = new ArrayList<>();
        for (VirtualFileInfo f : files) {
            Long h = aHash(f.physicalPath());
            if (h != null) {
                hashable.add(f);
                hashes.add(h);
            }
        }

        // Single-link clustering with union-find.
        int n = hashable.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int dist = Long.bitCount(hashes.get(i) ^ hashes.get(j));
                if (dist <= threshold) union(parent, i, j);
            }
        }

        // Collect non-trivial clusters.
        Map<Integer, List<VirtualFileInfo>> clusters = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            clusters.computeIfAbsent(root, k -> new ArrayList<>()).add(hashable.get(i));
        }
        List<List<VirtualFileInfo>> groups = new ArrayList<>();
        for (List<VirtualFileInfo> c : clusters.values()) {
            if (c.size() >= 2) groups.add(c);
        }
        return groups;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /**
     * Computes a 64-bit average-hash fingerprint of the given image file.
     * Returns {@code null} if the file is not a decodable image.
     */
    static Long aHash(String physicalPath) {
        if (physicalPath == null) return null;
        try {
            BufferedImage src = ImageIO.read(new File(physicalPath));
            if (src == null) return null;

            // Downscale to HASH_DIM x HASH_DIM, force grayscale.
            BufferedImage small = new BufferedImage(HASH_DIM, HASH_DIM, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = small.createGraphics();
            g.drawImage(src, 0, 0, HASH_DIM, HASH_DIM, null);
            g.dispose();

            int[] pixels = new int[HASH_DIM * HASH_DIM];
            long sum = 0;
            for (int y = 0; y < HASH_DIM; y++) {
                for (int x = 0; x < HASH_DIM; x++) {
                    int rgb = small.getRGB(x, y);
                    int gray = rgb & 0xFF; // TYPE_BYTE_GRAY: all channels equal
                    pixels[y * HASH_DIM + x] = gray;
                    sum += gray;
                }
            }
            long mean = sum / pixels.length;

            long hash = 0L;
            for (int i = 0; i < pixels.length; i++) {
                if (pixels[i] >= mean) hash |= (1L << i);
            }
            return hash;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}

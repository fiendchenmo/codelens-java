package com.codelens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SummaryCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void testCacheMiss() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        SummaryCache.CacheEntry entry = cache.lookup("Test.java", "public class Test {}", "deepseek-v4-flash");
        assertNull(entry);
    }

    @Test
    void testCacheSaveAndLookup() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        String source = "public class Test { void process() {} }";
        String result = "{\"summary\": \"test class\"}";
        String model = "deepseek-v4-flash";

        cache.save("Test.java", source, model, result);
        SummaryCache.CacheEntry entry = cache.lookup("Test.java", source, model);

        assertNotNull(entry);
        assertEquals(model, entry.model);
        assertTrue(entry.result.contains("test class"));
    }

    @Test
    void testCacheHitSameContent() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        String source = "public class Test {}";
        cache.save("Test.java", source, "deepseek-v4-flash", "{\"summary\": \"ok\"}");

        SummaryCache.CacheEntry entry = cache.lookup("Test.java", source, "deepseek-v4-flash");
        assertNotNull(entry);
    }

    @Test
    void testCacheMissDifferentContent() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        String source1 = "public class Test {}";
        String source2 = "public class Test { void newMethod() {} }";
        cache.save("Test.java", source1, "deepseek-v4-flash", "{\"summary\": \"v1\"}");

        SummaryCache.CacheEntry entry = cache.lookup("Test.java", source2, "deepseek-v4-flash");
        assertNull(entry);
    }

    @Test
    void testCacheMissDifferentModel() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        String source = "public class Test {}";
        cache.save("Test.java", source, "deepseek-v4-flash", "{\"summary\": \"v1\"}");

        // Different model should miss
        SummaryCache.CacheEntry entry = cache.lookup("Test.java", source, "gpt-4");
        assertNull(entry);
    }

    @Test
    void testCacheDisabled() {
        SummaryCache cache = new SummaryCache(tempDir, false);
        String source = "public class Test {}";
        cache.save("Test.java", source, "deepseek-v4-flash", "{\"summary\": \"ok\"}");

        // Lookup should return null when disabled
        SummaryCache.CacheEntry entry = cache.lookup("Test.java", source, "deepseek-v4-flash");
        assertNull(entry);
    }

    @Test
    void testCacheNullRoot() {
        SummaryCache cache = new SummaryCache(null, true);
        String source = "public class Test {}";
        cache.save("Test.java", source, "deepseek-v4-flash", "{\"summary\": \"ok\"}");
        assertNull(cache.lookup("Test.java", source, "deepseek-v4-flash"));
    }

    @Test
    void testInvalidate() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        String source = "public class Test {}";
        cache.save("Test.java", source, "deepseek-v4-flash", "{\"summary\": \"ok\"}");

        assertTrue(cache.invalidate("Test.java", source));
        assertNull(cache.lookup("Test.java", source, "deepseek-v4-flash"));
    }

    @Test
    void testClearAll() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        cache.save("A.java", "class A {}", "deepseek-v4-flash", "{\"summary\": \"A\"}");
        cache.save("B.java", "class B {}", "deepseek-v4-flash", "{\"summary\": \"B\"}");

        int count = cache.clearAll();
        assertEquals(2, count);
        assertNull(cache.lookup("A.java", "class A {}", "deepseek-v4-flash"));
    }

    @Test
    void testListEntries() {
        SummaryCache cache = new SummaryCache(tempDir, true);
        cache.save("A.java", "class A {}", "deepseek-v4-flash", "{\"summary\": \"A\"}");
        cache.save("B.java", "class B {}", "deepseek-v4-flash", "{\"summary\": \"B\"}");

        List<String> entries = cache.listEntries();
        assertEquals(2, entries.size());
    }

    @Test
    void testMd5() {
        String hash1 = SummaryCache.md5("hello");
        String hash2 = SummaryCache.md5("hello");
        String hash3 = SummaryCache.md5("world");
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertEquals(32, hash1.length()); // MD5 hex = 32 chars
    }
}

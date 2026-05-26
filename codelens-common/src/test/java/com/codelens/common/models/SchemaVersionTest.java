package com.codelens.common.models;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SchemaVersion 枚举单元测试
 * 
 * @since 0.2.9
 */
class SchemaVersionTest {

    @Test
    void testValues() {
        SchemaVersion[] versions = SchemaVersion.values();
        assertEquals(2, versions.length);
        assertEquals(SchemaVersion.V2, versions[0]);
        assertEquals(SchemaVersion.V3, versions[1]);
    }

    @Test
    void testFromString() {
        assertEquals(SchemaVersion.V2, SchemaVersion.fromString("v2"));
        assertEquals(SchemaVersion.V2, SchemaVersion.fromString("2"));
        assertEquals(SchemaVersion.V2, SchemaVersion.fromString("V2"));
        assertEquals(SchemaVersion.V2, SchemaVersion.fromString("V2"));
        
        assertEquals(SchemaVersion.V3, SchemaVersion.fromString("v3"));
        assertEquals(SchemaVersion.V3, SchemaVersion.fromString("3"));
        assertEquals(SchemaVersion.V3, SchemaVersion.fromString("V3"));
        
        assertNull(SchemaVersion.fromString(null));
        assertNull(SchemaVersion.fromString("v4"));
        assertNull(SchemaVersion.fromString(""));
    }

    @Test
    void testIsLatest() {
        assertFalse(SchemaVersion.V2.isLatest());
        assertTrue(SchemaVersion.V3.isLatest());
    }

    @Test
    void testVersionString() {
        assertEquals("v2", SchemaVersion.V2.getVersion());
        assertEquals("v3", SchemaVersion.V3.getVersion());
    }

    @Test
    void testMajorVersion() {
        assertEquals(2, SchemaVersion.V2.getMajorVersion());
        assertEquals(3, SchemaVersion.V3.getMajorVersion());
    }
}

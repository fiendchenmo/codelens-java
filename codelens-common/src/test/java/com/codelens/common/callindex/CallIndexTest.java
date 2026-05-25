package com.codelens.common.callindex;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C-9 CallIndex Phase 2 单元测试。
 *
 * 覆盖范围：
 * A1 CRUD 基本（#1-#8）
 * A2 增量更新（#9-#11）
 * A3 持久化（#12-#13）
 * A4 并发安全（#14-#15）
 * A5 边界条件（#16-#19）
 * A6 CallIndexManager（#20-#22）
 */
public class CallIndexTest {

    @TempDir
    Path tempDir;

    private SQLiteCallIndex index;
    private String dbPath;

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve(".codelens/callindex.db").toString();
        index = new SQLiteCallIndex(dbPath);
    }

    @AfterEach
    void tearDown() {
        if (index != null) {
            index.close();
        }
    }

    // ========== A1 — CRUD 基本 ==========

    @Test
    void testInsertAndQueryByCaller() {
        CallRecord record = new CallRecord("UserService", "process", "OrderService",
            "createOrder", "DIRECT", "UserService.java", 42, "HIGH");
        index.insert(record);

        List<CallRecord> results = index.queryByCaller("UserService", "process");
        assertEquals(1, results.size());
        CallRecord r = results.get(0);
        assertEquals("UserService", r.getCallerClass());
        assertEquals("process", r.getCallerMethod());
        assertEquals("OrderService", r.getCalleeClass());
        assertEquals("createOrder", r.getCalleeMethod());
        assertEquals("DIRECT", r.getCallType());
        assertEquals("UserService.java", r.getFilePath());
        assertEquals(42, r.getLineNumber());
        assertEquals("HIGH", r.getConfidence());
    }

    @Test
    void testQueryByCaller_noMatch() {
        List<CallRecord> results = index.queryByCaller("NonExistent", "method");
        assertTrue(results.isEmpty());
    }

    @Test
    void testInsertAndQueryByCallee() {
        CallRecord record = new CallRecord("UserService", "process", "OrderService",
            "createOrder", "DIRECT", "UserService.java", 42, "HIGH");
        index.insert(record);

        List<CallRecord> results = index.queryByCallee("OrderService", "createOrder");
        assertEquals(1, results.size());
        assertEquals("UserService", results.get(0).getCallerClass());
    }

    @Test
    void testQueryByCallee_multipleRecords() {
        index.insert(new CallRecord("ServiceA", "run", "TargetDAO", "save", "DIRECT", "A.java", 10, "HIGH"));
        index.insert(new CallRecord("ServiceB", "exec", "TargetDAO", "save", "DIRECT", "B.java", 5, "HIGH"));

        List<CallRecord> results = index.queryByCallee("TargetDAO", "save");
        assertEquals(2, results.size());
        assertEquals(5, results.get(0).getLineNumber());
        assertEquals(10, results.get(1).getLineNumber());
    }

    @Test
    void testDeleteByFile() {
        index.insert(new CallRecord("A", "a", "T", "t", "DIRECT", "FileA.java", 1, "HIGH"));
        index.insert(new CallRecord("B", "b", "T", "t", "DIRECT", "FileB.java", 1, "HIGH"));
        index.insert(new CallRecord("C", "c", "T", "t", "DIRECT", "FileA.java", 2, "HIGH"));

        index.deleteByFile("FileA.java");

        assertEquals(0, index.queryByCaller("A", "a").size());
        assertEquals(0, index.queryByCaller("C", "c").size());
        assertEquals(1, index.queryByCaller("B", "b").size());
    }

    @Test
    void testDeleteByFile_nonExistent() {
        index.insert(new CallRecord("A", "a", "T", "t", "DIRECT", "FileA.java", 1, "HIGH"));
        index.deleteByFile("NonExistent.java");
        assertEquals(1, index.queryByCaller("A", "a").size());
    }

    @Test
    void testInsert_multipleCallTypes() {
        index.insert(new CallRecord("UserService", "process", "OrderService", "createOrder", "DIRECT", "A.java", 1, "HIGH"));
        index.insert(new CallRecord("ReportController", "generate", "ReportService", null, "SPRING_INJECTION", "B.java", 2, "MEDIUM"));
        index.insert(new CallRecord("ProxyHandler", "invoke", "TargetClass", "targetMethod", "REFLECTION", "C.java", 3, "LOW"));

        assertEquals(1, index.queryByCaller("UserService", "process").size());
        assertEquals(1, index.queryByCaller("ReportController", "generate").size());
        assertEquals(1, index.queryByCaller("ProxyHandler", "invoke").size());
    }

    @Test
    void testClose_andReopen() {
        index.insert(new CallRecord("A", "a", "T", "t", "DIRECT", "A.java", 1, "HIGH"));
        index.close();
        SQLiteCallIndex reopen = new SQLiteCallIndex(dbPath);
        assertNotNull(reopen);
        reopen.close();
    }

    // ========== A2 — 增量更新 ==========

    @Test
    void testIncrementalUpdate_modifyFile() {
        index.insert(new CallRecord("Old", "old", "T", "t", "DIRECT", "File.java", 1, "HIGH"));
        index.insert(new CallRecord("Old", "old2", "T", "t", "DIRECT", "File.java", 2, "HIGH"));

        index.deleteByFile("File.java");
        index.insert(new CallRecord("New", "new", "T", "t", "DIRECT", "File.java", 1, "HIGH"));

        assertEquals(1, index.getRecordCount());
        assertEquals(1, index.queryByCaller("New", "new").size());
    }

    @Test
    void testIncrementalUpdate_addNewCall() {
        index.insert(new CallRecord("Svc", "run", "T", "t", "DIRECT", "File.java", 1, "HIGH"));

        index.deleteByFile("File.java");
        index.insert(new CallRecord("Svc", "run", "T", "t", "DIRECT", "File.java", 1, "HIGH"));
        index.insert(new CallRecord("Svc", "run", "T2", "t2", "DIRECT", "File.java", 2, "HIGH"));

        List<CallRecord> results = index.queryByCaller("Svc", "run");
        assertEquals(2, results.size());
    }

    @Test
    void testIncrementalUpdate_removeCall() {
        index.insert(new CallRecord("Svc", "run", "T", "t", "DIRECT", "File.java", 1, "HIGH"));
        index.insert(new CallRecord("Svc", "run", "T2", "t2", "DIRECT", "File.java", 2, "HIGH"));

        index.deleteByFile("File.java");
        index.insert(new CallRecord("Svc", "run", "T", "t", "DIRECT", "File.java", 1, "HIGH"));

        List<CallRecord> results = index.queryByCaller("Svc", "run");
        assertEquals(1, results.size());
        assertEquals("T", results.get(0).getCalleeClass());
    }

    // ========== A3 — 持久化 ==========

    @Test
    void testPersistence_closeAndReopen() {
        index.insert(new CallRecord("UserService", "process", "OrderService", "createOrder", "DIRECT", "A.java", 42, "HIGH"));
        index.close();

        SQLiteCallIndex reopen = new SQLiteCallIndex(dbPath);
        List<CallRecord> results = reopen.queryByCaller("UserService", "process");
        assertEquals(1, results.size());
        assertEquals("OrderService", results.get(0).getCalleeClass());
        assertEquals("createOrder", results.get(0).getCalleeMethod());
        reopen.close();
    }

    @Test
    void testPersistence_afterIncrementalUpdate() {
        index.insert(new CallRecord("Old", "old", "T", "t", "DIRECT", "File.java", 1, "HIGH"));
        index.deleteByFile("File.java");
        index.insert(new CallRecord("New", "new", "T", "t", "DIRECT", "File.java", 1, "HIGH"));
        index.close();

        SQLiteCallIndex reopen = new SQLiteCallIndex(dbPath);
        assertEquals(0, reopen.queryByCaller("Old", "old").size());
        assertEquals(1, reopen.queryByCaller("New", "new").size());
        reopen.close();
    }

    // ========== A4 — 并发安全 ==========

    @Test
    void testConcurrentWrite() throws InterruptedException {
        int threadCount = 10;
        int recordsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < recordsPerThread; i++) {
                    index.insert(new CallRecord("Thread" + threadId, "run",
                        "Target", "method", "DIRECT", "T" + threadId + ".java", i, null));
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assertEquals(threadCount * recordsPerThread, index.getRecordCount());
    }

    @Test
    void testConcurrentReadWrite() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            index.insert(new CallRecord("Writer", "run", "Target", "method",
                "DIRECT", "Write.java", i, "HIGH"));
        }

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                index.insert(new CallRecord("Writer2", "exec", "Target", "method",
                    "DIRECT", "Write2.java", i, "HIGH"));
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                index.queryByCaller("Writer", "run");
                index.queryByCallee("Target", "method");
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        assertEquals(200, index.getRecordCount());
    }

    // ========== A5 — 边界条件 ==========

    @Test
    void testQuery_emptyTable() {
        assertTrue(index.queryByCaller("Any", "method").isEmpty());
        assertTrue(index.queryByCallee("Any", "method").isEmpty());
    }

    @Test
    void testInsert_batchLargeVolume() {
        int count = 10000;
        for (int i = 0; i < count; i++) {
            index.insert(new CallRecord("Class" + i, "method", "Target", "t",
                "DIRECT", "File" + i + ".java", i, "LOW"));
        }
        assertEquals(count, index.getRecordCount());
        assertEquals(1, index.queryByCaller("Class1", "method").size());
    }

    @Test
    void testInsert_duplicateRecord() {
        CallRecord record = new CallRecord("A", "a", "T", "t", "DIRECT", "A.java", 1, "HIGH");
        index.insert(record);
        index.insert(record);

        assertEquals(2, index.queryByCaller("A", "a").size());
    }

    @Test
    void testInsert_nullOptionalFields() {
        index.insert(new CallRecord("Util", "run", "Helper", "assist", "DIRECT", "Util.java", 10, null));

        List<CallRecord> results = index.queryByCaller("Util", "run");
        assertEquals(1, results.size());
        assertNull(results.get(0).getConfidence());
    }

    // ========== A7 — 批量插入 ==========

    @Test
    void testBatchInsert() {
        int count = 1000;
        List<CallRecord> records = new ArrayList<CallRecord>();
        for (int i = 0; i < count; i++) {
            records.add(new CallRecord("BatchClass", "method" + i, "Target", "t",
                "DIRECT", "Batch.java", i, "HIGH"));
        }
        index.batchInsert(records);

        assertEquals(count, index.getRecordCount());

        List<CallRecord> results = index.queryByCaller("BatchClass", "method0");
        assertEquals(1, results.size());
        assertEquals("method0", results.get(0).getCallerMethod());
        assertEquals("Target", results.get(0).getCalleeClass());

        results = index.queryByCaller("BatchClass", "method999");
        assertEquals(1, results.size());
        assertEquals("method999", results.get(0).getCallerMethod());
    }

    // ========== A6 — CallIndexManager ==========

    @Test
    void testManager_lifecycle() {
        CallIndexManager manager = new CallIndexManager(tempDir);
        CallIndex idx = manager.getIndex();
        assertNotNull(idx);
        assertFalse(manager.isClosed());
        manager.close();
        assertTrue(manager.isClosed());
    }

    @Test
    void testManager_getIndex_afterClose() {
        CallIndexManager manager = new CallIndexManager(tempDir);
        manager.getIndex();
        manager.close();
        assertThrows(IllegalStateException.class, manager::getIndex);
    }

    @Test
    void testManager_multipleCreate() {
        CallIndexManager mgr1 = new CallIndexManager(tempDir);
        mgr1.getIndex().insert(new CallRecord("A", "a", "T", "t", "DIRECT", "A.java", 1, "HIGH"));
        mgr1.close();

        CallIndexManager mgr2 = new CallIndexManager(tempDir);
        CallIndex idx2 = mgr2.getIndex();
        List<CallRecord> results = idx2.queryByCaller("A", "a");
        assertEquals(1, results.size());
        mgr2.close();
    }
}

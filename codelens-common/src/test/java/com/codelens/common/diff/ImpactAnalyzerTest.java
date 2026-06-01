package com.codelens.common.diff;

import com.codelens.common.callindex.CallRecord;
import com.codelens.common.callindex.SQLiteCallIndex;
import com.codelens.common.models.ArchitectureLayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImpactAnalyzer BFS 扩散引擎单元测试。
 * <p>
 * 覆盖 TC-IA-01 ~ TC-IA-09 测试用例。
 * 使用 SQLite 内存数据库（:memory:）模拟 CallIndex。
 * </p>
 */
public class ImpactAnalyzerTest {

    private SQLiteCallIndex index;

    @AfterEach
    void tearDown() {
        if (index != null) {
            try { index.close(); } catch (Exception ignored) { }
        }
    }

    // ==================== TC-IA-01: 单方法直接影响 ====================

    @Test
    void testSingleMethodDirectImpact() {
        // Setup CallIndex: OrderController.submit() → OrderService.processOrder() (DIRECT)
        index = new SQLiteCallIndex(":memory:");
        index.insert(new CallRecord("OrderController", "submit",
                "OrderService", "processOrder", "DIRECT",
                "src/OrderController.java", 25, null));

        // Setup change: OrderService.processOrder() MODIFIED
        ChangedFile changedFile = createChangedFile("src/OrderService.java", "OrderService",
                ChangeType.MODIFIED,
                new ChangedMethod("OrderService", "processOrder", "processOrder()",
                        ChangeType.MODIFIED, 10, 10));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // Verify
        assertEquals(1, report.impacts.size());
        ImpactNode node = report.impacts.get(0);
        assertEquals("OrderController", node.className);
        assertEquals("submit", node.methodName);
        assertEquals(ImpactLevel.DIRECT, node.level);
        assertEquals(ImpactConfidence.HIGH, node.confidence);
        assertEquals(1, node.hopDistance);
        assertEquals(2, node.impactPath.size());
        assertTrue(node.impactPath.get(1).contains("OrderController.submit"));
    }

    // ==================== TC-IA-02: 多跳间接影响 ====================

    @Test
    void testMultiHopIndirectImpact() {
        index = new SQLiteCallIndex(":memory:");
        // ServiceB.methodB() → ServiceA.methodA()
        index.insert(new CallRecord("ServiceB", "methodB",
                "ServiceA", "methodA", "DIRECT",
                "src/ServiceB.java", 15, null));
        // ControllerC.handle() → ServiceB.methodB()
        index.insert(new CallRecord("ControllerC", "handle",
                "ServiceB", "methodB", "DIRECT",
                "src/ControllerC.java", 30, null));

        // Change: ServiceA.methodA() MODIFIED
        ChangedFile changedFile = createChangedFile("src/ServiceA.java", "ServiceA",
                ChangeType.MODIFIED,
                new ChangedMethod("ServiceA", "methodA", "methodA()",
                        ChangeType.MODIFIED, 5, 5));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertEquals(2, report.impacts.size());

        // ServiceB.methodB: hop=1, DIRECT, HIGH
        ImpactNode serviceB = findNode(report.impacts, "ServiceB", "methodB");
        assertNotNull(serviceB);
        assertEquals(ImpactLevel.DIRECT, serviceB.level);
        assertEquals(1, serviceB.hopDistance);
        assertEquals(ImpactConfidence.HIGH, serviceB.confidence);

        // ControllerC.handle: hop=2, INDIRECT, MEDIUM (downgraded from HIGH)
        ImpactNode controllerC = findNode(report.impacts, "ControllerC", "handle");
        assertNotNull(controllerC);
        assertEquals(ImpactLevel.INDIRECT, controllerC.level);
        assertEquals(2, controllerC.hopDistance);
        assertEquals(ImpactConfidence.MEDIUM, controllerC.confidence);

        // Verify paths
        assertTrue(controllerC.impactPath.size() >= 3);
        assertTrue(controllerC.impactPath.get(controllerC.impactPath.size() - 1).contains("ControllerC.handle"));
    }

    // ==================== TC-IA-03: maxHops 限制 ====================

    @Test
    void testMaxHopsLimit() {
        index = new SQLiteCallIndex(":memory:");
        // 4-hop chain: D → C → B → A
        index.insert(new CallRecord("B", "m1", "A", "methodA", "DIRECT", "src/B.java", 1, null));
        index.insert(new CallRecord("C", "m2", "B", "m1", "DIRECT", "src/C.java", 1, null));
        index.insert(new CallRecord("D", "m3", "C", "m2", "DIRECT", "src/D.java", 1, null));
        index.insert(new CallRecord("E", "m4", "D", "m3", "DIRECT", "src/E.java", 1, null));

        ChangedFile changedFile = createChangedFile("src/A.java", "A",
                ChangeType.MODIFIED,
                new ChangedMethod("A", "methodA", "methodA()",
                        ChangeType.MODIFIED, 1, 1));

        // maxHops=2
        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 2);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // Only hop≤2: B (hop=1), C (hop=2)
        assertEquals(2, report.impacts.size());
        assertNotNull(findNode(report.impacts, "B", "m1"));
        assertNotNull(findNode(report.impacts, "C", "m2"));
        assertNull(findNode(report.impacts, "D", "m3"));
        assertNull(findNode(report.impacts, "E", "m4"));
    }

    // ==================== TC-IA-04: 去重（多路径到达同一方法）====================

    @Test
    void testDedupMultiplePaths() {
        index = new SQLiteCallIndex(":memory:");
        // A.methodA() changed
        // B.m1() → A.methodA() (hop=1)
        // C.m2() → B.m1() → A.methodA() (hop=2)
        // D.m3() → A.methodA() (hop=1)
        index.insert(new CallRecord("B", "m1", "A", "methodA", "DIRECT", "src/B.java", 1, null));
        index.insert(new CallRecord("C", "m2", "B", "m1", "DIRECT", "src/C.java", 1, null));
        index.insert(new CallRecord("D", "m3", "A", "methodA", "DIRECT", "src/D.java", 1, null));

        ChangedFile changedFile = createChangedFile("src/A.java", "A",
                ChangeType.MODIFIED,
                new ChangedMethod("A", "methodA", "methodA()",
                        ChangeType.MODIFIED, 1, 1));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // 3 unique nodes: B (hop=1, DIRECT, HIGH), C (hop=2, INDIRECT, MEDIUM), D (hop=1, DIRECT, HIGH)
        assertEquals(3, report.impacts.size());

        ImpactNode b = findNode(report.impacts, "B", "m1");
        assertNotNull(b);
        assertEquals(ImpactLevel.DIRECT, b.level);
        assertEquals(1, b.hopDistance);

        ImpactNode c = findNode(report.impacts, "C", "m2");
        assertNotNull(c);
        assertEquals(ImpactLevel.INDIRECT, c.level);
        assertEquals(2, c.hopDistance);

        ImpactNode d = findNode(report.impacts, "D", "m3");
        assertNotNull(d);
        assertEquals(ImpactLevel.DIRECT, d.level);
        assertEquals(1, d.hopDistance);
    }

    // ==================== TC-IA-05: Spring 注入置信度 MEDIUM ====================

    @Test
    void testSpringInjectionConfidence() {
        index = new SQLiteCallIndex(":memory:");
        // OrderService.processOrder() → PaymentService.pay() (SPRING_INJECTION)
        index.insert(new CallRecord("OrderService", "processOrder",
                "PaymentService", "pay", "SPRING_INJECTION",
                "src/OrderService.java", 42, null));

        // Change: PaymentService.pay() MODIFIED
        ChangedFile changedFile = createChangedFile("src/PaymentService.java", "PaymentService",
                ChangeType.MODIFIED,
                new ChangedMethod("PaymentService", "pay", "pay()",
                        ChangeType.MODIFIED, 10, 10));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertEquals(1, report.impacts.size());
        ImpactNode node = report.impacts.get(0);
        assertEquals(ImpactConfidence.MEDIUM, node.confidence);
        assertEquals(1, node.hopDistance);
    }

    // ==================== TC-IA-06: 反射调用置信度 LOW ====================

    @Test
    void testReflectionConfidence() {
        index = new SQLiteCallIndex(":memory:");
        // Invoker.invoke() → Target.execute() (REFLECTION)
        index.insert(new CallRecord("Invoker", "invoke",
                "Target", "execute", "REFLECTION",
                "src/Invoker.java", 10, null));

        // Change: Target.execute() MODIFIED
        ChangedFile changedFile = createChangedFile("src/Target.java", "Target",
                ChangeType.MODIFIED,
                new ChangedMethod("Target", "execute", "execute()",
                        ChangeType.MODIFIED, 5, 5));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertEquals(1, report.impacts.size());
        ImpactNode node = report.impacts.get(0);
        assertEquals(ImpactConfidence.LOW, node.confidence);
        assertEquals(1, node.hopDistance);
    }

    // ==================== TC-IA-07: 新增文件不参与反向扩散 ====================

    @Test
    void testAddedFileNoReverseDiffusion() {
        index = new SQLiteCallIndex(":memory:");
        // Suppose there IS a record that would match if we searched
        index.insert(new CallRecord("SomeCaller", "call",
                "NewService", "process", "DIRECT",
                "src/SomeCaller.java", 1, null));

        // Change: NewService.process() ADDED (new file, no callers should exist)
        ChangedFile changedFile = new ChangedFile("src/NewService.java", "NewService", ChangeType.ADDED);
        changedFile.significance = ChangeSignificance.HIGH;
        // ADDED files have no changed methods per DiffParser spec

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // No diffusion from ADDED files
        assertEquals(0, report.impacts.size());
    }

    // ==================== TC-IA-08: 删除文件参与反向扩散 ====================

    @Test
    void testDeletedFileReverseDiffusion() {
        index = new SQLiteCallIndex(":memory:");
        // OrderController.submit() → OldService.process() (DIRECT)
        index.insert(new CallRecord("OrderController", "submit",
                "OldService", "process", "DIRECT",
                "src/OrderController.java", 15, null));

        // Change: OldService.process() DELETED
        ChangedMethod deletedMethod = new ChangedMethod("OldService", "process", "process()",
                ChangeType.DELETED, 10, 0);
        ChangedFile changedFile = createChangedFile("src/OldService.java", "OldService",
                ChangeType.DELETED, deletedMethod);

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // OrderController.submit should be affected
        assertEquals(1, report.impacts.size());
        ImpactNode node = report.impacts.get(0);
        assertEquals("OrderController", node.className);
        assertEquals("submit", node.methodName);
        assertEquals(ImpactConfidence.HIGH, node.confidence);
        assertEquals(1, node.hopDistance);

        // Path should contain "DELETED" annotation
        assertTrue(node.impactPath.get(0).contains("DELETED"),
                "Impact path should contain DELETED annotation");
    }

    // ==================== TC-IA-09: CallIndex 缺失降级 ====================

    @Test
    void testCallIndexMissingDegradation() {
        ChangedFile changedFile = new ChangedFile("src/Service.java", "Service", ChangeType.MODIFIED);
        changedFile.significance = ChangeSignificance.HIGH;
        changedFile.changedMethods.add(
                new ChangedMethod("Service", "process", "process()",
                        ChangeType.MODIFIED, 1, 1));

        // CallIndex = null
        ImpactAnalyzer analyzer = new ImpactAnalyzer(null, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // No impacts (degraded to file-level only)
        assertEquals(0, report.impacts.size());
        // Summary should indicate degradation
        assertNotNull(report.summary);
        assertNotNull(report.summary.note);
        assertTrue(report.summary.note.contains("CallIndex"),
                "Summary note should mention CallIndex: " + report.summary.note);
        // Changes should still be present
        assertEquals(1, report.changes.size());
    }

    // ==================== 摘要验证 ====================

    @Test
    void testSummaryCounts() {
        index = new SQLiteCallIndex(":memory:");
        index.insert(new CallRecord("ControllerA", "handle",
                "ServiceA", "process", "DIRECT",
                "src/ControllerA.java", 1, null));
        index.insert(new CallRecord("ControllerB", "handle",
                "ServiceB", "process", "DIRECT",
                "src/ControllerB.java", 1, null));

        ChangedFile file1 = createChangedFile("src/ServiceA.java", "ServiceA",
                ChangeType.MODIFIED,
                new ChangedMethod("ServiceA", "process", "process()",
                        ChangeType.MODIFIED, 1, 1));
        ChangedFile file2 = createChangedFile("src/ServiceB.java", "ServiceB",
                ChangeType.MODIFIED,
                new ChangedMethod("ServiceB", "process", "process()",
                        ChangeType.MODIFIED, 1, 1));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Arrays.asList(file1, file2), "abc123");

        ImpactSummary s = report.summary;
        assertEquals(2, s.totalChangedFiles);
        assertEquals(2, s.totalChangedMethods);
        assertEquals(2, s.directImpactCount);  // ControllerA + ControllerB
        assertEquals(0, s.indirectImpactCount);
    }

    // ==================== 包级降级验证 ====================

    @Test
    void testFileCountThresholdDegradation() {
        index = new SQLiteCallIndex(":memory:");
        // Create 51+ changed files to trigger package-level degradation
        List<ChangedFile> manyChanges = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) {
            ChangedFile f = new ChangedFile("src/File" + i + ".java", "File" + i, ChangeType.MODIFIED);
            f.significance = ChangeSignificance.HIGH;
            f.changedMethods.add(new ChangedMethod("File" + i, "method",
                    "method()", ChangeType.MODIFIED, 1, 1));
            manyChanges.add(f);
        }

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(manyChanges, "abc123");

        // Degraded to package-level: no impact nodes
        assertEquals(0, report.impacts.size());
        assertNotNull(report.summary.note);
        assertTrue(report.summary.note.contains("降级"),
                "Summary note should mention degradation: " + report.summary.note);
    }

    // ==================== 架构层检测 ====================

    @Test
    void testArchitectureLayerDetection() {
        index = new SQLiteCallIndex(":memory:");
        index.insert(new CallRecord("OrderController", "submit",
                "OrderService", "processOrder", "DIRECT",
                "src/OrderController.java", 1, null));

        ChangedFile changedFile = createChangedFile("src/OrderService.java", "OrderService",
                ChangeType.MODIFIED,
                new ChangedMethod("OrderService", "processOrder", "processOrder()",
                        ChangeType.MODIFIED, 10, 10));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertEquals(1, report.impacts.size());
        // "OrderController" → detectByClassName → CONTROLLER
        assertEquals(ArchitectureLayer.CONTROLLER, report.impacts.get(0).layer);
    }

    // ==================== BFS 广度限制 ====================

    @Test
    void testMaxCallersPerNodeLimit() {
        index = new SQLiteCallIndex(":memory:");
        // 一个方法被 100 个地方调用
        for (int i = 0; i < 100; i++) {
            index.insert(new CallRecord("Caller" + i, "call",
                    "ServiceA", "methodA", "DIRECT",
                    "src/Caller" + i + ".java", 1, null));
        }

        ChangedFile changedFile = createChangedFile("src/ServiceA.java", "ServiceA",
                ChangeType.MODIFIED,
                new ChangedMethod("ServiceA", "methodA", "methodA()",
                        ChangeType.MODIFIED, 1, 1));

        // maxCallersPerNode=10
        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3, 10, 200);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        // 最多返回 10 个 caller
        assertTrue(report.impacts.size() <= 10,
                "Should limit to maxCallersPerNode=10, got " + report.impacts.size());
    }

    @Test
    void testMaxTotalImpactsLimit() {
        index = new SQLiteCallIndex(":memory:");
        // 链式调用: A.methodA → B.methodB → C.methodC ... (每层1个callee, 但每层有多个caller)
        // 让每个方法被10个调用者引用，3跳后应有 10*10*10 = 1000+ 个节点
        String[] classes = {"ServiceA", "ServiceB", "ServiceC", "ServiceD"};
        String[] methods = {"methodA", "methodB", "methodC", "methodD"};
        for (int hop = 0; hop < 3; hop++) {
            for (int i = 0; i < 10; i++) {
                index.insert(new CallRecord(
                        "Caller_" + hop + "_" + i, "call",
                        classes[hop], methods[hop], "DIRECT",
                        "src/Caller.java", 1, null));
            }
        }
        // 链：ServiceD → ServiceC → ServiceB → ServiceA
        index.insert(new CallRecord("ServiceB", "methodB",
                "ServiceA", "methodA", "DIRECT", "src/B.java", 1, null));
        index.insert(new CallRecord("ServiceC", "methodC",
                "ServiceB", "methodB", "DIRECT", "src/C.java", 1, null));
        index.insert(new CallRecord("ServiceD", "methodD",
                "ServiceC", "methodC", "DIRECT", "src/D.java", 1, null));

        ChangedFile changedFile = createChangedFile("src/ServiceA.java", "ServiceA",
                ChangeType.MODIFIED,
                new ChangedMethod("ServiceA", "methodA", "methodA()",
                        ChangeType.MODIFIED, 1, 1));

        // maxTotalImpacts=50
        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 5, 100, 50);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertTrue(report.impacts.size() <= 50,
                "Should limit to maxTotalImpacts=50, got " + report.impacts.size());
    }

    @Test
    void testDefaultConstructorDelegates() {
        // 默认构造器应委托给完整构造器，不抛异常
        index = new SQLiteCallIndex(":memory:");
        index.insert(new CallRecord("OrderController", "submit",
                "OrderService", "processOrder", "DIRECT",
                "src/OrderController.java", 25, null));

        ChangedFile changedFile = createChangedFile("src/OrderService.java", "OrderService",
                ChangeType.MODIFIED,
                new ChangedMethod("OrderService", "processOrder", "processOrder()",
                        ChangeType.MODIFIED, 10, 10));

        // 使用默认构造器（maxHops=3, maxCallersPerNode=30, maxTotalImpacts=200）
        ImpactAnalyzer analyzer = new ImpactAnalyzer(index, 3);
        ImpactReport report = analyzer.analyze(Collections.singletonList(changedFile), "abc123");

        assertEquals(1, report.impacts.size());
        ImpactNode node = report.impacts.get(0);
        assertEquals("OrderController", node.className);
        assertEquals(ImpactLevel.DIRECT, node.level);
        assertEquals(ImpactConfidence.HIGH, node.confidence);
    }

    // ==================== 辅助方法 ====================

    private ChangedFile createChangedFile(String filePath, String className,
                                          ChangeType changeType, ChangedMethod... methods) {
        ChangedFile file = new ChangedFile(filePath, className, changeType);
        file.significance = ChangeSignificance.HIGH;
        if (methods != null) {
            for (ChangedMethod m : methods) {
                file.changedMethods.add(m);
            }
        }
        return file;
    }

    private ImpactNode findNode(List<ImpactNode> nodes, String className, String methodName) {
        for (ImpactNode node : nodes) {
            if (className.equals(node.className) && methodName.equals(node.methodName)) {
                return node;
            }
        }
        return null;
    }
}

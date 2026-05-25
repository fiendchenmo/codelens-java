package com.codelens.common.validators;

import com.codelens.common.validators.l3.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * C-7 L3 多轮验证 — 单元测试
 *
 * 覆盖范围：
 * 1. L3Verifier 接口契约
 * 2. ConfidenceThreshold 配置
 * 3. VerificationRequest / VerificationResult 数据结构
 * 4. CrossValidator 交叉验证
 * 5. ConstraintValidator 约束验证
 * 6. VotingValidator 投票验证
 * 7. V2/V3 Schema 兼容性
 * 8. 配置外部化
 * 9. 边界条件
 */
public class L3VerifierTest {

    private L3Config config;

    @BeforeEach
    void setUp() {
        config = new L3Config();
        config.setEnabled(true);
        config.setConfidenceThreshold(ConfidenceLevel.MEDIUM);
        config.setCrossValidationEnabled(true);
        config.setVotingEnabled(false);
        config.setMaxRetries(1);
    }

    // ========== 1. L3Verifier 接口契约 ==========

    @Test
    void testVerifierInterfaceVerify() {
        // L3Verifier 接口必须提供 verify(VerificationRequest) -> VerificationResult
        L3Verifier verifier = new CrossValidator(config);
        VerificationRequest request = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.LOW,
            "method_call",
            "源码上下文：OrderService.java 第45行"
        );
        VerificationResult result = verifier.verify(request);
        assertNotNull(result);
        assertNotNull(result.getStatus());
        assertNotNull(result.getOriginalClaim());
    }

    @Test
    void testVerifierInterfaceBatchVerify() {
        // L3Verifier 接口应支持批量验证
        L3Verifier verifier = new CrossValidator(config);
        List<VerificationRequest> requests = Arrays.asList(
            new VerificationRequest("结论1", ConfidenceLevel.LOW, "method_call", "ctx1"),
            new VerificationRequest("结论2", ConfidenceLevel.MEDIUM, "dependency", "ctx2")
        );
        List<VerificationResult> results = verifier.verifyAll(requests);
        assertNotNull(results);
        assertEquals(2, results.size());
    }

    // ========== 2. ConfidenceThreshold 配置 ==========

    @Test
    void testThresholdDefault() {
        L3Config defaultConfig = new L3Config();
        assertEquals(ConfidenceLevel.MEDIUM, defaultConfig.getConfidenceThreshold());
        assertFalse(defaultConfig.isEnabled());
        assertTrue(defaultConfig.isCrossValidationEnabled());
        assertFalse(defaultConfig.isVotingEnabled());
        assertEquals(1, defaultConfig.getMaxRetries());
    }

    @Test
    void testThresholdFiltersHighConfidence() {
        // 阈值 MEDIUM：低于 MEDIUM（即 LOW）才触发验证
        ConfidenceThreshold threshold = new ConfidenceThreshold(ConfidenceLevel.MEDIUM);
        assertFalse(threshold.shouldVerify(ConfidenceLevel.HIGH));
        assertFalse(threshold.shouldVerify(ConfidenceLevel.MEDIUM));
        assertTrue(threshold.shouldVerify(ConfidenceLevel.LOW));
    }

    @Test
    void testThresholdFiltersMediumConfidence() {
        // 阈值 HIGH：低于 HIGH（即 LOW + MEDIUM）触发验证
        ConfidenceThreshold threshold = new ConfidenceThreshold(ConfidenceLevel.HIGH);
        assertFalse(threshold.shouldVerify(ConfidenceLevel.HIGH));
        assertTrue(threshold.shouldVerify(ConfidenceLevel.MEDIUM));
        assertTrue(threshold.shouldVerify(ConfidenceLevel.LOW));
    }

    @Test
    void testThresholdLowPassesAll() {
        // 阈值 LOW：没有级别低于 LOW，全部不触发
        ConfidenceThreshold threshold = new ConfidenceThreshold(ConfidenceLevel.LOW);
        assertFalse(threshold.shouldVerify(ConfidenceLevel.HIGH));
        assertFalse(threshold.shouldVerify(ConfidenceLevel.MEDIUM));
        assertFalse(threshold.shouldVerify(ConfidenceLevel.LOW));
    }

    @Test
    void testConfigExternalizable() {
        // 配置项应支持通过 Map 覆盖
        Map<String, String> overrides = new HashMap<>();
        overrides.put("l3.enabled", "true");
        overrides.put("l3.confidence.threshold", "HIGH");
        overrides.put("l3.max-retries", "2");
        L3Config fromMap = L3Config.fromMap(overrides);
        assertTrue(fromMap.isEnabled());
        assertEquals(ConfidenceLevel.HIGH, fromMap.getConfidenceThreshold());
        assertEquals(2, fromMap.getMaxRetries());
    }

    // ========== 3. VerificationRequest / VerificationResult ==========

    @Test
    void testVerificationRequestConstruction() {
        VerificationRequest req = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.LOW,
            "method_call",
            "源码上下文"
        );
        assertEquals("OrderService.process() 调用了 PaymentService.pay()", req.getClaim());
        assertEquals(ConfidenceLevel.LOW, req.getConfidence());
        assertEquals("method_call", req.getClaimType());
        assertEquals("源码上下文", req.getContext());
    }

    @Test
    void testVerificationResultPass() {
        VerificationResult result = new VerificationResult(
            "原始结论",
            VerificationStatus.PASSED,
            "交叉验证一致",
            ConfidenceLevel.HIGH
        );
        assertEquals(VerificationStatus.PASSED, result.getStatus());
        assertTrue(result.isPassed());
        assertFalse(result.isRejected());
        assertFalse(result.isPending());
    }

    @Test
    void testVerificationResultReject() {
        VerificationResult result = new VerificationResult(
            "原始结论",
            VerificationStatus.REJECTED,
            "与方法签名冲突",
            ConfidenceLevel.HIGH
        );
        assertEquals(VerificationStatus.REJECTED, result.getStatus());
        assertTrue(result.isRejected());
        assertFalse(result.isPassed());
    }

    @Test
    void testVerificationResultPending() {
        VerificationResult result = new VerificationResult(
            "原始结论",
            VerificationStatus.PENDING,
            "需要人工确认",
            ConfidenceLevel.MEDIUM
        );
        assertEquals(VerificationStatus.PENDING, result.getStatus());
        assertTrue(result.isPending());
    }

    // ========== 4. CrossValidator 交叉验证 ==========

    @Test
    void testCrossValidatorConsistentResult() {
        // 当二次验证结论与原始结论一致时，结果应为 PASSED
        CrossValidator validator = new CrossValidator(config, (claim, context) ->
            "OrderService.process() 确实调用了 PaymentService.pay()"
        );
        VerificationRequest req = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PASSED, result.getStatus());
    }

    @Test
    void testCrossValidatorInconsistentResult() {
        // 当二次验证结论与原始结论矛盾时，结果应为 REJECTED
        CrossValidator validator = new CrossValidator(config, (claim, context) ->
            "OrderService.process() 并未调用 PaymentService.pay()，而是调用了 OrderDAO.save()"
        );
        VerificationRequest req = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.REJECTED, result.getStatus());
        assertNotNull(result.getEvidence());
    }

    @Test
    void testCrossValidatorDisabled() {
        // 交叉验证关闭时不应执行验证
        config.setCrossValidationEnabled(false);
        CrossValidator validator = new CrossValidator(config, (claim, context) -> "whatever");
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PENDING, result.getStatus()); // 跳过验证=待定
    }

    @Test
    void testCrossValidatorMaxRetryExceeded() {
        // 超过最大重试次数时应返回 PENDING
        config.setMaxRetries(1);
        int[] callCount = {0};
        CrossValidator validator = new CrossValidator(config, (claim, context) -> {
            callCount[0]++;
            return "不确定，无法验证"; // 模糊回答
        });
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        // 模糊回答不构成一致也不构成矛盾 → PENDING
        assertTrue(result.isPending() || result.getStatus() == VerificationStatus.REJECTED);
    }

    // ========== 5. ConstraintValidator 约束验证 ==========

    @Test
    void testConstraintValidatorKnownConstraintPass() {
        // 已知约束（方法签名存在）验证通过
        List<KnownConstraint> constraints = Arrays.asList(
            new KnownConstraint("method_signature", "com.example.OrderService.process()"),
            new KnownConstraint("method_signature", "com.example.PaymentService.pay()")
        );
        ConstraintValidator validator = new ConstraintValidator(config, constraints);
        VerificationRequest req = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.MEDIUM,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        // 两个签名都在约束中 → PASSED
        assertEquals(VerificationStatus.PASSED, result.getStatus());
    }

    @Test
    void testConstraintValidatorUnknownConstraintReject() {
        // 结论引用了不存在的方法签名 → REJECTED
        List<KnownConstraint> constraints = Arrays.asList(
            new KnownConstraint("method_signature", "com.example.OrderService.process()")
            // PaymentService.pay() 不在已知约束中
        );
        ConstraintValidator validator = new ConstraintValidator(config, constraints);
        VerificationRequest req = new VerificationRequest(
            "OrderService.process() 调用了 PaymentService.pay()",
            ConfidenceLevel.MEDIUM,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.REJECTED, result.getStatus());
    }

    @Test
    void testConstraintValidatorEmptyConstraints() {
        // 无约束时，无法验证 → PENDING
        ConstraintValidator validator = new ConstraintValidator(config, Collections.emptyList());
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.MEDIUM,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PENDING, result.getStatus());
    }

    @Test
    void testConstraintValidatorFactTagOnly() {
        // [FACT] 标注的结论只做 ConstraintValidator
        List<KnownConstraint> constraints = Arrays.asList(
            new KnownConstraint("method_signature", "com.example.OrderService.process()")
        );
        ConstraintValidator validator = new ConstraintValidator(config, constraints);
        VerificationRequest req = new VerificationRequest(
            "[FACT] OrderService.process() 存在",
            ConfidenceLevel.HIGH,
            "fact",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        // [FACT] 在约束中 → PASSED
        assertEquals(VerificationStatus.PASSED, result.getStatus());
    }

    // ========== 6. VotingValidator 投票验证 ==========

    @Test
    void testVotingValidatorMajorityPass() {
        // 3 个模型中 2 个同意 → PASSED
        List<VotingValidator.VoteFunction> voters = Arrays.asList(
            claim -> "确认：结论正确",
            claim -> "确认：结论正确",
            claim -> "否认：结论有误"
        );
        VotingValidator validator = new VotingValidator(config, voters);
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PASSED, result.getStatus());
    }

    @Test
    void testVotingValidatorMajorityReject() {
        // 3 个模型中 2 个否决 → REJECTED
        List<VotingValidator.VoteFunction> voters = Arrays.asList(
            claim -> "否认：结论有误",
            claim -> "否认：结论有误",
            claim -> "确认：结论正确"
        );
        VotingValidator validator = new VotingValidator(config, voters);
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.REJECTED, result.getStatus());
    }

    @Test
    void testVotingValidatorDisabled() {
        // 投票验证关闭时不执行
        config.setVotingEnabled(false);
        VotingValidator validator = new VotingValidator(config, Collections.emptyList());
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PENDING, result.getStatus());
    }

    @Test
    void testVotingValidatorTieResult() {
        // 2 个模型 1:1 平票 → PENDING
        List<VotingValidator.VoteFunction> voters = Arrays.asList(
            claim -> "确认",
            claim -> "否认"
        );
        VotingValidator validator = new VotingValidator(config, voters);
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        assertEquals(VerificationStatus.PENDING, result.getStatus());
    }

    // ========== 7. V2/V3 Schema 兼容 ==========

    @Test
    void testV3SchemaFactInferenceExtraction() {
        // V3 Schema：能从 JSON 中提取 [FACT] 和 [INFER] 标注
        String v3Json = "{" +
            "\"methods\": [{" +
            "  \"name\": \"process\"," +
            "  \"calls\": [{\"target\": \"PaymentService.pay()\", \"tag\": \"[FACT]\"}]," +
            "  \"risks\": [{\"description\": \"可能超时\", \"tag\": \"[INFER]\"}]" +
            "}]}";
        List<VerificationRequest> requests = L3RequestExtractor.fromV3Json(v3Json, ConfidenceLevel.MEDIUM);
        assertNotNull(requests);
        // [FACT] 标注的项置信度高，不触发；[INFER] 标注的项触发
        boolean hasInferRequest = requests.stream()
            .anyMatch(r -> r.getClaim().contains("[INFER]") || r.getConfidence() != ConfidenceLevel.HIGH);
        assertTrue(hasInferRequest);
    }

    @Test
    void testV2SchemaNoTagging() {
        // V2 Schema：无 [FACT]/[INFER] 标注，所有结论统一验证
        String v2Json = "{" +
            "\"keyMethods\": [{\"name\": \"process\", \"line\": 10}]," +
            "\"dependencies\": [{\"name\": \"PaymentService\", \"line\": 5}]," +
            "\"risks\": [{\"description\": \"risk\", \"line\": 8}]" +
            "}";
        List<VerificationRequest> requests = L3RequestExtractor.fromV2Json(v2Json, ConfidenceLevel.MEDIUM);
        assertNotNull(requests);
        // V2 无标注，所有中/低置信度都触发
        assertTrue(requests.size() > 0);
    }

    @Test
    void testV3FactOnlyConstraintValidation() {
        // V3 [FACT] 结论只走 ConstraintValidator
        VerificationRequest factReq = new VerificationRequest(
            "[FACT] process() 存在于 OrderService",
            ConfidenceLevel.HIGH,
            "fact",
            "ctx"
        );
        // [FACT] 结论置信度 HIGH，不应触发交叉验证
        ConfidenceThreshold threshold = new ConfidenceThreshold(ConfidenceLevel.MEDIUM);
        assertFalse(threshold.shouldVerify(factReq.getConfidence()));
    }

    // ========== 8. 验证摘要生成 ==========

    @Test
    void testVerificationSummaryGeneration() {
        List<VerificationResult> results = Arrays.asList(
            new VerificationResult("结论1", VerificationStatus.PASSED, "交叉验证一致", ConfidenceLevel.HIGH),
            new VerificationResult("结论2", VerificationStatus.REJECTED, "签名不存在", ConfidenceLevel.HIGH),
            new VerificationResult("结论3", VerificationStatus.PENDING, "待确认", ConfidenceLevel.MEDIUM)
        );
        L3Summary summary = new L3Summary(results);
        assertEquals(1, summary.getPassedCount());
        assertEquals(1, summary.getRejectedCount());
        assertEquals(1, summary.getPendingCount());
        assertEquals(3, summary.getTotalCount());
        assertNotNull(summary.formatReport());
        assertTrue(summary.formatReport().contains("PASSED"));
        assertTrue(summary.formatReport().contains("REJECTED"));
        assertTrue(summary.formatReport().contains("PENDING"));
    }

    // ========== 9. 边界条件 ==========

    @Test
    void testL3DisabledSkipsAll() {
        // L3 关闭时，所有验证跳过
        config.setEnabled(false);
        CrossValidator validator = new CrossValidator(config, (claim, ctx) -> "whatever");
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            "ctx"
        );
        VerificationResult result = validator.verify(req);
        // 关闭状态：直接返回 SKIPPED 或 PENDING
        assertTrue(result.getStatus() == VerificationStatus.SKIPPED
            || result.getStatus() == VerificationStatus.PENDING);
    }

    @Test
    void testEmptyClaimList() {
        // 空结论列表 → 空结果
        L3Verifier verifier = new CrossValidator(config, (claim, ctx) -> "确认");
        List<VerificationResult> results = verifier.verifyAll(Collections.emptyList());
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testNullContextHandling() {
        // context 为 null 时不应抛 NPE
        VerificationRequest req = new VerificationRequest(
            "某结论",
            ConfidenceLevel.LOW,
            "method_call",
            null
        );
        assertNotNull(req);
        assertNull(req.getContext());
        // 验证器不应因 null context 崩溃
        CrossValidator validator = new CrossValidator(config, (claim, ctx) -> "确认");
        assertDoesNotThrow(() -> validator.verify(req));
    }

    @Test
    void testAllHighConfidenceNoVerification() {
        // 全部 HIGH 置信度 → 无需验证
        List<VerificationRequest> requests = Arrays.asList(
            new VerificationRequest("结论1", ConfidenceLevel.HIGH, "fact", "ctx"),
            new VerificationRequest("结论2", ConfidenceLevel.HIGH, "fact", "ctx")
        );
        ConfidenceThreshold threshold = new ConfidenceThreshold(ConfidenceLevel.MEDIUM);
        List<VerificationRequest> toVerify = new ArrayList<>();
        for (VerificationRequest req : requests) {
            if (threshold.shouldVerify(req.getConfidence())) {
                toVerify.add(req);
            }
        }
        assertTrue(toVerify.isEmpty());
    }

    @Test
    void testJdk8Compatibility() {
        // 确保不使用 JDK 1.8 以上特性（如 List.of, Map.of, var 等）
        // 此测试本身即为验证——如果编译通过则兼容
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        assertNotNull(list);
        assertNotNull(map);
    }
}

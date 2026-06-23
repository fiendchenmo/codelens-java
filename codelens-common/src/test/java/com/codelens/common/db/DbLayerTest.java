package com.codelens.common.db;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.MethodReport;
import com.codelens.common.agent.contradiction.ContradictionDetector;
import com.codelens.common.agent.contradiction.ContradictionFinding;
import com.codelens.common.agent.contradiction.ContradictionReport;
import com.codelens.common.agent.model.V3DbOperation;
import com.codelens.common.db.index.DbIndexBuilder;
import com.codelens.common.db.model.*;
import com.codelens.common.db.parser.MyBatisXmlParser;
import com.codelens.common.db.parser.SqlTableExtractor;
import com.codelens.common.db.query.DbAnalysisRepository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-014 数据库层隐式依赖分析 — 单元测试。
 *
 * 覆盖范围：
 *   TC-DB-01 ~ 09: MyBatis XML 解析 (9 个 case from requirement)
 *   TC-DB-10 ~ 14: SQL 表名/字段提取
 *   TC-DB-15 ~ 18: DbIndexBuilder + DbAnalysisRepository
 *   TC-DB-19 ~ 21: C5 矛盾检测规则
 *   TC-DB-22 ~ 24: V3DbOperation 模型
 *   TC-DB-25 ~ 27: 数据模型完整性
 */
public class DbLayerTest {

    @TempDir
    Path tempDir;

    private Connection conn;
    private String dbPath;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve(".codelens/graph.db").toString();
        // 确保父目录存在
        Files.createDirectories(tempDir.resolve(".codelens"));
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ================================================================
    // TC-DB-01 ~ 09: MyBatis XML 解析
    // ================================================================

    // TC-DB-01: 简单 SELECT
    @Test
    void testParseSimpleSelect() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <select id=\"selectUserById\" resultType=\"SysUser\">\n" +
                "    SELECT * FROM sys_user WHERE user_id = #{userId}\n" +
                "  </select>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        SqlOperation op = ops.get(0);
        assertEquals("com.ruoyi.system.mapper.SysUserMapper", op.getMapperInterface());
        assertEquals("selectUserById", op.getMethodName());
        assertEquals(SqlType.SELECT, op.getSqlType());
        assertEquals("MYBATIS_XML", op.getSourceType());
        assertNotNull(op.getSqlText());
        assertTrue(op.getSqlText().contains("SELECT"));
    }

    // TC-DB-02: JOIN 查询
    @Test
    void testParseJoinQuery() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <select id=\"selectUserWithDept\" resultMap=\"UserDeptResult\">\n" +
                "    SELECT u.*, d.dept_name FROM sys_user u\n" +
                "    LEFT JOIN sys_dept d ON u.dept_id = d.dept_id\n" +
                "  </select>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        assertEquals("selectUserWithDept", ops.get(0).getMethodName());
        assertTrue(ops.get(0).getSqlText().contains("LEFT JOIN"));
        assertEquals("UserDeptResult", ops.get(0).getResultMapId());
    }

    // TC-DB-03: INSERT
    @Test
    void testParseInsert() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <insert id=\"insertUser\" parameterType=\"SysUser\">\n" +
                "    INSERT INTO sys_user(user_name, status)\n" +
                "    VALUES(#{userName}, #{status})\n" +
                "  </insert>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        SqlOperation op = ops.get(0);
        assertEquals("insertUser", op.getMethodName());
        assertEquals(SqlType.INSERT, op.getSqlType());
    }

    // TC-DB-04: UPDATE
    @Test
    void testParseUpdate() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <update id=\"updateUserStatus\">\n" +
                "    UPDATE sys_user SET status = #{status}\n" +
                "    WHERE user_id = #{userId}\n" +
                "  </update>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        assertEquals("updateUserStatus", ops.get(0).getMethodName());
        assertEquals(SqlType.UPDATE, ops.get(0).getSqlType());
        assertTrue(ops.get(0).getSqlText().contains("UPDATE"));
    }

    // TC-DB-05: DELETE
    @Test
    void testParseDelete() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <delete id=\"deleteUserById\">\n" +
                "    DELETE FROM sys_user WHERE user_id = #{userId}\n" +
                "  </delete>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        assertEquals("deleteUserById", ops.get(0).getMethodName());
        assertEquals(SqlType.DELETE, ops.get(0).getSqlType());
    }

    // TC-DB-06: 动态 SQL（if / where）
    @Test
    void testParseDynamicSql() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <select id=\"selectUserList\" resultType=\"SysUser\">\n" +
                "    SELECT * FROM sys_user\n" +
                "    <where>\n" +
                "      <if test=\"userName != null\">AND user_name LIKE CONCAT('%', #{userName}, '%')</if>\n" +
                "      <if test=\"status != null\">AND status = #{status}</if>\n" +
                "    </where>\n" +
                "  </select>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        SqlOperation op = ops.get(0);
        assertEquals("selectUserList", op.getMethodName());
        // SQL 文本应包含内容（动态标签被剥离）
        assertNotNull(op.getSqlText());
        assertFalse(op.getSqlText().isEmpty());
    }

    // TC-DB-07: resultMap 解析
    @Test
    void testParseResultMap() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <resultMap id=\"SysUserResult\" type=\"com.ruoyi.system.domain.SysUser\">\n" +
                "    <id property=\"userId\" column=\"user_id\"/>\n" +
                "    <result property=\"userName\" column=\"user_name\"/>\n" +
                "    <result property=\"status\" column=\"status\"/>\n" +
                "  </resultMap>\n" +
                "  <select id=\"selectUserById\" resultMap=\"SysUserResult\">\n" +
                "    SELECT * FROM sys_user WHERE user_id = #{userId}\n" +
                "  </select>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        SqlOperation op = ops.get(0);
        assertEquals("SysUserResult", op.getResultMapId());
        assertNotNull(op.getFieldMapping());
        assertEquals("SysUserResult", op.getFieldMapping().getResultMapId());
        assertEquals("com.ruoyi.system.domain.SysUser", op.getFieldMapping().getJavaType());
        assertEquals(3, op.getFieldMapping().getEntries().size());

        // 验证映射条目
        FieldMapEntry idEntry = op.getFieldMapping().findByProperty("userId");
        assertNotNull(idEntry);
        assertEquals("user_id", idEntry.getColumn());
        assertTrue(idEntry.isId());

        FieldMapEntry nameEntry = op.getFieldMapping().findByProperty("userName");
        assertNotNull(nameEntry);
        assertEquals("user_name", nameEntry.getColumn());
        assertFalse(nameEntry.isId());
    }

    // TC-DB-08: SQL 片段 + include 引用
    @Test
    void testParseSqlFragmentInclude() {
        String xml = "<mapper namespace=\"com.ruoyi.system.mapper.SysUserMapper\">\n" +
                "  <sql id=\"selectUserVo\">\n" +
                "    SELECT user_id, user_name, status FROM sys_user\n" +
                "  </sql>\n" +
                "  <select id=\"selectUserById\">\n" +
                "    <include refid=\"selectUserVo\"/>\n" +
                "    WHERE user_id = #{userId}\n" +
                "  </select>\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/SysUserMapper.xml");
        assertEquals(1, ops.size());
        SqlOperation op = ops.get(0);
        // include 展开后 SQL 文本应包含 SELECT 内容（至少不包含 <include 标签）
        String sqlText = op.getSqlText();
        assertNotNull(sqlText);
        assertFalse(sqlText.contains("<include"), "include tag should be expanded");
    }

    // TC-DB-09: 空 Mapper（无操作标签）
    @Test
    void testParseEmptyMapper() {
        String xml = "<mapper namespace=\"com.example.EmptyMapper\">\n" +
                "  <!-- intentionally empty -->\n" +
                "</mapper>";

        List<SqlOperation> ops = MyBatisXmlParser.parse(xml, "test/EmptyMapper.xml");
        assertNotNull(ops);
        assertTrue(ops.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    // TC-DB-10 ~ 14: SQL 表名/字段提取
    // ════════════════════════════════════════════════════════════════

    // TC-DB-10: SELECT 表名提取
    @Test
    void testExtractTablesFromSelect() {
        // TC-DB-01 的 SQL
        String sql = "SELECT * FROM sys_user WHERE user_id = ?";
        Set<String> tables = SqlTableExtractor.extractTables(sql, SqlType.SELECT);
        assertEquals(1, tables.size());
        assertTrue(tables.contains("sys_user"));
    }

    // TC-DB-10b: JOIN 表名提取
    @Test
    void testExtractTablesFromJoin() {
        String sql = "SELECT u.*, d.dept_name FROM sys_user u LEFT JOIN sys_dept d ON u.dept_id = d.dept_id";
        Set<String> tables = SqlTableExtractor.extractTables(sql, SqlType.SELECT);
        assertEquals(2, tables.size());
        assertTrue(tables.contains("sys_user"));
        assertTrue(tables.contains("sys_dept"));
    }

    // TC-DB-11: INSERT 表名 + 字段提取
    @Test
    void testExtractInsertTableAndFields() {
        String sql = "INSERT INTO sys_user(user_name, status) VALUES(?, ?)";
        Set<String> tables = SqlTableExtractor.extractTables(sql, SqlType.INSERT);
        assertEquals(1, tables.size());
        assertTrue(tables.contains("sys_user"));

        Set<String> fields = SqlTableExtractor.extractFields(sql, SqlType.INSERT);
        assertTrue(fields.contains("user_name"), "fields should contain user_name, got: " + fields);
        assertTrue(fields.contains("status"), "fields should contain status, got: " + fields);
    }

    // TC-DB-12: UPDATE 字段提取
    @Test
    void testExtractUpdateFields() {
        String sql = "UPDATE sys_user SET status = ? WHERE user_id = ?";
        Set<String> fields = SqlTableExtractor.extractFields(sql, SqlType.UPDATE);
        assertTrue(fields.contains("status"), "fields should contain status, got: " + fields);
        assertTrue(fields.contains("user_id"), "fields should contain user_id, got: " + fields);
    }

    // TC-DB-13: DELETE 表名提取
    @Test
    void testExtractDeleteTable() {
        String sql = "DELETE FROM sys_user WHERE user_id = ?";
        Set<String> tables = SqlTableExtractor.extractTables(sql, SqlType.DELETE);
        assertEquals(1, tables.size());
        assertTrue(tables.contains("sys_user"));
    }

    // TC-DB-14: 动态标签剥离
    @Test
    void testStripDynamicTags() {
        String sql = "SELECT * FROM sys_user <where><if test=\"name != null\">AND user_name LIKE ?</if></where>";
        String stripped = SqlTableExtractor.stripDynamicTags(sql);
        assertFalse(stripped.contains("<where>"), "should strip <where>");
        assertFalse(stripped.contains("<if"), "should strip <if>");
        assertTrue(stripped.contains("SELECT"), "should keep SELECT");
        assertTrue(stripped.contains("user_name"), "should keep field content");
    }

    // TC-DB-14b: WILDCARD 检测
    @Test
    void testDetectWildcard() {
        String sql = "SELECT * FROM sys_user";
        Set<String> fields = SqlTableExtractor.extractFields(sql, SqlType.SELECT);
        assertTrue(fields.contains("WILDCARD"), "SELECT * should produce WILDCARD marker");
    }

    // ════════════════════════════════════════════════════════════════
    // TC-DB-15 ~ 18: DbIndexBuilder + DbAnalysisRepository
    // ════════════════════════════════════════════════════════════════

    private List<SqlOperation> createSampleOperations() {
        List<SqlOperation> ops = new ArrayList<SqlOperation>();

        // 操作1: selectUserById
        SqlOperation op1 = new SqlOperation();
        op1.setMapperInterface("com.ruoyi.system.mapper.SysUserMapper");
        op1.setMethodName("selectUserById");
        op1.setSqlType(SqlType.SELECT);
        op1.addTable("sys_user");
        op1.addField("user_id");
        op1.addField("user_name");
        op1.addField("status");
        op1.setSqlText("SELECT user_id, user_name, status FROM sys_user WHERE user_id = ?");
        op1.setXmlLine(45);
        op1.setSourceType(SqlOperation.SOURCE_MYBATIS_XML);
        op1.setSourceFile("mapper/SysUserMapper.xml");

        // 结果映射
        FieldMapping fm1 = new FieldMapping();
        fm1.setResultMapId("SysUserResult");
        fm1.setJavaType("com.ruoyi.system.domain.SysUser");
        fm1.getEntries().add(new FieldMapEntry("userId", "user_id", true));
        fm1.getEntries().add(new FieldMapEntry("userName", "user_name", false));
        fm1.getEntries().add(new FieldMapEntry("status", "status", false));
        op1.setFieldMapping(fm1);
        ops.add(op1);

        // 操作2: insertUser
        SqlOperation op2 = new SqlOperation();
        op2.setMapperInterface("com.ruoyi.system.mapper.SysUserMapper");
        op2.setMethodName("insertUser");
        op2.setSqlType(SqlType.INSERT);
        op2.addTable("sys_user");
        op2.addField("user_name");
        op2.addField("status");
        op2.setSqlText("INSERT INTO sys_user(user_name, status) VALUES(?, ?)");
        op2.setXmlLine(120);
        op2.setSourceType(SqlOperation.SOURCE_MYBATIS_XML);
        op2.setSourceFile("mapper/SysUserMapper.xml");
        ops.add(op2);

        // 操作3: selectDeptById (不同 Mapper，操作 sys_dept 表)
        SqlOperation op3 = new SqlOperation();
        op3.setMapperInterface("com.ruoyi.system.mapper.SysDeptMapper");
        op3.setMethodName("selectDeptById");
        op3.setSqlType(SqlType.SELECT);
        op3.addTable("sys_dept");
        op3.addField("dept_id");
        op3.addField("dept_name");
        op3.setSqlText("SELECT dept_id, dept_name FROM sys_dept WHERE dept_id = ?");
        op3.setXmlLine(30);
        op3.setSourceType(SqlOperation.SOURCE_MYBATIS_XML);
        op3.setSourceFile("mapper/SysDeptMapper.xml");
        ops.add(op3);

        // 操作4: 跨模块共享表 (另一个包的 Mapper)
        SqlOperation op4 = new SqlOperation();
        op4.setMapperInterface("com.ruoyi.order.mapper.OrderMapper");
        op4.setMethodName("selectOrderById");
        op4.setSqlType(SqlType.SELECT);
        op4.addTable("sys_user");
        op4.addField("user_id");
        op4.addField("user_name");
        op4.setSqlText("SELECT user_id, user_name FROM sys_user WHERE user_id = ?");
        op4.setXmlLine(15);
        op4.setSourceType(SqlOperation.SOURCE_MYBATIS_XML);
        op4.setSourceFile("mapper/OrderMapper.xml");
        ops.add(op4);

        return ops;
    }

    // TC-DB-15: DbIndexBuilder 写入 + 查询
    @Test
    void testIndexBuildAndQuery() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-001");

        // 验证 db_operations 表有数据
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM db_operations")) {
            assertTrue(rs.next());
            // 4 个操作，每个至少 1 个表 → ≥4 行
            assertTrue(rs.getInt(1) >= 4, "should have at least 4 rows in db_operations");
        }

        // 验证 db_field_mappings 表有数据
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM db_field_mappings")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt(1), "should have 3 field mapping entries");
        }

        // 验证表名去重
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT table_name FROM db_operations ORDER BY table_name")) {
            assertTrue(rs.next());
            assertEquals("sys_dept", rs.getString("table_name"));
            assertTrue(rs.next());
            assertEquals("sys_user", rs.getString("table_name"));
            // 应该只有 2 个不同的表
            assertFalse(rs.next());
        }
    }

    // TC-DB-16: DbAnalysisRepository — findByTable
    @Test
    void testRepositoryFindByTable() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-002");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<DbOperationRecord> results = repo.findByTable(conn, "sys_user");

        assertTrue(results.size() >= 3,
                "sys_user should be in at least 3 operations");
        // 检查包含 SELECT 和 INSERT
        boolean hasSelect = false, hasInsert = false;
        for (DbOperationRecord r : results) {
            if ("SELECT".equals(r.getSqlType())) hasSelect = true;
            if ("INSERT".equals(r.getSqlType())) hasInsert = true;
        }
        assertTrue(hasSelect, "should have SELECT operations on sys_user");
        assertTrue(hasInsert, "should have INSERT operations on sys_user");
    }

    // TC-DB-17: DbAnalysisRepository — findByTableAndField
    @Test
    void testRepositoryFindByTableAndField() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-003");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<DbOperationRecord> results = repo.findByTableAndField(conn, "sys_user", "status");

        // 应至少找到 selectUserById（fields 含 status）和 insertUser（fields 含 status）
        assertTrue(results.size() >= 1,
                "should find operations containing 'status' field on sys_user");
    }

    // TC-DB-18: DbAnalysisRepository — findTableSharing
    @Test
    void testRepositoryFindTableSharing() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-004");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<TableSharingRecord> results = repo.findTableSharing(conn);

        assertFalse(results.isEmpty());
        // sys_user 被 2 个 Mapper 操作
        TableSharingRecord sysUserSharing = null;
        for (TableSharingRecord r : results) {
            if ("sys_user".equals(r.getTableName())) {
                sysUserSharing = r;
                break;
            }
        }
        assertNotNull(sysUserSharing, "sys_user should appear in table sharing results");
        assertEquals(2, sysUserSharing.getMapperCount(),
                "sys_user is operated by 2 mappers: SysUserMapper + OrderMapper");
    }

    // TC-DB-18b: DbAnalysisRepository — findByMapperMethod
    @Test
    void testRepositoryFindByMapperMethod() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-005");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<DbOperationRecord> results = repo.findByMapperMethod(conn,
                "com.ruoyi.system.mapper.SysUserMapper", "selectUserById");

        assertEquals(1, results.size());
        assertEquals("sys_user", results.get(0).getTableName());
        assertEquals("SELECT", results.get(0).getSqlType());
    }

    // TC-DB-18c: DbAnalysisRepository — findFieldMapping
    @Test
    void testRepositoryFindFieldMapping() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-006");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<FieldMappingRecord> results = repo.findFieldMapping(conn,
                "com.ruoyi.system.mapper.SysUserMapper");

        assertEquals(3, results.size());
        // 按 property 验证
        Set<String> properties = new LinkedHashSet<String>();
        for (FieldMappingRecord r : results) {
            properties.add(r.getPropertyName());
        }
        assertTrue(properties.contains("userId"));
        assertTrue(properties.contains("userName"));
        assertTrue(properties.contains("status"));
    }

    // TC-DB-18d: DbAnalysisRepository — getTableStats
    @Test
    void testRepositoryGetTableStats() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-007");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        Map<String, Map<String, Integer>> stats = repo.getTableStats(conn);

        assertTrue(stats.containsKey("sys_user"));
        Map<String, Integer> userStats = stats.get("sys_user");
        assertTrue(userStats.containsKey("SELECT"));
        assertTrue(userStats.containsKey("INSERT"));
    }

    // TC-DB-18e: findDbOperationsForCalls
    @Test
    void testFindDbOperationsForCalls() throws Exception {
        List<SqlOperation> ops = createSampleOperations();
        DbIndexBuilder.build(conn, ops, "test-hash-008");

        DbAnalysisRepository repo = new DbAnalysisRepository();
        List<String> mapperInterfaces = Arrays.asList(
                "com.ruoyi.system.mapper.SysUserMapper",
                "com.ruoyi.system.mapper.SysDeptMapper");
        List<String> methodNames = Arrays.asList("selectUserById", "selectDeptById");

        List<V3DbOperation> results = repo.findDbOperationsForCalls(
                conn, mapperInterfaces, methodNames);

        assertTrue(results.size() >= 2, "should find at least 2 db operations");
        // 检查 sourceMethod
        Set<String> sourceMethods = new LinkedHashSet<String>();
        for (V3DbOperation v3 : results) {
            sourceMethods.add(v3.sourceMethod);
        }
        assertTrue(sourceMethods.contains(
                "com.ruoyi.system.mapper.SysUserMapper.selectUserById"));
        assertTrue(sourceMethods.contains(
                "com.ruoyi.system.mapper.SysDeptMapper.selectDeptById"));
    }

    // ════════════════════════════════════════════════════════════════
    // TC-DB-19 ~ 21: C5 矛盾检测规则
    // ════════════════════════════════════════════════════════════════

    // TC-DB-19: C5 — 3+ 模块触发矛盾
    @Test
    void testC5DbCouplingDetected() {
        // 构造表共享数据：sys_order 表被 4 个不同包的 Mapper 操作
        List<TableSharingRecord> tableSharing = new ArrayList<TableSharingRecord>();
        TableSharingRecord record = new TableSharingRecord();
        record.setTableName("sys_order");
        record.setMapperInterfaces(Arrays.asList(
                "com.ruoyi.order.mapper.OrderMapper",
                "com.ruoyi.payment.mapper.PaymentMapper",
                "com.ruoyi.report.mapper.ReportMapper",
                "com.ruoyi.notify.mapper.NotifyMapper"
        ));
        record.setMapperCount(4);
        record.setPackages(Arrays.asList(
                "com.ruoyi.order", "com.ruoyi.payment",
                "com.ruoyi.report", "com.ruoyi.notify"
        ));
        tableSharing.add(record);

        ContradictionDetector detector = new ContradictionDetector();
        // 使用空 report（C1-C4 跳过），只测 C5
        AnalysisReport emptyReport = new AnalysisReport();
        emptyReport.setMethods(new ArrayList<MethodReport>());

        ContradictionReport report = detector.detect(emptyReport, null, tableSharing);

        assertFalse(report.getFindings().isEmpty(), "C5 should detect cross-module coupling");
        ContradictionFinding finding = report.getFindings().get(0);
        assertEquals(ContradictionFinding.ContradictionType.DB_COUPLING, finding.getType());
        assertEquals(ContradictionFinding.Severity.LOW, finding.getSeverity());
        assertEquals(-0.1, finding.getConfidencePenalty(), 0.001);
        assertTrue(finding.getDescription().contains("sys_order"));
    }

    // TC-DB-20: C5 — 2 模块不触发（阈值为 ≥3）
    @Test
    void testC5DbCouplingNotTriggered() {
        List<TableSharingRecord> tableSharing = new ArrayList<TableSharingRecord>();
        TableSharingRecord record = new TableSharingRecord();
        record.setTableName("sys_user");
        record.setMapperInterfaces(Arrays.asList(
                "com.ruoyi.system.mapper.SysUserMapper",
                "com.ruoyi.admin.mapper.AdminUserMapper"
        ));
        record.setMapperCount(2);
        record.setPackages(Arrays.asList("com.ruoyi.system", "com.ruoyi.admin"));
        tableSharing.add(record);

        ContradictionDetector detector = new ContradictionDetector();
        AnalysisReport emptyReport = new AnalysisReport();
        emptyReport.setMethods(new ArrayList<MethodReport>());

        ContradictionReport report = detector.detect(emptyReport, null, tableSharing);

        // 2 个模块不触发
        boolean hasC5 = false;
        for (ContradictionFinding f : report.getFindings()) {
            if (f.getType() == ContradictionFinding.ContradictionType.DB_COUPLING) {
                hasC5 = true;
            }
        }
        assertFalse(hasC5, "C5 should not trigger for only 2 modules");
    }

    // TC-DB-21: C5 — null/empty tableSharing 不报错
    @Test
    void testC5DbCouplingNullOrEmpty() {
        ContradictionDetector detector = new ContradictionDetector();
        AnalysisReport emptyReport = new AnalysisReport();
        emptyReport.setMethods(new ArrayList<MethodReport>());

        // null tableSharing
        ContradictionReport report1 = detector.detect(emptyReport, null, null);
        assertNotNull(report1);

        // empty list
        ContradictionReport report2 = detector.detect(emptyReport, null,
                new ArrayList<TableSharingRecord>());
        assertNotNull(report2);

        // 原有 detect() 方法（不含 tableSharing）
        ContradictionReport report3 = detector.detect(emptyReport, null);
        assertNotNull(report3);
    }

    // ════════════════════════════════════════════════════════════════
    // TC-DB-22 ~ 24: V3DbOperation 模型
    // ════════════════════════════════════════════════════════════════

    // TC-DB-22: V3DbOperation 构造
    @Test
    void testV3DbOperationConstruction() {
        V3DbOperation op = new V3DbOperation("sys_user", "SELECT",
                Arrays.asList("user_id", "user_name"), "SysUserMapper.selectUserById");

        assertEquals("sys_user", op.tableName);
        assertEquals("SELECT", op.sqlType);
        assertEquals(2, op.fields.size());
        assertEquals("SysUserMapper.selectUserById", op.sourceMethod);
    }

    // TC-DB-23: V3DbOperation 默认构造
    @Test
    void testV3DbOperationDefaultConstructor() {
        V3DbOperation op = new V3DbOperation();
        assertNull(op.tableName);
        assertNull(op.sqlType);
        assertNotNull(op.fields);
        assertTrue(op.fields.isEmpty());
        assertNull(op.sourceMethod);
    }

    // TC-DB-24: V3DbOperation null fields
    @Test
    void testV3DbOperationNullFields() {
        V3DbOperation op = new V3DbOperation("sys_user", "DELETE", null, "SomeMapper.delete");
        assertNotNull(op.fields);
        assertTrue(op.fields.isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    // TC-DB-25 ~ 27: 数据模型完整性
    // ════════════════════════════════════════════════════════════════

    // TC-DB-25: SqlOperation 字段默认值
    @Test
    void testSqlOperationDefaults() {
        SqlOperation op = new SqlOperation();
        assertNotNull(op.getTables());
        assertTrue(op.getTables().isEmpty());
        assertNotNull(op.getFields());
        assertTrue(op.getFields().isEmpty());
        assertEquals(0, op.getXmlLine());
        assertNull(op.getSqlText());
    }

    // TC-DB-26: TableSharingRecord 跨模块计数
    @Test
    void testTableSharingRecordDistinctModuleCount() {
        TableSharingRecord record = new TableSharingRecord();
        record.setTableName("sys_order");
        record.setPackages(Arrays.asList(
                "com.ruoyi.order.mapper",    // → com.ruoyi.order
                "com.ruoyi.order.dao",       // → com.ruoyi.order (同模块!)
                "com.ruoyi.payment.mapper",  // → com.ruoyi.payment
                "com.ruoyi.report.mapper"    // → com.ruoyi.report
        ));

        // 3 个不同模块：order, payment, report
        assertEquals(3, record.getDistinctModuleCount());
    }

    // TC-DB-27: FieldMapping 查找方法
    @Test
    void testFieldMappingFindMethods() {
        FieldMapping fm = new FieldMapping();
        fm.getEntries().add(new FieldMapEntry("userId", "user_id", true));
        fm.getEntries().add(new FieldMapEntry("userName", "user_name", false));

        // findByProperty
        FieldMapEntry e1 = fm.findByProperty("userId");
        assertNotNull(e1);
        assertEquals("user_id", e1.getColumn());
        assertTrue(e1.isId());

        FieldMapEntry e2 = fm.findByProperty("nonExistent");
        assertNull(e2);

        // findByColumn
        FieldMapEntry e3 = fm.findByColumn("user_name");
        assertNotNull(e3);
        assertEquals("userName", e3.getProperty());

        FieldMapEntry e4 = fm.findByColumn("nonExistent");
        assertNull(e4);
    }
}

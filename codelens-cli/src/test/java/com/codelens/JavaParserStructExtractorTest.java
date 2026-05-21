package com.codelens;

import com.codelens.common.normalizers.StructContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Paths;

public class JavaParserStructExtractorTest {

    @Test
    public void testExtractFields() throws Exception {
        StructContext ctx = JavaParserStructExtractor.extract(
            Paths.get("src/test/resources/SysUserServiceImpl.java").toAbsolutePath());

        assertNotNull(ctx);
        assertEquals("com.ruoyi.system.service.impl", ctx.getPackageName());
        assertEquals("SysUserServiceImpl", ctx.getClassName());

        // 字段提取 — 当前类 + 父类中的 @Autowired/@Resource/@Inject 字段
        assertEquals(2, ctx.getFields().size());
        assertEquals("userMapper", ctx.getFields().get(0).name);
        assertEquals("userRoleMapper", ctx.getFields().get(1).name);
    }

    @Test
    public void testExtractMethods() throws Exception {
        StructContext ctx = JavaParserStructExtractor.extract(
            Paths.get("src/test/resources/SysUserServiceImpl.java").toAbsolutePath());

        assertNotNull(ctx);
        assertTrue(ctx.getMethods().size() >= 5);

        // 检查关键方法
        boolean foundSelectUserList = false;
        boolean foundInsertUser = false;
        boolean foundInsertUserRole = false;
        for (StructContext.MethodInfo m : ctx.getMethods()) {
            if (m.signature.startsWith("selectUserList")) {
                foundSelectUserList = true;
                assertEquals("public", m.visibility);
            }
            if (m.signature.startsWith("insertUser(")) {
                foundInsertUser = true;
                assertEquals("public", m.visibility);
            }
            if (m.signature.startsWith("insertUserRole(")) {
                foundInsertUserRole = true;
                assertEquals("private", m.visibility);
            }
        }
        assertTrue(foundSelectUserList, "selectUserList 方法未提取");
        assertTrue(foundInsertUser, "insertUser 方法未提取");
        assertTrue(foundInsertUserRole, "insertUserRole 方法未提取");
    }

    @Test
    public void testToPromptContext() throws Exception {
        StructContext ctx = JavaParserStructExtractor.extract(
            Paths.get("src/test/resources/SysUserServiceImpl.java").toAbsolutePath());

        String prompt = ctx.toPromptContext();

        // 检查底图标记
        assertTrue(prompt.contains("[代码结构底图"));
        assertTrue(prompt.contains("com.ruoyi.system.service.impl"));
        assertTrue(prompt.contains("SysUserServiceImpl"));

        // 字段输出包含 @Autowired 注入字段
        assertTrue(prompt.contains("userMapper"));
        assertTrue(prompt.contains("userRoleMapper"));

        // 检查方法输出
        assertTrue(prompt.contains("selectUserList"));
        assertTrue(prompt.contains("insertUser"));
        assertTrue(prompt.contains("public"));
    }
}

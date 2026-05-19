package com.codelens;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallIndexTest {
    
    @TempDir
    Path tempDir;
    
    Path projectRoot;
    CallIndex indexer;
    
    @BeforeEach
    void setUp() throws IOException, SQLException {
        projectRoot = tempDir;
        indexer = new CallIndex(projectRoot);
    }
    
    @AfterEach
    void tearDown() {
        if (indexer != null) {
            indexer.close();
        }
    }
    
    @Test
    void testIndexFile_extractsClass() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "package com.example;\n" +
            "\n" +
            "import java.util.List;\n" +
            "\n" +
            "public class UserService {\n" +
            "    public void process() {}\n" +
            "    public void callExternal() {}\n" +
            "}\n";
        Path javaFile = tempDir.resolve("UserService.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> classes = indexer.findByType(CallIndex.TYPE_CLASS);
        boolean foundUserService = classes.stream()
            .anyMatch(r -> r.term.equals("UserService"));
        assertTrue(foundUserService, "Should find UserService class");
    }
    
    @Test
    void testIndexFile_extractsImport() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "package com.example;\n" +
            "import java.util.List;\n" +
            "import com.foo.Bar;\n" +
            "\n" +
            "public class TestClass {}\n";
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> imports = indexer.findByType(CallIndex.TYPE_IMPORT);
        boolean foundList = imports.stream()
            .anyMatch(r -> r.term.equals("List") || r.term.equals("java.util.List"));
        assertTrue(foundList, "Should find imported List");
    }
    
    @Test
    void testIndexFile_extractsMethod() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "public class TestClass {\n" +
            "    public void doSomething() {}\n" +
            "    public String getData() { return null; }\n" +
            "}\n";
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> methods = indexer.findByType(CallIndex.TYPE_METHOD);
        boolean foundDoSomething = methods.stream()
            .anyMatch(r -> r.term.equals("doSomething"));
        assertTrue(foundDoSomething, "Should find doSomething method");
        
        boolean foundGetData = methods.stream()
            .anyMatch(r -> r.term.equals("getData"));
        assertFalse(foundGetData, "getData should be filtered as getter");
    }
    
    @Test
    void testIndexFile_extractsCallee() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "public class TestClass {\n" +
            "    public void process() {\n" +
            "        userService.save();\n" +
            "    }\n" +
            "}\n";
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> callees = indexer.findByType(CallIndex.TYPE_CALLEE);
        boolean foundSave = callees.stream()
            .anyMatch(r -> r.term.equals("save"));
        assertTrue(foundSave, "Should find save callee");
        
        boolean foundUserServiceSave = callees.stream()
            .anyMatch(r -> r.term.equals("userService.save"));
        assertTrue(foundUserServiceSave, "Should find userService.save callee");
    }
    
    @Test
    void testFindByClass() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "package com.example;\n" +
            "public class MyService {\n" +
            "    public void test() {}\n" +
            "}\n";
        Path javaFile = tempDir.resolve("MyService.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> results = indexer.findByClass("MyService");
        assertFalse(results.isEmpty(), "Should find MyService");
    }
    
    @Test
    void testFindByTermPrefix() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent = 
            "public class TestClass {\n" +
            "    public void process() {\n" +
            "        serviceA.doAction();\n" +
            "        serviceB.doAction();\n" +
            "    }\n" +
            "}\n";
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.write(javaFile, javaContent.getBytes());
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> results = indexer.findByTermPrefix("serviceA.", CallIndex.TYPE_CALLEE);
        assertEquals(1, results.size(), "Should find 1 serviceA.doAction callee");
        assertEquals("serviceA.doAction", results.get(0).term);
    }
    
    @Test
    void testMd5IncrementalIndexing() throws IOException, SQLException, NoSuchAlgorithmException {
        String javaContent1 = 
            "public class TestClass {\n" +
            "    public void method1() {}\n" +
            "}\n";
        Path javaFile = tempDir.resolve("TestClass.java");
        Files.write(javaFile, javaContent1.getBytes());
        
        indexer.indexFile(javaFile);
        
        indexer.indexFile(javaFile);
        
        List<CallIndex.IndexResult> methods = indexer.findByType(CallIndex.TYPE_METHOD);
        long methodCount = methods.stream()
            .filter(r -> r.term.equals("method1") && r.filePath.equals(javaFile.toString()))
            .count();
        
        assertEquals(1, methodCount, "Should only have one method1 entry");
    }
}

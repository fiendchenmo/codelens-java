package com.codelens;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallerFinderTest {
    
    @TempDir
    Path tempDir;
    
    Path projectRoot;
    CallIndex indexer;
    CallerFinder callerFinder;
    
    @BeforeEach
    void setUp() throws IOException, SQLException {
        projectRoot = tempDir;
        indexer = new CallIndex(projectRoot);
        callerFinder = new CallerFinder(indexer, projectRoot);
    }
    
    @AfterEach
    void tearDown() {
        if (indexer != null) {
            indexer.close();
        }
    }
    
    @Test
    void testFindCallersViaImport() throws IOException, SQLException, NoSuchAlgorithmException {
        String targetContent = 
            "package com.example;\n" +
            "public class TargetService {\n" +
            "    public void process() {}\n" +
            "}\n";
        Path targetFile = tempDir.resolve("TargetService.java");
        Files.write(targetFile, targetContent.getBytes());
        
        String callerContent = 
            "package com.example;\n" +
            "import com.example.TargetService;\n" +
            "public class CallerClass {\n" +
            "    private TargetService target;\n" +
            "}\n";
        Path callerFile = tempDir.resolve("CallerClass.java");
        Files.write(callerFile, callerContent.getBytes());
        
        indexer.indexFile(targetFile);
        indexer.indexFile(callerFile);
        
        List<CallerFinder.CallerInfo> callers = callerFinder.findCallers("TargetService");
        
        assertFalse(callers.isEmpty(), "Should find callers of TargetService");
        boolean foundCaller = callers.stream()
            .anyMatch(c -> c.filePath.contains("CallerClass.java"));
        assertTrue(foundCaller, "Should find CallerClass as caller");
    }
    
    @Test
    void testFindCallersEmptyResult() throws IOException, SQLException, NoSuchAlgorithmException {
        String content = 
            "public class Unrelated {\n" +
            "    public void test() {}\n" +
            "}\n";
        Path file = tempDir.resolve("Unrelated.java");
        Files.write(file, content.getBytes());
        
        indexer.indexFile(file);
        
        List<CallerFinder.CallerInfo> callers = callerFinder.findCallers("NonExistentClass");
        assertTrue(callers.isEmpty(), "Should not find callers for non-existent class");
    }

    @Test
    void testInterfacePenetrationSingleImpl() throws IOException, SQLException, NoSuchAlgorithmException {
        // Interface: MyService
        String interfaceContent =
            "package com.example;\n" +
            "public interface MyService {\n" +
            "    void execute();\n" +
            "}\n";
        Path interfaceFile = tempDir.resolve("MyService.java");
        Files.write(interfaceFile, interfaceContent.getBytes());

        // Impl: MyServiceImpl implements MyService
        String implContent =
            "package com.example;\n" +
            "public class MyServiceImpl implements MyService {\n" +
            "    @Override\n" +
            "    public void execute() {}\n" +
            "}\n";
        Path implFile = tempDir.resolve("MyServiceImpl.java");
        Files.write(implFile, implContent.getBytes());

        // Caller: imports MyServiceImpl
        String callerContent =
            "package com.example;\n" +
            "import com.example.MyServiceImpl;\n" +
            "public class CallerClass {\n" +
            "    private MyServiceImpl service;\n" +
            "}\n";
        Path callerFile = tempDir.resolve("CallerClass.java");
        Files.write(callerFile, callerContent.getBytes());

        indexer.indexFile(interfaceFile);
        indexer.indexFile(implFile);
        indexer.indexFile(callerFile);

        List<CallerFinder.CallerInfo> callers = callerFinder.findCallersWithInterfacePenetration("MyService");

        assertFalse(callers.isEmpty(), "Should find callers via interface penetration");
        boolean hasImplCaller = callers.stream()
            .anyMatch(c -> c.filePath.contains("CallerClass.java") && c.description.contains("(via interface MyService)"));
        assertTrue(hasImplCaller, "Should find CallerClass via interface penetration with annotation");
    }

    @Test
    void testInterfacePenetrationMultipleImpls() throws IOException, SQLException, NoSuchAlgorithmException {
        // Interface: MyService
        String interfaceContent =
            "package com.example;\n" +
            "public interface MyService {\n" +
            "    void execute();\n" +
            "}\n";
        Path interfaceFile = tempDir.resolve("MyService.java");
        Files.write(interfaceFile, interfaceContent.getBytes());

        // Impl1: MyServiceImpl1 implements MyService
        String impl1Content =
            "package com.example;\n" +
            "public class MyServiceImpl1 implements MyService {\n" +
            "    @Override\n" +
            "    public void execute() {}\n" +
            "}\n";
        Path impl1File = tempDir.resolve("MyServiceImpl1.java");
        Files.write(impl1File, impl1Content.getBytes());

        // Impl2: MyServiceImpl2 implements MyService
        String impl2Content =
            "package com.example;\n" +
            "public class MyServiceImpl2 implements MyService {\n" +
            "    @Override\n" +
            "    public void execute() {}\n" +
            "}\n";
        Path impl2File = tempDir.resolve("MyServiceImpl2.java");
        Files.write(impl2File, impl2Content.getBytes());

        // Caller1: uses MyServiceImpl1
        String caller1Content =
            "package com.example;\n" +
            "import com.example.MyServiceImpl1;\n" +
            "public class CallerClass1 {\n" +
            "    private MyServiceImpl1 service;\n" +
            "}\n";
        Path caller1File = tempDir.resolve("CallerClass1.java");
        Files.write(caller1File, caller1Content.getBytes());

        // Caller2: uses MyServiceImpl2
        String caller2Content =
            "package com.example;\n" +
            "import com.example.MyServiceImpl2;\n" +
            "public class CallerClass2 {\n" +
            "    private MyServiceImpl2 service;\n" +
            "}\n";
        Path caller2File = tempDir.resolve("CallerClass2.java");
        Files.write(caller2File, caller2Content.getBytes());

        indexer.indexFile(interfaceFile);
        indexer.indexFile(impl1File);
        indexer.indexFile(impl2File);
        indexer.indexFile(caller1File);
        indexer.indexFile(caller2File);

        List<CallerFinder.CallerInfo> callers = callerFinder.findCallersWithInterfacePenetration("MyService");

        assertFalse(callers.isEmpty(), "Should find callers via interface penetration");
        assertTrue(callers.stream().anyMatch(c -> c.filePath.contains("CallerClass1.java")),
            "Should find CallerClass1 for impl1");
        assertTrue(callers.stream().anyMatch(c -> c.filePath.contains("CallerClass2.java")),
            "Should find CallerClass2 for impl2");
    }

    @Test
    void testInterfacePenetrationRegularClass() throws IOException, SQLException, NoSuchAlgorithmException {
        // Regular class (not interface)
        String classContent =
            "package com.example;\n" +
            "public class MyService {\n" +
            "    public void execute() {}\n" +
            "}\n";
        Path classFile = tempDir.resolve("MyService.java");
        Files.write(classFile, classContent.getBytes());

        // Caller: imports MyService
        String callerContent =
            "package com.example;\n" +
            "import com.example.MyService;\n" +
            "public class CallerClass {\n" +
            "    private MyService service;\n" +
            "}\n";
        Path callerFile = tempDir.resolve("CallerClass.java");
        Files.write(callerFile, callerContent.getBytes());

        indexer.indexFile(classFile);
        indexer.indexFile(callerFile);

        List<CallerFinder.CallerInfo> callersWithPenetration = callerFinder.findCallersWithInterfacePenetration("MyService");
        List<CallerFinder.CallerInfo> directCallers = callerFinder.findCallers("MyService");

        assertFalse(callersWithPenetration.isEmpty(), "Should find callers");
        assertEquals(directCallers.size(), callersWithPenetration.size(),
            "Should match direct callers count for non-interface class");
        boolean hasViaAnnotation = callersWithPenetration.stream()
            .anyMatch(c -> c.description.contains("(via interface"));
        assertFalse(hasViaAnnotation, "Should NOT have via interface annotation for non-interface class");
    }
}

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
}

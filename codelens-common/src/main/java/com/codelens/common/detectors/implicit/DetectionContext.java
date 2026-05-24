package com.codelens.common.detectors.implicit;

import java.util.Map;

public class DetectionContext {
    private final String fileName;
    private final String sourceCode;
    private final DetectionSource source;
    private final Map<String, Object> graphQueryResults;

    public DetectionContext(String fileName, String sourceCode, DetectionSource source) {
        this(fileName, sourceCode, source, null);
    }

    public DetectionContext(String fileName, String sourceCode, DetectionSource source, Map<String, Object> graphQueryResults) {
        this.fileName = fileName;
        this.sourceCode = sourceCode;
        this.source = source;
        this.graphQueryResults = graphQueryResults;
    }

    public String getFileName() { return fileName; }
    public String getSourceCode() { return sourceCode; }
    public DetectionSource getSource() { return source; }
    public Map<String, Object> getGraphQueryResults() { return graphQueryResults; }
}

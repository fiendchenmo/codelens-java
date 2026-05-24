package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventListenerDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern EVENT_LISTENER_PATTERN =
            Pattern.compile("@EventListener\\s*(?:public\\s+|private\\s+|protected\\s+)?\\w+\\s+\\w+\\(");

    public EventListenerDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.EVENT_LISTENER, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.EVENT_LISTENER, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        if (!isJavaFile(context.getFileName())) {
            return new DetectionResult(DetectionDimension.EVENT_LISTENER, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        Matcher matcher = EVENT_LISTENER_PATTERN.matcher(source);
        while (matcher.find()) {
            deps.add(new ImplicitDependency("EventListener", DetectionDimension.EVENT_LISTENER, ConfidenceLevel.HIGH, "IMPORT_MATCH"));
        }

        return new DetectionResult(DetectionDimension.EVENT_LISTENER, context.getFileName(), deps);
    }

    private boolean isJavaFile(String fileName) {
        return fileName != null && fileName.endsWith(".java");
    }
}

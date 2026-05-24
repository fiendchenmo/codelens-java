package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigReferenceDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern VALUE_PATTERN = Pattern.compile("@Value\\(\"\\$\\{");

    public ConfigReferenceDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.CONFIG_REFERENCE, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.CONFIG_REFERENCE, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        if (!isJavaFile(context.getFileName())) {
            return new DetectionResult(DetectionDimension.CONFIG_REFERENCE, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        Matcher matcher = VALUE_PATTERN.matcher(source);
        while (matcher.find()) {
            deps.add(new ImplicitDependency("ConfigReference", DetectionDimension.CONFIG_REFERENCE, ConfidenceLevel.LOW, "IMPORT_MATCH"));
        }

        return new DetectionResult(DetectionDimension.CONFIG_REFERENCE, context.getFileName(), deps);
    }

    private boolean isJavaFile(String fileName) {
        return fileName != null && fileName.endsWith(".java");
    }
}

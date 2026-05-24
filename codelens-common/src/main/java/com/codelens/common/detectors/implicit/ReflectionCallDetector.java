package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReflectionCallDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern GETBEAN_PATTERN = Pattern.compile("getBean\\(");
    private static final Pattern CLASS_FORNAME_PATTERN = Pattern.compile("Class\\.forName\\(");

    public ReflectionCallDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.REFLECTION_CALL, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.REFLECTION_CALL, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        if (GETBEAN_PATTERN.matcher(source).find()) {
            deps.add(new ImplicitDependency("ReflectionCall", DetectionDimension.REFLECTION_CALL, ConfidenceLevel.MEDIUM, "IMPORT_MATCH"));
        }
        if (CLASS_FORNAME_PATTERN.matcher(source).find()) {
            deps.add(new ImplicitDependency("ClassForName", DetectionDimension.REFLECTION_CALL, ConfidenceLevel.MEDIUM, "IMPORT_MATCH"));
        }

        return new DetectionResult(DetectionDimension.REFLECTION_CALL, context.getFileName(), deps);
    }
}

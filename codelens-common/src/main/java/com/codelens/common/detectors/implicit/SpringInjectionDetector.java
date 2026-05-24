package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpringInjectionDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern AUTOWIRED_PATTERN =
            Pattern.compile("@Autowired\\s*(?:\\w+\\s+)?(?:private\\s+|public\\s+|protected\\s+)?(\\w+)\\s+\\w+");
    private static final Pattern RESOURCE_PATTERN =
            Pattern.compile("@Resource\\s*(?:\\w+\\s+)?(?:private\\s+|public\\s+|protected\\s+)?(\\w+)\\s+\\w+");
    private static final Pattern INJECT_PATTERN =
            Pattern.compile("@Inject\\s*(?:\\w+\\s+)?(?:private\\s+|public\\s+|protected\\s+)?(\\w+)\\s+\\w+");

    public SpringInjectionDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.SPRING_INJECTION, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        if (!isJavaFile(context.getFileName())) {
            return new DetectionResult(DetectionDimension.SPRING_INJECTION, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.SPRING_INJECTION, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        extractInjectionDeps(source, AUTOWIRED_PATTERN, deps);
        extractInjectionDeps(source, RESOURCE_PATTERN, deps);
        extractInjectionDeps(source, INJECT_PATTERN, deps);

        return new DetectionResult(DetectionDimension.SPRING_INJECTION, context.getFileName(), deps);
    }

    private void extractInjectionDeps(String source, Pattern pattern, List<ImplicitDependency> deps) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            String target = matcher.group(1);
            deps.add(new ImplicitDependency(target, DetectionDimension.SPRING_INJECTION, ConfidenceLevel.HIGH, "IMPORT_MATCH"));
        }
    }

    private boolean isJavaFile(String fileName) {
        return fileName != null && fileName.endsWith(".java");
    }
}

package com.codelens.common.detectors.implicit;

import com.google.gson.Gson;
import java.util.List;

public class DetectionResult {
    private final DetectionDimension dimension;
    private final String fileName;
    private final List<ImplicitDependency> implicitDependencies;

    public DetectionResult(DetectionDimension dimension, String fileName, List<ImplicitDependency> implicitDependencies) {
        this.dimension = dimension;
        this.fileName = fileName;
        this.implicitDependencies = implicitDependencies;
    }

    public DetectionDimension getDimension() { return dimension; }
    public String getFileName() { return fileName; }
    public List<ImplicitDependency> getImplicitDependencies() { return implicitDependencies; }

    public boolean hasImplicitDependencies() {
        return implicitDependencies != null && !implicitDependencies.isEmpty();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}

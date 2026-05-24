package com.codelens.common.detectors.implicit;

import java.util.List;

public class DetectionSummary {
    private final int totalDimensionCount;
    private final int dimensionsWithDeps;
    private final int dimensionsWithoutDeps;
    private final int totalImplicitDepCount;

    public DetectionSummary(List<DetectionResult> results) {
        this.totalDimensionCount = results.size();
        int withDeps = 0;
        int totalDeps = 0;
        for (DetectionResult r : results) {
            if (r.hasImplicitDependencies()) {
                withDeps++;
                totalDeps += r.getImplicitDependencies().size();
            }
        }
        this.dimensionsWithDeps = withDeps;
        this.dimensionsWithoutDeps = totalDimensionCount - withDeps;
        this.totalImplicitDepCount = totalDeps;
    }

    public int getTotalDimensionCount() { return totalDimensionCount; }
    public int getDimensionsWithDeps() { return dimensionsWithDeps; }
    public int getDimensionsWithoutDeps() { return dimensionsWithoutDeps; }
    public int getTotalImplicitDepCount() { return totalImplicitDepCount; }

    public String formatReport() {
        return "Detection Summary:\n" +
               "  Total dimensions: " + totalDimensionCount + "\n" +
               "  With dependencies: " + dimensionsWithDeps + "\n" +
               "  Without dependencies: " + dimensionsWithoutDeps + "\n" +
               "  Total implicit dependencies: " + totalImplicitDepCount;
    }
}

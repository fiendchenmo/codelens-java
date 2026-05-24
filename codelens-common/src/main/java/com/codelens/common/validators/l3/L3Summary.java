package com.codelens.common.validators.l3;

import java.util.List;

public class L3Summary {
    private final int passedCount;
    private final int rejectedCount;
    private final int pendingCount;

    public L3Summary(List<VerificationResult> results) {
        int passed = 0, rejected = 0, pending = 0;
        for (VerificationResult r : results) {
            switch (r.getStatus()) {
                case PASSED:
                    passed++;
                    break;
                case REJECTED:
                    rejected++;
                    break;
                case PENDING:
                    pending++;
                    break;
                default:
                    break;
            }
        }
        this.passedCount = passed;
        this.rejectedCount = rejected;
        this.pendingCount = pending;
    }

    public int getPassedCount() { return passedCount; }
    public int getRejectedCount() { return rejectedCount; }
    public int getPendingCount() { return pendingCount; }
    public int getTotalCount() { return passedCount + rejectedCount + pendingCount; }

    public String formatReport() {
        return "L3 Verification Summary:\n" +
               "  PASSED: " + passedCount + "\n" +
               "  REJECTED: " + rejectedCount + "\n" +
               "  PENDING: " + pendingCount;
    }
}

package com.codelens.common.validators.l3;

import java.util.List;

public interface L3Verifier {
    VerificationResult verify(VerificationRequest request);
    List<VerificationResult> verifyAll(List<VerificationRequest> requests);
}

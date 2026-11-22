package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.GoogleRegisterRequest;
import com.library.userservice.auth.dto.GoogleRegistrationResult;
import com.library.userservice.auth.dto.GoogleRequest;
import com.library.userservice.common.api.ApiResponse;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthService {
    private final GoogleIdTokenVerifier verifier;
    private final GoogleRegistrationToken registrationTokens;
    private final GoogleAuthTransactions transactions;

    GoogleAuthService(GoogleIdTokenVerifier verifier, GoogleRegistrationToken registrationTokens,
            GoogleAuthTransactions transactions) {
        this.verifier = verifier;
        this.registrationTokens = registrationTokens;
        this.transactions = transactions;
    }

    public ApiResponse<?> authenticate(GoogleRequest request) {
        registrationTokens.requireConfigured();
        return transactions.authenticate(verifier.verify(request.idToken()));
    }

    public GoogleRegistrationResult register(GoogleRegisterRequest request) {
        verifier.requireConfigured();
        return transactions.register(registrationTokens.verify(request.registrationToken()), request);
    }
}

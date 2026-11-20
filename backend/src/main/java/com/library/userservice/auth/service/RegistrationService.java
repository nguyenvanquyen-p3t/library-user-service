package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.RegisterCodeRequest;
import com.library.userservice.auth.dto.RegisterRequest;
import com.library.userservice.user.dto.UserProfile;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    private final RegistrationTransactions transactions;
    private final OtpMailSender mail;

    public RegistrationService(RegistrationTransactions transactions, OtpMailSender mail) {
        this.transactions = transactions;
        this.mail = mail;
    }

    public OtpRequestResult requestCode(RegisterCodeRequest request) {
        mail.requireConfigured();
        String code = transactions.issue(request);
        mail.sendRegistrationCode(request.email(), code);
        return new OtpRequestResult();
    }

    public UserProfile register(RegisterRequest request) {
        return transactions.register(request);
    }
}

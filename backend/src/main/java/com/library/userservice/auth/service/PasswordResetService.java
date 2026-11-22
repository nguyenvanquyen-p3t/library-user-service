package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.ForgotPasswordCodeRequest;
import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.auth.dto.ResetPasswordRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTransactions transactions;
    private final OtpMailSender mail;

    PasswordResetService(PasswordResetTransactions transactions, OtpMailSender mail) {
        this.transactions = transactions;
        this.mail = mail;
    }

    public OtpRequestResult requestCode(ForgotPasswordCodeRequest request) {
        var code = transactions.issue(request.email());
        if (code.isPresent()) {
            try {
                mail.sendPasswordResetCode(request.email(), code.get());
            } catch (OtpMailSender.DeliveryFailure exception) {
                log.warn("SMTP không xác nhận gửi OTP PASSWORD_RESET sau commit");
            }
        }
        return new OtpRequestResult();
    }

    public void reset(ResetPasswordRequest request) {
        transactions.reset(request);
    }
}

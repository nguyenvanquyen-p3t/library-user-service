package com.library.userservice.auth.service;

import com.library.userservice.auth.dto.OtpRequestResult;
import com.library.userservice.security.UserPrincipal;
import com.library.userservice.user.dto.SetPasswordRequest;
import org.springframework.stereotype.Service;

@Service
public class SetPasswordService {
    private final SetPasswordTransactions transactions;
    private final OtpMailSender mail;

    SetPasswordService(SetPasswordTransactions transactions, OtpMailSender mail) {
        this.transactions = transactions;
        this.mail = mail;
    }

    public OtpRequestResult requestCode(UserPrincipal principal) {
        var issued = transactions.issue(principal);
        mail.sendSetPasswordCode(issued.email(), issued.code());
        return new OtpRequestResult();
    }

    public void setPassword(UserPrincipal principal, SetPasswordRequest request) {
        transactions.setPassword(principal, request);
    }
}

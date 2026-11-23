package com.library.userservice.auth.service;

import com.library.userservice.common.api.ErrorCode;
import com.library.userservice.common.exception.ApplicationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class OtpMailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String host;

    public OtpMailSender(ObjectProvider<JavaMailSender> senders, @Value("${app.otp.mail-from:}") String from,
            @Value("${spring.mail.host:}") String host) {
        this.senders = senders;
        this.from = from;
        this.host = host;
    }

    void requireConfigured() {
        if (from.isBlank() || host.isBlank() || senders.getIfAvailable() == null)
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    void sendRegistrationCode(String email, String code) {
        sendCode(email, code, "Mã xác minh đăng ký");
    }

    void sendPasswordResetCode(String email, String code) {
        sendCode(email, code, "Mã xác minh đặt lại mật khẩu");
    }

    void sendSetPasswordCode(String email, String code) {
        sendCode(email, code, "Mã xác minh thiết lập mật khẩu");
    }

    private void sendCode(String email, String code, String subject) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText("Mã xác minh của bạn là " + code + ". Mã có hiệu lực trong 10 phút.");
        try {
            senders.getObject().send(message);
        } catch (MailException exception) {
            // Không đính kèm lỗi SMTP có thể chứa địa chỉ hoặc nội dung thư.
            throw new DeliveryFailure();
        }
    }

    static final class DeliveryFailure extends ApplicationException {
        DeliveryFailure() {
            super(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}

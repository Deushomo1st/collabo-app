package com.collabo.backend.service;

import com.collabo.backend.config.MailConfig;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailConfig mailConfig;

    public EmailService(JavaMailSender mailSender, MailConfig mailConfig) {
        this.mailSender = mailSender;
        this.mailConfig = mailConfig;
    }

    public void sendWelcomeEmail(String toEmail, String username) {
        SimpleMailMessage message = new SimpleMailMessage();
        // Sender identity lives in MailConfig (app.mail.from) — must be on the verified domain
        message.setFrom(mailConfig.getFromAddress());
        message.setTo(toEmail);
        message.setSubject("Welcome to COLLABO! 🚀");

        // This is your curated message
        String emailBody = String.format(
                "Hi %s,\n\n" +
                        "Welcome to COLLABO! We are thrilled to have you on board.\n\n" +
                        "Your account has been successfully created. You can now start collaborating, pitching ideas, and building with your team.\n\n" +
                        "If you have any questions, just reply to this email.\n\n" +
                        "Best regards,\n" +
                        "The COLLABO Team\n" +
                        "https://collaboapp.pro",
                username
        );

        message.setText(emailBody);
        mailSender.send(message);
    }
}

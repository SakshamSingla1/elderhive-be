package com.elderhive.services;

public interface EmailService {

    void sendEmail(String to, String subject, String htmlContent);
}

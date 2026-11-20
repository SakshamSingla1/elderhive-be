package com.elderhive.sms;

public interface SmsSenderService {

    SmsSendResult send(String toPhoneNumber, String body);
}

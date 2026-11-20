package com.elderhive.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The only class that actually touches the Twilio SDK - TwilioSmsSenderService depends on
 * the TwilioMessageClient interface instead, so it can be unit-tested with a mock.
 */
@Service
@ConditionalOnProperty(name = "app.sms.enabled", havingValue = "true")
public class TwilioMessageClientImpl implements TwilioMessageClient {

    private final String accountSid;
    private final String authToken;

    public TwilioMessageClientImpl(@Value("${app.sms.twilio.account-sid}") String accountSid,
                                    @Value("${app.sms.twilio.auth-token}") String authToken) {
        this.accountSid = accountSid;
        this.authToken = authToken;
    }

    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
    }

    @Override
    public String sendMessage(String to, String from, String body) {
        Message message = Message.creator(new PhoneNumber(to), new PhoneNumber(from), body).create();
        return message.getSid();
    }
}

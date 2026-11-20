package com.elderhive.sms;

/**
 * A thin seam around Twilio's heavily-static SDK (Twilio.init(...) + Message.creator(...)
 * are static calls, not an injectable client object) purely so TwilioSmsSenderService stays
 * unit-testable with a mock rather than needing a real Twilio account/network call in tests.
 */
public interface TwilioMessageClient {

    /**
     * @return the provider message SID on success
     * @throws RuntimeException (Twilio's own ApiException or similar) on any send failure
     */
    String sendMessage(String to, String from, String body);
}

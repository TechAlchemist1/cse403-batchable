package com.batchable.backend.twilio;

public interface TwilioManager {

    void sendMessage(String toPhoneNumber, String message);

}

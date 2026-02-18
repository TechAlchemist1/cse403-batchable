package com.batchable.backend.twilio;

/**
 * Request body for the send-text API: recipient phone number and message text.
 */
public class SmsRequest {

    /** Recipient phone number (E.164 format recommended, e.g. +1234567890). */
    private String phoneNumber;

    /** Body of the SMS to send. */ 
    private String message;

    // getters + setters
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}

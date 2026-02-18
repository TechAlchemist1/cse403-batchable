package com.batchable.backend.twilio;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for sending SMS via Twilio.
 * Exposes POST /api/send-text to send a text message to a phone number.
 */
@RestController
@RequestMapping("/api")
public class SmsController {

    private final SmsService smsService;

    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    /**
     * Sends an SMS to the given phone number with the given message body.
     *
     * @param request JSON body with phoneNumber and message
     * @return 200 OK with "Message sent!" on success
     */
    @PostMapping("/send-text")
    public ResponseEntity<String> sendText(@RequestBody SmsRequest request) {
        smsService.sendSms(request.getPhoneNumber(), request.getMessage());
        return ResponseEntity.ok("Message sent!");
    }
}

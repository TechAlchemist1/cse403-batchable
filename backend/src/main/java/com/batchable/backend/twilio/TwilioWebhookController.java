package com.batchable.backend.twilio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    // Create a logger for this class
    private static final Logger logger = LoggerFactory.getLogger(TwilioWebhookController.class);

    @PostMapping("/incoming")
    public String receiveSms(
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        logger.info("Incoming SMS from: {}", from);
        logger.info("Message body: {}", body);

        // Later this will call BatchingManager
        // For now just log

        return "Received";
    }
}
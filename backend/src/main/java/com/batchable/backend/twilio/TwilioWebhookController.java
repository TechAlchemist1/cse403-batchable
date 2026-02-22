// We will use this controller to receive incoming SMS messages from Twilio.
package com.batchable.backend.twilio;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    @PostMapping("/incoming")
    public String receiveSms(
            @RequestParam("From") String from,
            @RequestParam("Body") String body) {

        System.out.println("Incoming SMS from: " + from);
        System.out.println("Message body: " + body);
        // Later this will call BatchingManager
        // For now just log

        return "Received";
    }
}

// package com.batchable.backend.twilio;

// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;

// import org.springframework.security.config.Customizer;
// import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// import org.springframework.security.web.SecurityFilterChain;

// /**
//  * Security configuration for Twilio-related endpoints.
//  * Allows unauthenticated access to SMS send/receive and webhook URLs so Twilio can call them.
//  */
// @Configuration
// public class SecurityConfig {

//     /**
//      * Configures the security filter chain: disables CSRF for Twilio webhooks,
//      * permits public access to send-text, receive-text, and incoming webhook paths,
//      * and requires JWT for all other requests.
//      */
//     @Bean
//     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

//         // Disable CSRF so Twilio webhooks can POST without a CSRF token
//         http
//             .csrf(csrf -> csrf.disable())
//             // Allow unauthenticated access only to Twilio SMS and webhook endpoints
//             .authorizeHttpRequests(auth -> auth
//                 .requestMatchers("/api/send-text", "/api/receive-text", "/twilio/incoming").permitAll()
//                 .anyRequest().authenticated()
//             )
//             .oauth2ResourceServer(oauth2 ->
//                 oauth2.jwt(Customizer.withDefaults())
//             );

//         return http.build();
//     }
// }

package com.batchable.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


/**
 * WebSocketConfig sets up the WebSocket infrastructure for real-time updates.
 *
 * Responsibilities:
 *  - Register the WebSocket endpoint that clients connect to
 *  - Enable a simple in-memory message broker for broadcasting
 *  - Define application destination prefixes for sending messages from backend
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure message broker for pushing updates to clients.
     *
     * - enableSimpleBroker("/topic") means clients can subscribe to destinations starting with "/topic".
     * - setApplicationDestinationPrefixes("/app") means messages sent from clients to backend should start with "/app".
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");   // simple in-memory broker
        config.setApplicationDestinationPrefixes("/app"); // prefix for messages from client -> server
    }

    /**
     * Register the WebSocket endpoint that clients connect to.
     *
     * - "/ws" is the URL clients will connect to.
     * - withSockJS() provides fallback support if native WebSocket is unavailable.
     * - setAllowedOriginPatterns("*") allows cross-origin connections (adjust in production!).
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // allow CORS for dev purposes
                .withSockJS();                  // fallback for browsers without WebSocket
    }
}

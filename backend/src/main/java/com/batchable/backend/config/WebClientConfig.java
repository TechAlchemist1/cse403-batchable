package com.batchable.backend.config;

// Marks this class as a source of Spring configuration.
// Spring will scan this class at startup and look for @Bean methods.
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// WebClient is Spring’s non-blocking HTTP client (used to call external APIs)
import org.springframework.web.reactive.function.client.WebClient;

// Allows injecting values from application.yml / application.properties
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class WebClientConfig {

  /**
   * Provide a WebClient.Builder bean explicitly. This ensures Spring can inject it into other
   * beans.
   */
  @Bean
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }

  /**
   * This method defines a Spring Bean.
   *
   * - Spring will call this method once at startup - The returned WebClient object is stored in the
   * Spring container - Other classes can inject this WebClient
   * using @Qualifier("googleRoutesWebClient")
   */
  @Bean
  public WebClient googleRoutesWebClient(

      // Spring automatically provides a WebClient.Builder
      // This builder is preconfigured with Spring defaults
      WebClient.Builder builder,

      // Reads the value of:
      // google.routes.base-url
      // from application.yml (or application.properties)
      @Value("${google.routes.base-url}") String baseUrl) {
    return builder
        // Sets a default base URL so future requests
        // only need to specify paths (e.g. "/directions/v2:computeRoutes")
        .baseUrl(baseUrl)

        // Adds a default HTTP header to every request
        .defaultHeader("Content-Type", "application/json")

        // Builds the final immutable WebClient instance
        .build();
  }
}

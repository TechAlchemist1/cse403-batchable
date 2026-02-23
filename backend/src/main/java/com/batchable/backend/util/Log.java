package com.batchable.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for logging objects as pretty-printed JSON.
 */
public class Log {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Logger logger = LoggerFactory.getLogger(Log.class);

  // Register JavaTimeModule in a static initializer block
  static {
    mapper.registerModule(new JavaTimeModule());
    // Optional: use ISO-8601 strings instead of timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public static void printAsJson(Object toPrint) {
    try {
      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toPrint);
      logger.info("\n{}", json);
    } catch (Exception e) {
      logger.error("Failed to convert object to JSON", e);
    }
  }
}
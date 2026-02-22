package com.batchable.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for logging objects as pretty-printed JSON.
 * 
 * This class uses SLF4J Logger instead of System.out.println, allowing logs to respect logging
 * levels and be directed to files or monitoring systems. Useful for debugging complex objects in a
 * readable JSON format.
 */
public class Log {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final Logger logger = LoggerFactory.getLogger(Log.class);

  public static void printAsJson(Object toPrint) {
    try {
      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toPrint);
      // Use logger instead of System.out
      logger.info("\n{}", json);
    } catch (Exception e) {
      logger.error("Failed to convert object to JSON", e);
    }
  }
}

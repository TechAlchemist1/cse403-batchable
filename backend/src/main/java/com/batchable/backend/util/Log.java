package com.batchable.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;


public class Log {
  private static ObjectMapper mapper = new ObjectMapper();

  public static void printAsJson(Object toPrint) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(toPrint);
      System.out.println(json);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

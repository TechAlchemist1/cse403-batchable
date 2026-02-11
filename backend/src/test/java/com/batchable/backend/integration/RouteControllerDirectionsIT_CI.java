package com.batchable.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

@SpringBootTest
@AutoConfigureMockMvc
class RouteControllerDirectionsIT_CI {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void testGetDirections() throws Exception {
    mockMvc
        .perform(get("/routes/directions").param("from", "Olympia,WA").with(jwt()).param("to",
            "Bellingham,WA"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.distanceMeters").isNumber())
        .andExpect(jsonPath("$.durationSeconds").isNumber());
  }
}

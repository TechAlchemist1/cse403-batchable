package com.batchable.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


@SpringBootTest
@AutoConfigureMockMvc
class RouteControllerMatrixIT_CI {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void testGetDistanceMatrix() throws Exception {
    mockMvc
        .perform(get("/routes/distance-matrix").param("origins", "Seattle,WA")
            .param("origins", "Redmond,WA").param("destinations", "Bellevue,WA")
            .param("destinations", "Redmond,WA").param("destinations", "Tacoma,WA"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.matrix").isArray())
        .andExpect(jsonPath("$.matrix.length()").value(2))
        .andExpect(jsonPath("$.matrix[0]").isArray())
        .andExpect(jsonPath("$.matrix[0].length()").value(3))
        .andExpect(jsonPath("$.matrix[0][0]").isNumber())
        .andExpect(jsonPath("$.matrix[0][1]").isNumber())
        .andExpect(jsonPath("$.matrix[0][2]").isNumber())
        .andExpect(jsonPath("$.matrix[1][0]").isNumber())
        .andExpect(jsonPath("$.matrix[1][1]").isNumber())
        .andExpect(jsonPath("$.matrix[1][2]").isNumber());
  }
}

package com.batchable.backend.unit;

import com.batchable.backend.controller.RouteController;
import com.batchable.backend.model.dto.DistanceMatrixResponse;
import com.batchable.backend.model.dto.DistanceMatrixResponse.MatrixElement;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteService routeService;

    @Test
    void testGetDistanceMatrixEndpoint() throws Exception {
        // Arrange
        MatrixElement e00 = new MatrixElement();
        e00.setOriginIndex(0);
        e00.setDestinationIndex(0);
        e00.setDistanceMeters(1000);
        e00.setDuration("600s");

        DistanceMatrixResponse mockResponse = new DistanceMatrixResponse(List.of(e00), false);

        Mockito.when(routeService.getDistanceMatrix(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(mockResponse);

        // Act & Assert
        mockMvc.perform(get("/routes/distance-matrix")
                        .param("origins", "Seattle,WA,Redmond,WA")
                        .param("destinations", "Bellevue,WA,Kirkland,WA")
                        .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.matrix[0][0]").value(1000));

        // Verify the service was called
        Mockito.verify(routeService).getDistanceMatrix(Mockito.anyList(), Mockito.anyList());
    }
}

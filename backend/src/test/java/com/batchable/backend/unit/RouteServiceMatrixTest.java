package com.batchable.backend.unit;

import com.batchable.backend.client.GoogleRoutesClient;
import com.batchable.backend.model.dto.DistanceMatrixResponse;
import com.batchable.backend.model.dto.DistanceMatrixResponse.MatrixElement;
import com.batchable.backend.service.RouteService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteServiceDistanceMatrixTimeTest {

    @Test
    void testGetDistanceMatrixUseTime() {
        // Arrange
        GoogleRoutesClient mockClient = Mockito.mock(GoogleRoutesClient.class);
        List<MatrixElement> elements = getMatrixElements();

        // Stub the client to return a DistanceMatrixResponse using duration
        Mockito.when(mockClient.getDistanceMatrix(Mockito.any()))
                .thenReturn(new DistanceMatrixResponse(elements, true)); // true = use duration

        RouteService service = new RouteService(mockClient);

        List<String> origins = List.of("Seattle,WA", "Redmond,WA");
        List<String> destinations = List.of("Bellevue,WA", "Kirkland,WA");

        // Act
        DistanceMatrixResponse response = service.getDistanceMatrix(origins, destinations);

        // Assert that the matrix now contains duration in seconds
        int[][] matrix = response.getMatrix();
        assertEquals(600, matrix[0][0]);
        assertEquals(1200, matrix[0][1]);
        assertEquals(900, matrix[1][0]);
        assertEquals(1500, matrix[1][1]);

        // Verify that the client was called
        Mockito.verify(mockClient).getDistanceMatrix(Mockito.any());
    }

    @Test
    void testGetDistanceMatrixUseDistance() {
        // Arrange
        GoogleRoutesClient mockClient = Mockito.mock(GoogleRoutesClient.class);
        List<MatrixElement> elements = getMatrixElements();

        // Stub the client to return a DistanceMatrixResponse using distance
        Mockito.when(mockClient.getDistanceMatrix(Mockito.any()))
                .thenReturn(new DistanceMatrixResponse(elements, false)); // false = use distance

        RouteService service = new RouteService(mockClient);

        List<String> origins = List.of("Seattle,WA", "Redmond,WA");
        List<String> destinations = List.of("Bellevue,WA", "Kirkland,WA");

        // Act
        DistanceMatrixResponse response = service.getDistanceMatrix(origins, destinations);

        // Assert that the matrix now contains duration in seconds
        int[][] matrix = response.getMatrix();
        assertEquals(1000, matrix[0][0]);
        assertEquals(2000, matrix[0][1]);
        assertEquals(1500, matrix[1][0]);
        assertEquals(2500, matrix[1][1]);

        // Verify that the client was called
        Mockito.verify(mockClient).getDistanceMatrix(Mockito.any());
    }

    private List<MatrixElement> getMatrixElements() {
        MatrixElement e00 = new MatrixElement();
        e00.setOriginIndex(0);
        e00.setDestinationIndex(0);
        e00.setDistanceMeters(1000);
        e00.setDuration("600s"); 

        MatrixElement e01 = new MatrixElement();
        e01.setOriginIndex(0);
        e01.setDestinationIndex(1);
        e01.setDistanceMeters(2000);
        e01.setDuration("1200s"); 

        MatrixElement e10 = new MatrixElement();
        e10.setOriginIndex(1);
        e10.setDestinationIndex(0);
        e10.setDistanceMeters(1500);
        e10.setDuration("900s");  

        MatrixElement e11 = new MatrixElement();
        e11.setOriginIndex(1);
        e11.setDestinationIndex(1);
        e11.setDistanceMeters(2500);
        e11.setDuration("1500s"); 

        return List.of(e00, e01, e10, e11);
    }

}

package com.aws.iot.evergreen.cli.impl;

import com.aws.iot.evergreen.cli.util.logs.impl.VisualizationImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VisualizationImplTest {
    private static final String logEntry = "{\"thread\":\"idle-connection-reaper\",\"level\":\"DEBUG\"," +
            "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\"," +
            "\"timestamp\":1594836028088,\"cause\":null}";

    private static final String badLogEntry = "{\"threeeeeeead\":\"idle-connection-reaper\",\"level\":\"DEBUG\"," +
            "\"eventType\":\"null\",\"message\":\"Closing connections idle longer than 60000 MILLISECONDS\"," +
            "\"timestamp\":1594836028088,\"cause\":null}";


    @Test
    void VisualizeHappyCase() {
        VisualizationImpl visualization = new VisualizationImpl();

        assertEquals("2020 Jul 15 06:00:28 [DEBUG] (idle-connection-reaper) null: null. " +
                "Closing connections idle longer than 60000 MILLISECONDS", visualization.Visualize(logEntry));
    }

    @Test
    void VisualizeFailToSerialize() {
        VisualizationImpl visualization = new VisualizationImpl();
        Exception failToSerialize = assertThrows(RuntimeException.class,
                () -> visualization.Visualize(badLogEntry));
        assertTrue(failToSerialize.getMessage().contains("Failed to serialize: " + badLogEntry));
    }
}

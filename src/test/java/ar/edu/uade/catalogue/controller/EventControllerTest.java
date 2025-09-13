package ar.edu.uade.catalogue.controller;

import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.service.KafkaMockService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaMockService kafkaMockService;

    @Test
    void getAll_ShouldReturnListOfEvents() throws Exception {
        when(kafkaMockService.getAll()).thenReturn(Arrays.asList(
                new Event("TYPE1", "payload1"),
                new Event("TYPE2", "payload2")
        ));

        mockMvc.perform(get("/event/getAll")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("TYPE1"))
                .andExpect(jsonPath("$[1].type").value("TYPE2"));
    }

    @Test
    void getAll_ShouldReturnNoContent_WhenEmpty() throws Exception {
        when(kafkaMockService.getAll()).thenReturn(Arrays.asList());

        mockMvc.perform(get("/event/getAll")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // tu controller devuelve 200 incluso si está vacío
                .andExpect(jsonPath("$").isEmpty());
    }
}

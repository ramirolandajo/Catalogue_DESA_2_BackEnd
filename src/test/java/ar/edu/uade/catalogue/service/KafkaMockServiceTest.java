package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaMockServiceTest {

    private EventRepository eventRepository;
    private KafkaMockService kafkaMockService;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        kafkaMockService = new KafkaMockService();
        kafkaMockService.eventRepository = eventRepository; // inyectamos mock
    }

    @Test
    void sendEvent_ShouldSaveJsonPayload() {
        // Arrange
        TestPayload payload = new TestPayload("test", 123);
        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Event savedEvent = kafkaMockService.sendEvent("TEST", payload);

        // Assert
        assertEquals("TEST", savedEvent.getType());
        assertTrue(savedEvent.getPayload().contains("\"name\":\"test\""));
        verify(eventRepository, times(1)).save(any(Event.class));
    }

    @Test
    void sendEvent_ShouldFallbackToToString_WhenJsonFails() {
        Object invalidPayload = new Object() {
            @Override
            public String toString() {
                return "fallback-string";
            }
        };

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Event savedEvent = kafkaMockService.sendEvent("FAIL_JSON", invalidPayload);

        assertEquals("FAIL_JSON", savedEvent.getType());
        assertEquals("fallback-string", savedEvent.getPayload());
    }

    @Test
    void getAll_ShouldReturnEvents() {
        List<Event> events = Arrays.asList(
                new Event("TYPE1", "payload1"),
                new Event("TYPE2", "payload2")
        );
        when(eventRepository.findAll()).thenReturn(events);

        List<Event> result = kafkaMockService.getAll();

        assertEquals(2, result.size());
        verify(eventRepository, times(1)).findAll();
    }

    private record TestPayload(String name, int value) {}
}

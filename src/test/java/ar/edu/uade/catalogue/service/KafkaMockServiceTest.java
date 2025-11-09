package ar.edu.uade.catalogue.service;

import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class KafkaMockServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KafkaMockService kafkaMockService;

    private Event savedEvent;

    @BeforeEach
    void setUp() {
        savedEvent = new Event("TEST_EVENT", "{\"key\":\"value\"}");
        savedEvent.setId(1);
    }

    // ---------------------------------------------------------
    // sendEvent()
    // ---------------------------------------------------------

    @Test
    @DisplayName("shouldSerializePayloadAndSaveEventSuccessfully")
    void shouldSerializePayloadAndSaveEventSuccessfully() throws Exception {
        String payloadJson = "{\"id\":10,\"name\":\"Product\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(payloadJson);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Object payload = new Object();
        Event result = kafkaMockService.sendEvent("CREATE_PRODUCT", payload);

        assertNotNull(result);
        assertEquals("CREATE_PRODUCT", result.getType());
        assertEquals(payloadJson, result.getPayload());
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("shouldFallbackToStringWhenJsonProcessingFails")
    void shouldFallbackToStringWhenJsonProcessingFails() throws Exception {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("fail") {});
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        Event result = kafkaMockService.sendEvent("FALLBACK", payload);

        assertNotNull(result);
        assertEquals("FALLBACK", result.getType());
        assertEquals(String.valueOf(payload), result.getPayload());
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    @DisplayName("shouldThrowRuntimeExceptionWhenRepositoryFails")
    void shouldThrowRuntimeExceptionWhenRepositoryFails() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"ok\":true}");
        when(eventRepository.save(any(Event.class))).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                kafkaMockService.sendEvent("ERROR", new Object())
        );

        assertTrue(ex.getMessage().contains("Error serializando payload"));
    }

    // ---------------------------------------------------------
    // getAll()
    // ---------------------------------------------------------

    @Test
    @DisplayName("shouldReturnAllEventsFromRepository")
    void shouldReturnAllEventsFromRepository() {
        when(eventRepository.findAll()).thenReturn(List.of(savedEvent));

        List<Event> result = kafkaMockService.getAll();

        assertEquals(1, result.size());
        assertEquals("TEST_EVENT", result.get(0).getType());
        verify(eventRepository).findAll();
    }

    @Test
    @DisplayName("shouldReturnEmptyListWhenRepositoryHasNoEvents")
    void shouldReturnEmptyListWhenRepositoryHasNoEvents() {
        when(eventRepository.findAll()).thenReturn(List.of());

        List<Event> result = kafkaMockService.getAll();

        assertTrue(result.isEmpty());
        verify(eventRepository).findAll();
    }
}

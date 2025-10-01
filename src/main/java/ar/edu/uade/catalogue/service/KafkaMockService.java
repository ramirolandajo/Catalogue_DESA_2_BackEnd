package ar.edu.uade.catalogue.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.uade.catalogue.model.Event;
import ar.edu.uade.catalogue.repository.EventRepository;

@Service
public class KafkaMockService {

    @Autowired 
    EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper; // usar el mapper de Spring (con JavaTimeModule)

    public Event sendEvent(String type, Object payload) {
        try {
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                payloadJson = String.valueOf(payload);
            }
            // Solo persistimos en la tabla event; la emisión HTTP se maneja en InventoryEventPublisher/CoreApiClient
            return eventRepository.save(new Event(type, payloadJson));
        } catch (Exception e) {
            throw new RuntimeException("Error serializando payload", e);
        }
    }

    public List<Event>getAll(){
        return eventRepository.findAll();
    }
}

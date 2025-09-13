package ar.edu.uade.catalogue.service;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Event sendEvent(String type, Object payload) {
        try {
            // si falla, lo guarda con toString()
            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException e) {
                payloadJson = payload.toString();
            }
            return eventRepository.save(new Event(type, payloadJson));
        } catch (Exception e) {
            throw new RuntimeException("Error serializando payload", e);
        }
    }
}

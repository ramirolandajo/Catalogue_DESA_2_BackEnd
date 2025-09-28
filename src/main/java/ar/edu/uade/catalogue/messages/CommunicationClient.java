package ar.edu.uade.catalogue.messages;

import ar.edu.uade.catalogue.model.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@AllArgsConstructor
@NoArgsConstructor(force = true)
@Getter
@Setter

@Service
public class CommunicationClient {

    private final RestTemplate restTemplate = new RestTemplate();

    private final KeycloakTokenService tokenService;

    private String originModule = "Catalogue";

    public CommunicationClient(KeycloakTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public void sendEvent(Event event) {
        String token = tokenService.getAccessToken();
        if (token == null) {
            throw new RuntimeException("No se pudo obtener token de Keycloak");
        }

        event.setOriginModule(originModule);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Event> request = new HttpEntity<>(event, headers);
        System.out.println(request.getBody());

        // Cuando este el endpoint/url real cambiar
        restTemplate.postForEntity("http://communication-service/api/events", request, Void.class);
    }
}

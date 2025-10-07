package ar.edu.uade.catalogue.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Component
public class KeycloakClient {
    private static final Logger log = LoggerFactory.getLogger(KeycloakClient.class);

    private final RestTemplate restTemplate;

    @Value("${keycloak.token.url}")
    private String tokenUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

   public KeycloakClient(RestTemplateBuilder builder) {
       // RestTemplate sin interceptor
       this.restTemplate = builder
               .requestFactory(() -> {
                   var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
                   factory.setConnectTimeout(Duration.ofSeconds(5));
                   factory.setReadTimeout(Duration.ofSeconds(10));
                   return factory;
               })
               .build();
   }

    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("expires_in") int expiresIn) {}

    public TokenResponse fetchClientCredentialsToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            return restTemplate.postForObject(tokenUrl, new HttpEntity<>(form, headers), TokenResponse.class);
        } catch (RestClientException e) {
            log.error("[Auth] Error obteniendo token en {}: {}", tokenUrl, e.getMessage());
            throw new RuntimeException("Error solicitando token a Keycloak", e);
        }
    }
}


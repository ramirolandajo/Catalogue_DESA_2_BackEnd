package ar.edu.uade.catalogue.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BackendTokenManager {
    private static final Logger log = LoggerFactory.getLogger(BackendTokenManager.class);

    private final KeycloakClient keycloakClient;

    @Value("${keycloak.refresh.enabled:false}")
    private boolean refreshEnabled;

    @Value("${keycloak.token.maxAttempts:3}")
    private int maxAttempts;

    @Value("${keycloak.token.backoff.ms:500}")
    private long backoffMs;

    private volatile String cachedToken;
    private volatile long expiresAtEpochSeconds = 0L;

    public BackendTokenManager(KeycloakClient keycloakClient) {
        this.keycloakClient = keycloakClient;
    }

    public synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && now < (expiresAtEpochSeconds - 30)) { // 30s skew
            return cachedToken;
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                KeycloakClient.TokenResponse tr = keycloakClient.fetchClientCredentialsToken();
                this.cachedToken = tr.accessToken();
                // Recalcular now dentro del ciclo
                now = Instant.now().getEpochSecond();
                this.expiresAtEpochSeconds = now + Math.max(30, tr.expiresIn());
                log.debug("[Auth] Token obtenido en intento {}. Expira en {}s", attempt, tr.expiresIn());
                return cachedToken;
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("[Auth] Falló intento {} de {} para obtener token: {}", attempt, maxAttempts, ex.toString());
                if (attempt < maxAttempts) {
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw last != null ? last : new RuntimeException("No se pudo obtener token");
    }
}

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
        int attempts = 0;
        RuntimeException last = null;
        while (attempts < 2) {
            attempts++;
            try {
                KeycloakClient.TokenResponse tr = keycloakClient.fetchClientCredentialsToken();
                this.cachedToken = tr.accessToken();
                this.expiresAtEpochSeconds = now + Math.max(30, tr.expiresIn());
                log.debug("[Auth] Token obtenido, expira en {}s", tr.expiresIn());
                return cachedToken;
            } catch (RuntimeException ex) {
                last = ex;
                try { Thread.sleep(400L * attempts); } catch (InterruptedException ignored) {}
            }
        }
        throw last != null ? last : new RuntimeException("No se pudo obtener token");
    }
}


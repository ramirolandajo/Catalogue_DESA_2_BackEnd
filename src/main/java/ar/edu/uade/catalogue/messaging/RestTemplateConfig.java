package ar.edu.uade.catalogue.messaging;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, BearerTokenInterceptor bearerTokenInterceptor) {
        RestTemplate rt = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(rt.getInterceptors());
        interceptors.add(bearerTokenInterceptor);
        rt.setInterceptors(interceptors);
        return rt;
    }
}


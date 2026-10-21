package com.jacolp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Context/mapper tests never call a model and must not initialize a network-backed JDK selector. */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineHttpTestConfiguration {
    @Bean
    RestClient.Builder offlineRestClientBuilder() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }

    @Bean
    WebClient.Builder offlineWebClientBuilder() {
        return WebClient.builder().clientConnector((method, uri, callback) ->
                Mono.error(new IllegalStateException("HTTP calls are disabled in offline context tests")));
    }
}

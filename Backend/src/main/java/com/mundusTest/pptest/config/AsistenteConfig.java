package com.mundusTest.pptest.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AsistenteConfig {
  @Bean("asistenteRestClient")
  public RestClient asistenteRestClient(
      @Value("${asistente.url:http://127.0.0.1:8002}") String url,
      @Value("${asistente.timeout-segundos:210}") int timeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(timeout));
    return RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }
}

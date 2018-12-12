package com.yugabyte.servicebroker;


import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;


@Configuration
@EnableAutoConfiguration
@ComponentScan("com.yugabyte.servicebroker")
public class YugaByteServiceTestConfig {
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
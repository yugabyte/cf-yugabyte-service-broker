/* Copyright (c) YugaByte, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.yugabyte.servicebroker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yugabyte.servicebroker.config.YugaByteServiceConfig;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
@DataJpaTest
public class YugaByteAdminServiceUnitTest {
  @Mock
  private RestTemplate mockRestTemplate;
  @Mock
  YugaByteServiceConfig mockAdminConfig;
  @Mock
  ServiceInstanceRepository mockInstanceRepository;
  @Mock
  YugaByteConfigRepository mockYugaByteConfigRepository;

  private YugaByteAdminService adminService;

  @Before
  public void setUp() {
    mockInstanceRepository = mock(ServiceInstanceRepository.class);
    mockYugaByteConfigRepository = mock(YugaByteConfigRepository.class);
    mockAdminConfig = mock(YugaByteServiceConfig.class);
    mockRestTemplate = mock(RestTemplate.class);
    mockAdminConfig.hostname = "localhost";
    mockAdminConfig.password = "password";
    mockAdminConfig.port= "9001";
    mockAdminConfig.user = "user";
    adminService = new YugaByteAdminService(mockAdminConfig, mockInstanceRepository,
        mockYugaByteConfigRepository, mockRestTemplate);
  }

  private HttpEntity getEntity(JsonNode bodyJson, String authToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (authToken != null && !authToken.isEmpty()) {
      headers.set("X-AUTH-TOKEN", authToken);
    }
    if (bodyJson == null) {
      return new HttpEntity<>(headers);
    } else {
      return new HttpEntity<>(bodyJson, headers);
    }
  }
  ObjectMapper mapper = new ObjectMapper();

  protected void setAuthToken() {
    ObjectNode authParams = mapper.createObjectNode();
    authParams.put("email", mockAdminConfig.user);
    authParams.put("password", mockAdminConfig.password);
    ObjectNode responseJson = mapper.createObjectNode();
    responseJson.put("authToken", "someToken");
    responseJson.put("customerUUID", "someUUID");

    Mockito.when(mockRestTemplate.exchange("http://localhost:9001/api/login",
        HttpMethod.POST, getEntity(authParams, null), JsonNode.class)).thenReturn(
        new ResponseEntity(responseJson, HttpStatus.OK));
  }

  protected void mockEndpoint(HttpMethod method, String endpoint, JsonNode body, HttpStatus status, JsonNode responseJson) {
    Mockito.when(mockRestTemplate.exchange("http://localhost:9001/api/customers/someUUID/" + endpoint,
        method, getEntity(body, "someToken"), JsonNode.class)).thenReturn(
        new ResponseEntity(responseJson, status));
  }

  @Test
  public void testSomething() throws IOException {
    setAuthToken();
    mockEndpoint(HttpMethod.GET, "releases", null, HttpStatus.OK, mapper.readTree("[\"1\", \"2\"]"));
    List<String> releases = adminService.getReleases();
    assertEquals(Arrays.asList("2", "1"), releases);
  }
}

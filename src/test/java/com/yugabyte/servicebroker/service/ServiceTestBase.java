package com.yugabyte.servicebroker.service;

import com.yugabyte.servicebroker.config.YugaByteServiceConfig;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.mockito.Mockito.mock;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;

@RunWith(MockitoJUnitRunner.class)
@DataJpaTest
abstract public class ServiceTestBase {

  YugaByteServiceConfig mockAdminConfig;
  ServiceInstanceRepository mockInstanceRepository;
  YugaByteConfigRepository mockYugaByteConfigRepository;
  static ClientAndServer mockServer;

  @BeforeClass
  public static void startServer() {
    mockServer = startClientAndServer(9001);
  }

  @AfterClass
  public static void stopServer() {
    mockServer.stop();
  }

  public void setUp() {
    mockInstanceRepository = mock(ServiceInstanceRepository.class);
    mockYugaByteConfigRepository = mock(YugaByteConfigRepository.class);
    mockAdminConfig = mock(YugaByteServiceConfig.class);
    mockAdminConfig.hostname = "localhost";
    mockAdminConfig.password = "password";
    mockAdminConfig.port= "9001";
    mockAdminConfig.user = "user";
    mockServer.reset();
  }

  protected void setAuthToken() {
    mockServer.when(HttpRequest.request().withPath("/api/login")).respond(
        HttpResponse.response().withBody("{\"authToken\": \"someToken\", \"customerUUID\": \"someUUID\"}")
    );
  }

  protected void mockEndpoint(String method, String endpoint, String responseJson) {
    mockServer.when(HttpRequest.request()
        .withPath("/api/customers/someUUID/" + endpoint)
        .withMethod(method)
        .withHeader(new Header("\"Content-type\", \"application/json\""))
        .withHeader(new Header("\"X-AUTH-TOKEN\", \"someToken\""))
    ).respond(
        HttpResponse.response().withBody(responseJson)
    );
  }
}

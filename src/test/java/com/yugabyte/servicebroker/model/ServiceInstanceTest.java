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
package com.yugabyte.servicebroker.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@DataJpaTest
public class ServiceInstanceTest {


  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private ServiceInstanceRepository serviceInstanceRepository;

  private ServiceInstance defaultServiceInstance;
  private String defaultUniverseUUID;

  @Before
  public void setUp() {
    CreateServiceInstanceRequest.CreateServiceInstanceRequestBuilder builder =
        CreateServiceInstanceRequest.builder();
    builder.planId("plan-1");
    builder.serviceDefinitionId("sd-1");
    builder.serviceInstanceId("instance-1");
    defaultUniverseUUID = UUID.randomUUID().toString();

    defaultServiceInstance = new ServiceInstance(builder.build(),
        defaultUniverseUUID);
    entityManager.persistAndFlush(defaultServiceInstance);
  }

  @Test
  public void testGetInstance() {
    ServiceInstance foundSI = serviceInstanceRepository
        .findById(defaultServiceInstance.getServiceInstanceId()).orElse(null);
    assertNotNull(foundSI);
    assertEquals(ServiceInstance.UniverseState.CREATING, foundSI.getUniverseState());
    assertEquals(defaultUniverseUUID, foundSI.getUniverseUUID());
    assertEquals("plan-1", foundSI.getPlanId());
    assertEquals("sd-1", foundSI.getServiceDefinitionId());
    assertEquals("instance-1", foundSI.getServiceInstanceId());
  }

  @Test
  public void testUpdateStateForFailedUniverse() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode universeDetails = mapper.createObjectNode();
    universeDetails.put("updateInProgress", false);
    universeDetails.put("updateSucceeded", false);
    defaultServiceInstance.updateState(universeDetails);
    assertEquals(ServiceInstance.UniverseState.ERROR, defaultServiceInstance.getUniverseState());
  }

  @Test
  public void testUpdateStateForInProgressUniverse() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode universeDetails = mapper.createObjectNode();
    universeDetails.put("updateInProgress", true);
    universeDetails.put("updateSucceeded", false);
    defaultServiceInstance.updateState(universeDetails);
    assertEquals(ServiceInstance.UniverseState.CREATING, defaultServiceInstance.getUniverseState());
  }

  @Test
  public void testUpdateStateForSuccessUniverse() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode universeDetails = mapper.createObjectNode();
    universeDetails.put("updateInProgress", false);
    universeDetails.put("updateSucceeded", true);
    defaultServiceInstance.updateState(universeDetails);
    assertEquals(ServiceInstance.UniverseState.LIVE, defaultServiceInstance.getUniverseState());
  }
}
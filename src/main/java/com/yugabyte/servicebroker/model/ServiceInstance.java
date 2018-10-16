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

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "service_instances")
public class ServiceInstance {
  public enum UniverseState {
    CREATING,
    UPDATING,
    DELETING,
    LIVE,
    ERROR
  }

  @Id
  @Column(length = 50)
  private String instanceId;

  @Column(length = 50)
  private String serviceDefinitionId;

  @Column(length = 50)
  private String planId;

  @Column(length = 50)
  private String universeUUID;

  @Column(length = 25)
  private UniverseState universeState;

  @SuppressWarnings("unused")
  private ServiceInstance() {}

  public ServiceInstance(CreateServiceInstanceRequest request,
                         String universeUUID) {
    this.serviceDefinitionId = request.getServiceDefinitionId();
    this.planId = request.getPlanId();
    this.instanceId = request.getServiceInstanceId();
    this.universeUUID = universeUUID;
    this.universeState = UniverseState.CREATING;
  }

  public void updateState(UniverseState universeState) {
    this.universeState = universeState;
  }

  public void updateState(JsonNode universeDetails) {
    boolean updateInProgress = universeDetails.get("updateInProgress").asBoolean();
    if ((this.universeState == UniverseState.CREATING ||
        this.universeState == UniverseState.UPDATING) && !updateInProgress) {
      boolean updateSucceeded = universeDetails.get("updateSucceeded").asBoolean();
      this.universeState = updateSucceeded ? UniverseState.LIVE : UniverseState.ERROR;
    }
  }

  public String getServiceInstanceId() {
    return this.instanceId;
  }

  public String getServiceDefinitionId() {
    return this.serviceDefinitionId;
  }

  public String getPlanId() {
    return this.planId;
  }

  public String getUniverseUUID() {
    return this.universeUUID;
  }

  public UniverseState getUniverseState() {
    return this.universeState;
  }
}

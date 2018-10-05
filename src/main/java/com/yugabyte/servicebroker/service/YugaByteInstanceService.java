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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.GetLastServiceOperationRequest;
import org.springframework.cloud.servicebroker.model.instance.GetLastServiceOperationResponse;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.GetServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.instance.OperationState;
import org.springframework.cloud.servicebroker.model.instance.UpdateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.UpdateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.service.ServiceInstanceService;
import org.springframework.stereotype.Service;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import com.yugabyte.servicebroker.model.ServiceInstance;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;

import java.util.Optional;

@Service
public class YugaByteInstanceService implements ServiceInstanceService {

  @Autowired
  YugaByteAdminService adminService;

  @Autowired
  YugaByteMetadataService metadataService;

  private final ServiceInstanceRepository instanceRepository;

  public YugaByteInstanceService(ServiceInstanceRepository instanceRepository) {
    this.instanceRepository = instanceRepository;
  }

  @Override
  public CreateServiceInstanceResponse createServiceInstance(CreateServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    CreateServiceInstanceResponse.CreateServiceInstanceResponseBuilder responseBuilder = CreateServiceInstanceResponse.builder();

    if (instanceRepository.existsById(instanceId)) {
      responseBuilder.instanceExisted(true);
      return responseBuilder.build();
    } else {
      JsonNode params = metadataService.getClusterPayload(request);
      JsonNode response = adminService.createUniverse(params);
      if (response.has("error")) {
        throw new YugaByteServiceException(response.get("error").asText());
      }

      ServiceInstance serviceInstance = new ServiceInstance(request, response.get("universeUUID").asText());
      instanceRepository.save(serviceInstance);
      return CreateServiceInstanceResponse.builder()
          .operation("Universe is being created: " + response.get("universeUUID").asText())
          .async(true)
          .build();
    }
  }

  @Override
  public UpdateServiceInstanceResponse updateServiceInstance(UpdateServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    UpdateServiceInstanceResponse.UpdateServiceInstanceResponseBuilder responseBuilder = UpdateServiceInstanceResponse.builder();

    Optional<ServiceInstance> serviceInstance = instanceRepository.findById(instanceId);
    if (!serviceInstance.isPresent()) {
      throw new ServiceInstanceDoesNotExistException(instanceId);
    }

    return responseBuilder
        .async(true)
        .operation("NOT IMPLEMENTED!!")
        .build();
  }

  @Override
  public DeleteServiceInstanceResponse deleteServiceInstance(DeleteServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    Optional<ServiceInstance> serviceInstance = instanceRepository.findById(instanceId);

    if (serviceInstance.isPresent()) {
      ServiceInstance si = serviceInstance.get();
      String universeUUID = si.getUniverseUUID();
      JsonNode response = adminService.deleteUniverse(universeUUID);
      if (response.has("error")) {
        si.updateState(ServiceInstance.UniverseState.ERROR);
        instanceRepository.save(si);
        return DeleteServiceInstanceResponse.builder()
            .operation("Delete Universe: " + si.getServiceInstanceId() + " Failed!")
            .async(true)
            .build();
      } else {
        si.updateState(ServiceInstance.UniverseState.DELETING);
        instanceRepository.save(si);
        return DeleteServiceInstanceResponse.builder()
            .operation("Deleting Universe: " + si.getServiceInstanceId())
            .async(true)
            .build();
      }
    } else {
      throw new ServiceInstanceDoesNotExistException(instanceId);
    }
  }

  @Override
  public GetServiceInstanceResponse getServiceInstance(GetServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    Optional<ServiceInstance> serviceInstance = instanceRepository.findById(instanceId);

    if (serviceInstance.isPresent()) {
      ServiceInstance si = serviceInstance.get();
      String universeUUID = si.getUniverseUUID();
      JsonNode response = adminService.getUniverse(universeUUID);
      si.updateState(response.get("universeDetails"));
      instanceRepository.save(si);
      return GetServiceInstanceResponse.builder()
          .serviceDefinitionId(serviceInstance.get().getServiceDefinitionId())
          .planId(serviceInstance.get().getPlanId())
          .parameters("universeState", si.getUniverseState())
          .parameters("universeUUID", si.getUniverseUUID())
          .build();
    } else {
      throw new ServiceInstanceDoesNotExistException(instanceId);
    }
  }

  @Override
  public GetLastServiceOperationResponse getLastOperation(GetLastServiceOperationRequest request) {
    String instanceId = request.getServiceInstanceId();
    Optional<ServiceInstance> serviceInstance = instanceRepository.findById(instanceId);

    if (serviceInstance.isPresent()) {
      ServiceInstance si = serviceInstance.get();
      String universeUUID = si.getUniverseUUID();
      JsonNode response = adminService.getUniverse(universeUUID);
      OperationState state;
      // If the error is invalid universe, likely the universe is deleted
      if (response.has("error") &&
          response.get("error").asText().contains("Invalid Universe UUID") &&
          si.getUniverseState().equals(ServiceInstance.UniverseState.DELETING)) {
        state = OperationState.SUCCEEDED;
      } else {
        si.updateState(response.get("universeDetails"));
        instanceRepository.save(si);
        if (si.getUniverseState().equals(ServiceInstance.UniverseState.LIVE)) {
          state = OperationState.SUCCEEDED;
        } else if (si.getUniverseState().equals(ServiceInstance.UniverseState.ERROR)) {
          state = OperationState.FAILED;
        } else {
          state = OperationState.IN_PROGRESS;
        }
      }
      return GetLastServiceOperationResponse.builder()
          .operationState(state)
          .build();
    } else {
      throw new ServiceInstanceDoesNotExistException(instanceId);
    }
  }
}
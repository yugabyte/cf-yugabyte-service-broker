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

import com.yugabyte.servicebroker.model.ServiceBinding;
import com.yugabyte.servicebroker.repository.ServiceBindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingDoesNotExistException;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.DeleteServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class YugaByteBindingService implements ServiceInstanceBindingService {

  private final ServiceBindingRepository bindingRepository;

  @Autowired
  private YugaByteAdminService adminService;

  public YugaByteBindingService(ServiceBindingRepository bindingRepository) {
    this.bindingRepository = bindingRepository;
  }

  @Override
  public CreateServiceInstanceBindingResponse createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
    String bindingId = request.getBindingId();
    Optional<ServiceBinding> binding = bindingRepository.findById(bindingId);
    CreateServiceInstanceAppBindingResponse.CreateServiceInstanceAppBindingResponseBuilder responseBuilder =
        CreateServiceInstanceAppBindingResponse.builder();

    if (binding.isPresent()) {
      responseBuilder
          .bindingExisted(true)
          .credentials(binding.get().getCredentials());
    } else {
      Map<String, Object> serviceEndpoints = adminService.getUniverseServiceEndpoints(
          request
      );
      ServiceBinding serviceBinding =
          new ServiceBinding(request.getBindingId(), request.getServiceInstanceId(), serviceEndpoints);
      bindingRepository.save(serviceBinding);

      responseBuilder
          .bindingExisted(false)
          .credentials(serviceEndpoints);
    }

    return responseBuilder.build();
  }

  @Override
  public void deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
    String bindingId = request.getBindingId();
    Optional<ServiceBinding> serviceBinding = bindingRepository.findById(bindingId);

    if (bindingRepository.existsById(bindingId)) {
      adminService.deleteServiceBindingCredentials(serviceBinding.get());
      bindingRepository.deleteById(bindingId);
    } else {
      throw new ServiceInstanceBindingDoesNotExistException(bindingId);
    }
  }

  @Override
  public GetServiceInstanceBindingResponse getServiceInstanceBinding(GetServiceInstanceBindingRequest request) {
    String bindingId = request.getBindingId();

    Optional<ServiceBinding> serviceBinding = bindingRepository.findById(bindingId);

    if (serviceBinding.isPresent()) {
      return GetServiceInstanceAppBindingResponse.builder()
          .credentials(serviceBinding.get().getCredentials())
          .build();
    } else {
      throw new ServiceInstanceBindingDoesNotExistException(bindingId);
    }
  }
}
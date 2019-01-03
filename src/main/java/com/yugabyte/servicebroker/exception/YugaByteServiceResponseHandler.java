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
package com.yugabyte.servicebroker.exception;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;

public class YugaByteServiceResponseHandler implements ResponseErrorHandler {
  private static final Log logger = LogFactory.getLog(YugaByteServiceResponseHandler.class);

  @Override
  public void handleError(ClientHttpResponse response) throws IOException {
    if (response.getStatusCode() == HttpStatus.FORBIDDEN ||
        response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
      String errorMsg = "YugaWare API call is unauthorized, check credentials";
      logger.error(errorMsg);
      throw new AuthenticationException(errorMsg);
    }
  }

  @Override
  public boolean hasError(ClientHttpResponse response) throws IOException {
    return response.getStatusCode().isError();
  }
}
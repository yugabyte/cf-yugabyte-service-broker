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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.security.Key;
import java.util.Base64;

@Converter
public class ConverterMapToHash implements AttributeConverter<Object, String> {
  private final static ObjectMapper objectMapper = new ObjectMapper();
  private static final String ALGORITHM = "AES/ECB/PKCS5Padding";
  private static final byte[] KEY = "lfoxLSQT3FZe15=!".getBytes();

  @Override
  public String convertToDatabaseColumn(Object data) {
    Key key = new SecretKeySpec(KEY, "AES");

    try {
      Cipher c = Cipher.getInstance(ALGORITHM);
      c.init(Cipher.ENCRYPT_MODE, key);
      return Base64.getEncoder().encodeToString(
          c.doFinal(objectMapper.writeValueAsBytes(data))
      );
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Object convertToEntityAttribute(String dbData) {
    Key key = new SecretKeySpec(KEY, "AES");
    try {
      Cipher c = Cipher.getInstance(ALGORITHM);
      c.init(Cipher.DECRYPT_MODE, key);
      return objectMapper.readValue(
          c.doFinal(Base64.getDecoder().decode(dbData)), Object.class
      );
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
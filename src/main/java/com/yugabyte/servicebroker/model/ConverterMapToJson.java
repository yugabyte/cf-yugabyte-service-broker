package com.yugabyte.servicebroker.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;

import javax.persistence.AttributeConverter;
import java.io.IOException;

public class ConverterMapToJson implements AttributeConverter<Object, String> {

  private final static ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Object meta) {
    try {
      return objectMapper.writeValueAsString(meta);
    } catch (JsonProcessingException ex) {
      throw new YugaByteServiceException("Unable to parse data");
    }
  }

  @Override
  public Object convertToEntityAttribute(String dbData) {
    try {
      return objectMapper.readValue(dbData, Object.class);
    } catch (IOException ex) {
      throw new YugaByteServiceException(ex.getMessage());
    }
  }

}
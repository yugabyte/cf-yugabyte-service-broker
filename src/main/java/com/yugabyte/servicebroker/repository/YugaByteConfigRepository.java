package com.yugabyte.servicebroker.repository;

import com.yugabyte.servicebroker.model.YugaByteConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YugaByteConfigRepository extends JpaRepository<YugaByteConfig, String> {
}
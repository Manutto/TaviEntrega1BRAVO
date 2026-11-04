package com.mundusTest.pptest.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {
  private final JdbcTemplate jdbc;

  public HealthRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void verificarConexion() {
    jdbc.queryForObject("SELECT 1", Integer.class);
  }
}

package com.mundusTest.pptest.service;

import com.mundusTest.pptest.repository.HealthRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {
  private final HealthRepository repository;

  public HealthService(HealthRepository repository) {
    this.repository = repository;
  }

  public boolean disponible() {
    try {
      repository.verificarConexion();
      return true;
    } catch (DataAccessException ex) {
      return false;
    }
  }
}

package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.repository.guias.SeaPreparacionConsulta;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SeaPreparacionConsultaService {
  private final SeaPreparacionConsulta repositorio;

  public SeaPreparacionConsultaService(SeaPreparacionConsulta repositorio) {
    this.repositorio = repositorio;
  }

  public List<Map<String, Object>> guias(int limite) {
    return repositorio.guias(limite);
  }
}

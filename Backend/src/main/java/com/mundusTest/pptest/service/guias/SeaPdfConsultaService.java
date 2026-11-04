package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.repository.guias.SeaPdfRepositorio;
import org.springframework.stereotype.Service;

@Service
public class SeaPdfConsultaService {
  private final SeaPdfRepositorio repositorio;

  public SeaPdfConsultaService(SeaPdfRepositorio repositorio) {
    this.repositorio = repositorio;
  }

  public byte[] pdfActual(String tipo, int id) {
    return repositorio.pdfActual(tipo, id);
  }
}

package com.mundusTest.pptest.controller.consulta;

import com.mundusTest.pptest.dto.consulta.*;
import com.mundusTest.pptest.service.consulta.ConsultaService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consulta")
public class ConsultaController {
  private final ConsultaService consultas;

  public ConsultaController(ConsultaService consultas) {
    this.consultas = consultas;
  }

  @GetMapping("/catalogo")
  public Map<String, Object> catalogo() {
    return consultas.catalogo();
  }

  @PostMapping
  public PreInformeDTO consultar(@RequestBody ConsultaProyectoDTO proyecto) {
    return consultas.consultar(proyecto);
  }
}

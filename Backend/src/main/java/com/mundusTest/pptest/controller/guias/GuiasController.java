package com.mundusTest.pptest.controller.guias;

import com.mundusTest.pptest.service.guias.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/guias")
public class GuiasController {
  private final SeaFlujoService flujo;
  private final SeaPreparacionConsultaService consulta;
  private final SeaExportacionJsonService exportacion;
  private final SeaPdfConsultaService pdf;
  private final SeaVectorService vectores;

  public GuiasController(SeaFlujoService flujo, SeaPreparacionConsultaService consulta,
      SeaExportacionJsonService exportacion, SeaPdfConsultaService pdf, SeaVectorService vectores) {
    this.flujo = flujo;
    this.consulta = consulta;
    this.exportacion = exportacion;
    this.pdf = pdf;
    this.vectores = vectores;
  }

  @PostMapping("/ingestas")
  public ResponseEntity<Map<String, Object>> iniciar() {
    return ResponseEntity.accepted().body(flujo.iniciar());
  }

  @GetMapping("/ingestas")
  public List<Map<String, Object>> ingestas() {
    return flujo.recientes();
  }

  @GetMapping("/ingestas/{id}")
  public Map<String, Object> ingesta(@PathVariable long id) {
    if (id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID positivo requerido.");
    return flujo.obtener(id);
  }

  @GetMapping
  public List<Map<String, Object>> guias() {
    return consulta.guias(5000);
  }

  @GetMapping("/{tipo}/{id}/json")
  public ResponseEntity<Map<String, Object>> json(@PathVariable String tipo, @PathVariable int id) {
    validarGuia(tipo, id);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guia-" + id + ".json\"")
        .body(exportacion.exportar("GUIA", id));
  }

  @GetMapping("/{tipo}/{id}/pdf")
  public ResponseEntity<byte[]> original(@PathVariable String tipo, @PathVariable int id) {
    validarGuia(tipo, id);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"guia-" + id + ".pdf\"")
        .body(pdf.pdfActual("GUIA", id));
  }

  @GetMapping("/buscar")
  public Map<String, Object> buscar(@RequestParam String q,
      @RequestParam(defaultValue = "5") int limite) {
    if (q == null || q.isBlank() || q.length() > 1000 || limite < 1 || limite > 20)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulta de 1–1000 caracteres y límite de 1–20.");
    return vectores.buscar(q.strip(), limite);
  }

  private void validarGuia(String tipo, int id) {
    if (!"GUIA".equals(tipo) || id < 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona GUIA y un ID positivo.");
  }
}

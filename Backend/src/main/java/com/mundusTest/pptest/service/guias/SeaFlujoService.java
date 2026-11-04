package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.repository.guias.SeaFlujoRepositorio;
import jakarta.annotation.PreDestroy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeaFlujoService {
  private static final Logger log = LoggerFactory.getLogger(SeaFlujoService.class);
  private final SeaFlujoRepositorio repo;
  private final SeaSincronizacionService catalogo;
  private final SeaPdfIngestaService pdf;
  private final SeaPreparacionService preparacion;
  private final SeaExportacionJsonService exportacion;
  private final SeaVectorService vectores;
  private final JsonMapper mapper;
  private final Path directorio;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean ocupado = new AtomicBoolean();

  public SeaFlujoService(SeaFlujoRepositorio repo, SeaSincronizacionService catalogo,
      SeaPdfIngestaService pdf, SeaPreparacionService preparacion,
      SeaExportacionJsonService exportacion, SeaVectorService vectores, JsonMapper mapper,
      @Value("${sea.flujo.directorio:../output/guias}") String directorio) {
    this.repo = repo;
    this.catalogo = catalogo;
    this.pdf = pdf;
    this.preparacion = preparacion;
    this.exportacion = exportacion;
    this.vectores = vectores;
    this.mapper = mapper;
    this.directorio = Path.of(directorio).toAbsolutePath().normalize();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recuperar() {
    repo.interrumpirPendientes();
  }

  public synchronized Map<String, Object> iniciar() {
    if (!ocupado.compareAndSet(false, true))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una ingesta de guías en curso.");
    long id = -1;
    try {
      id = repo.crear();
      Map<String, Object> creado = obtener(id);
      long trabajo = id;
      executor.submit(() -> ejecutar(trabajo));
      return creado;
    } catch (RuntimeException error) {
      ocupado.set(false);
      if (id > 0) repo.actualizar(id, "ERROR", "INICIO", Map.of(), mensaje(error));
      if (error instanceof DataIntegrityViolationException)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una ingesta activa.", error);
      throw error;
    }
  }

  public List<Map<String, Object>> recientes() { return repo.recientes(); }

  public Map<String, Object> obtener(long id) {
    var trabajo = repo.obtener(id);
    if (trabajo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingesta no encontrada.");
    return trabajo;
  }

  private void ejecutar(long id) {
    Map<String, Object> resultado = new LinkedHashMap<>();
    List<Map<String, Object>> errores = new ArrayList<>();
    List<Map<String, Object>> advertencias = new ArrayList<>();
    resultado.put("errores", errores);
    resultado.put("advertencias", advertencias);
    String etapa = "EXCEL";
    try {
      avance(id, etapa, resultado);
      resultado.put("catalogo", catalogo.sincronizarDesdeSea());
      var guias = repo.guiasPresentes();
      resultado.put("total_guias", guias.size());
      if (guias.isEmpty()) throw new IllegalStateException("El índice no contiene guías.");
      Path json = directorio.resolve("ingestas").resolve(Long.toString(id)).resolve("json");
      Files.createDirectories(json);
      int procesadas = 0;
      int exportadas = 0;
      for (int guia : guias) {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Ingesta detenida.");
        etapa = "PDF_JSON";
        resultado.put("guia_actual", guia);
        avance(id, etapa, resultado);
        try {
          var descarga = pdf.procesar("GUIA", guia);
          long version = ((Number) descarga.get("version_id")).longValue();
          preparacion.prepararVersion(version, false);
          var documento = exportacion.exportar("GUIA", guia);
          if (documento.get("fragmentos") instanceof List<?> fragmentos && fragmentos.isEmpty()) {
            advertencias.add(Map.of("guia", guia, "mensaje",
                "PDF guardado, pero sin fragmentos de texto. Puede necesitar OCR; no aporta vectores."));
          } else if (documento.get("calidad") instanceof Map<?, ?> calidad
              && calidad.get("paginas_con_alertas") instanceof Number numero && numero.longValue() > 0) {
            advertencias.add(Map.of("guia", guia, "mensaje", "Hay páginas con alertas de extracción; revisar el PDF.",
                "paginas_con_alertas", numero.longValue()));
          }
          Files.writeString(json.resolve("guia-" + guia + ".json"), mapper.writeValueAsString(documento));
          exportadas++;
        } catch (Exception error) {
          errores.add(Map.of("guia", guia, "mensaje", mensaje(error)));
        }
        resultado.put("procesadas", ++procesadas);
        resultado.put("json_exportados", exportadas);
        avance(id, etapa, resultado);
      }
      resultado.remove("guia_actual");
      if (exportadas == 0) throw new IllegalStateException("Ninguna guía pudo exportarse; se conserva el índice anterior.");
      etapa = "VECTORES";
      avance(id, etapa, resultado);
      resultado.put("vectorizacion", vectores.construir(json));
      String estado = !errores.isEmpty() ? "COMPLETADO_CON_ERRORES"
          : !advertencias.isEmpty() ? "COMPLETADO_CON_ALERTAS" : "COMPLETADO";
      repo.actualizar(id, estado, "FINALIZADO", resultado, null);
    } catch (Exception error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Falló la ingesta de guías {} en la etapa {}", id, etapa, error);
      try {
        repo.actualizar(id, "ERROR", etapa, resultado, mensaje(error));
      } catch (RuntimeException persistencia) {
        log.error("No se pudo guardar el estado de error de la ingesta {}", id, persistencia);
      }
    } finally {
      ocupado.set(false);
    }
  }

  private void avance(long id, String etapa, Map<String, Object> resultado) {
    repo.actualizar(id, "EN_PROCESO", etapa, resultado, null);
  }

  private static String mensaje(Exception error) {
    String texto = error instanceof ResponseStatusException r ? r.getReason() : error.getMessage();
    if (texto == null) texto = error.getClass().getSimpleName();
    return texto.substring(0, Math.min(2000, texto.length()));
  }

  @PreDestroy
  public void cerrar() { executor.shutdownNow(); }
}

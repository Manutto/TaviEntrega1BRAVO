package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.repository.guias.SeaFlujoRepositorio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeaVectorService {
  private final String python;
  private final Path script;
  private final Path salida;
  private final Path cache;
  private final String modelo;
  private final int timeout;
  private final JsonMapper mapper;
  private final SeaFlujoRepositorio repo;
  private final Semaphore procesos = new Semaphore(1, true);

  public SeaVectorService(@Value("${sea.vector.python:../.venv-vector/Scripts/python.exe}") String python,
      @Value("${sea.vector.script:../Preparador/json_to_vector.py}") String script,
      @Value("${sea.vector.directorio:../output/guias/vectores}") String salida,
      @Value("${sea.vector.cache:../output/modelos/huggingface}") String cache,
      @Value("${sea.vector.modelo:intfloat/multilingual-e5-base}") String modelo,
      @Value("${sea.vector.timeout-segundos:7200}") int timeout,
      JsonMapper mapper, SeaFlujoRepositorio repo) {
    this.python = python;
    this.script = Path.of(script).toAbsolutePath().normalize();
    this.salida = Path.of(salida).toAbsolutePath().normalize();
    this.cache = Path.of(cache).toAbsolutePath().normalize();
    this.modelo = modelo;
    this.timeout = timeout;
    this.mapper = mapper;
    this.repo = repo;
  }

  public Map<String, Object> construir(Path entrada) throws IOException, InterruptedException {
    return ejecutar(List.of("construir", "--entrada", entrada.toString(), "--salida", salida.toString(),
        "--modelo", modelo), timeout);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> buscar(String consulta, int limite) {
    try {
      Map<String, Object> respuesta = ejecutar(List.of("buscar", "--salida", salida.toString(),
          "--consulta", consulta, "--top-k", Integer.toString(limite), "--modelo", modelo), 180);
      Object encontrados = respuesta.get("resultados");
      if (!(encontrados instanceof List<?> filas)) throw new IOException("El buscador no entregó resultados válidos.");
      for (Object item : filas) {
        if (!(item instanceof Map<?, ?> fila) || !(fila.get("documento") instanceof Map<?, ?> documento)
            || !(documento.get("id") instanceof Number numero)
            || !(documento.get("sha256_pdf") instanceof String hash)
            || !"GUIA".equals(documento.get("tipo")))
          throw new IOException("Resultado vectorial sin procedencia de guía verificable.");
        if (!repo.versionActual(numero.intValue(), hash))
          throw new ResponseStatusException(HttpStatus.CONFLICT,
              "El índice contiene una guía desactualizada. Ejecuta nuevamente la ingesta.");
      }
      respuesta.put("llm_utilizada", false);
      return respuesta;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Búsqueda interrumpida.", error);
    } catch (IOException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> ejecutar(List<String> argumentos, int segundos)
      throws IOException, InterruptedException {
    if (!procesos.tryAcquire())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "El motor vectorial está ocupado; espera a que termine.");
    Path temporal = null;
    Process proceso = null;
    try {
      if (!Files.isRegularFile(script)) throw new IOException("No se encontró json_to_vector.py.");
      Files.createDirectories(salida);
      temporal = Files.createTempDirectory("mundus-vector-");
      Path json = temporal.resolve("respuesta.json");
      Path errores = temporal.resolve("errores.log");
      List<String> comando = new ArrayList<>(List.of(python, script.toString()));
      comando.addAll(argumentos);
      ProcessBuilder builder = new ProcessBuilder(comando);
      builder.directory(script.getParent().toFile());
      builder.redirectOutput(json.toFile()).redirectError(errores.toFile());
      builder.environment().put("PYTHONIOENCODING", "utf-8");
      builder.environment().put("PYTHONDONTWRITEBYTECODE", "1");
      builder.environment().put("HF_HUB_CACHE", cache.toString());
      builder.environment().put("HF_HUB_OFFLINE", "1");
      builder.environment().put("TRANSFORMERS_OFFLINE", "1");
      builder.environment().put("TOKENIZERS_PARALLELISM", "false");
      builder.environment().put("OMP_NUM_THREADS", "2");
      builder.environment().put("MKL_NUM_THREADS", "2");
      proceso = builder.start();
      proceso.getOutputStream().close();
      long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(segundos);
      while (!proceso.waitFor(200, TimeUnit.MILLISECONDS)) {
        if (System.nanoTime() > limite) throw new IOException("El motor vectorial superó el tiempo máximo.");
        if (Files.size(json) > 16L * 1024 * 1024 || Files.size(errores) > 16L * 1024 * 1024)
          throw new IOException("La salida del motor vectorial excedió el límite.");
      }
      if (proceso.exitValue() != 0) {
        String detalle;
        try (var entrada = Files.newInputStream(errores)) {
          detalle = new String(entrada.readNBytes(2000), StandardCharsets.UTF_8).strip();
        }
        throw new IOException("No se pudo ejecutar FAISS. Comprueba el entorno Python y la caché del modelo. " + detalle);
      }
      if (Files.size(json) > 16L * 1024 * 1024) throw new IOException("Respuesta vectorial demasiado grande.");
      try {
        return mapper.readValue(Files.readString(json), Map.class);
      } catch (RuntimeException error) {
        throw new IOException("El motor vectorial no entregó un objeto JSON válido.", error);
      }
    } finally {
      try {
        if (proceso != null && proceso.isAlive()) {
          proceso.descendants().forEach(ProcessHandle::destroyForcibly);
          proceso.destroyForcibly();
          proceso.waitFor(5, TimeUnit.SECONDS);
        }
        if (temporal != null) {
          try (var archivos = Files.walk(temporal)) {
            for (Path archivo : archivos.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(archivo);
          }
        }
      } finally {
        procesos.release();
      }
    }
  }
}

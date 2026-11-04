package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.entity.SeaDocumento;
import com.mundusTest.pptest.repository.guias.SeaRepositorio;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeaSincronizacionService {
  private static final int MAX_EXCEL = 15 * 1024 * 1024;
  private final SeaCatalogoDescarga descarga;
  private final SeaCatalogoExcel lector;
  private final SeaRepositorio repositorio;
  private final JsonMapper mapper;
  private final TransactionTemplate transaccion;

  public SeaSincronizacionService(
      SeaCatalogoDescarga descarga,
      SeaCatalogoExcel lector,
      SeaRepositorio repositorio,
      JsonMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.descarga = descarga;
    this.lector = lector;
    this.repositorio = repositorio;
    this.mapper = mapper;
    this.transaccion = new TransactionTemplate(transactionManager);
  }

  public synchronized Map<String, Object> sincronizarDesdeSea() {
    long historialId = repositorio.iniciar(descarga.urlPagina());
    try {
      SeaCatalogoDescarga.Descarga excel = descarga.descargarIndice();
      return procesar(historialId, excel.urlExcel(), excel.bytes());
    } catch (Exception e) {
      repositorio.error(historialId, recortar(e.getMessage()));
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "No se pudo sincronizar el índice oficial del SEA: " + recortar(e.getMessage()),
          e);
    }
  }

  public synchronized Map<String, Object> importarArchivo(byte[] archivo) {
    long historialId = repositorio.iniciar("Carga manual del índice SEA");
    try {
      return procesar(historialId, "ARCHIVO_LOCAL", archivo);
    } catch (Exception e) {
      repositorio.error(historialId, recortar(e.getMessage()));
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No se pudo importar el Excel: " + recortar(e.getMessage()), e);
    }
  }

  private Map<String, Object> procesar(long historialId, String urlExcel, byte[] archivo)
      throws IOException {
    if (archivo.length == 0 || archivo.length > MAX_EXCEL) {
      throw new IllegalArgumentException("Excel vacío o demasiado grande (máximo 15 MB).");
    }
    List<SeaDocumento> documentos = lector.leer(archivo);
    List<String> json = new ArrayList<>(documentos.size());
    List<String> hashes = new ArrayList<>(documentos.size());
    Set<String> ids = new HashSet<>();
    for (SeaDocumento d : documentos) {
      String id = d.tipoDocumento() + ":" + d.idDocumento();
      if (!ids.add(id)) throw new IllegalArgumentException("ID duplicado en el Excel: " + id);
      String texto = mapper.writeValueAsString(d.datos());
      json.add(texto);
      Map<String, Object> contenido = new LinkedHashMap<>(d.datos());
      contenido.remove("fila_excel");
      hashes.add(sha256(mapper.writeValueAsBytes(contenido)));
    }
    String shaExcel = sha256(archivo);
    SeaRepositorio.Estadisticas stats =
        transaccion.execute(status -> repositorio.guardar(documentos, json, hashes));
    if (stats == null) throw new IllegalStateException("No se pudo completar la transacción.");
    repositorio.terminar(historialId, urlExcel, shaExcel, stats);
    Map<String, Object> respuesta = new LinkedHashMap<>();
    respuesta.put("estado", "OK");
    respuesta.put("sincronizacion_id", historialId);
    respuesta.put("url_excel", urlExcel);
    respuesta.put("sha256_excel", shaExcel);
    respuesta.put("total", stats.total());
    respuesta.put("nuevos", stats.nuevos());
    respuesta.put("actualizados", stats.actualizados());
    respuesta.put("sin_cambios", stats.sinCambios());
    respuesta.put("ausentes_del_indice", stats.ausentes());
    return respuesta;
  }

  private String recortar(String mensaje) {
    if (mensaje == null || mensaje.isBlank()) return "Error no especificado";
    return mensaje.length() > 800 ? mensaje.substring(0, 800) : mensaje;
  }

  private String sha256(byte[] bytes) {
    try {
      byte[] suma = MessageDigest.getInstance("SHA-256").digest(bytes);
      return java.util.HexFormat.of().formatHex(suma);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No se encontró SHA-256", e);
    }
  }
}

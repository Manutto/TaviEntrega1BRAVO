package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.dto.guias.PdfTextoExtraidoDTO;
import com.mundusTest.pptest.repository.guias.SeaPdfRepositorio;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeaPdfIngestaService {
  private final SeaPdfRepositorio repositorio;
  private final SeaPdfDescarga descarga;
  private final SeaPdfTextoExtractor extractor;
  private final JsonMapper mapper;
  private final TransactionTemplate tx;

  public SeaPdfIngestaService(
      SeaPdfRepositorio repositorio,
      SeaPdfDescarga descarga,
      SeaPdfTextoExtractor extractor,
      JsonMapper mapper,
      PlatformTransactionManager transactionManager) {
    this.repositorio = repositorio;
    this.descarga = descarga;
    this.extractor = extractor;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(transactionManager);
  }

  @SuppressWarnings("unchecked")
  public synchronized Map<String, Object> procesar(String tipo, int id) {
    Map<String, Object> registro = repositorio.obtenerDocumento(tipo, id);
    if (registro == null)
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "La guía/criterio no existe en el índice; sincroniza el Excel primero.");
    if (!Boolean.TRUE.equals(registro.get("presente_en_indice"))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Documento ausente del índice SEA actual; no se descargará automáticamente.");
    }
    Map<String, Object> datos = (Map<String, Object>) registro.get("datos");
    Object links = datos.get("enlaces");
    Object linkPdf = links instanceof Map<?, ?> ? ((Map<?, ?>) links).get("documento_pdf") : null;
    if (!(linkPdf instanceof String url) || url.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Este registro no tiene un enlace PDF en la celda NOMBRE del Excel.");
    }
    try {
      byte[] bytes = descarga.descargarPdf(url);
      String hash = sha256(bytes);
      Map<String, Object> anterior = repositorio.versionActual(tipo, id);
      if (anterior != null && hash.equals(anterior.get("sha256_pdf"))) {
        repositorio.marcarVerificado(((Number) anterior.get("version_id")).longValue(), url);
        return respuesta(
            "SIN_CAMBIOS", tipo, id, datos.get("nombre"), repositorio.versionActual(tipo, id));
      }
      Map<String, Object> vieja = repositorio.versionPorHash(tipo, id, hash);
      if (vieja != null) {
        tx.executeWithoutResult(
            status ->
                repositorio.activarExistente(
                    tipo, id, ((Number) vieja.get("version_id")).longValue(), url));
        return respuesta(
            "VERSION_REUTILIZADA",
            tipo,
            id,
            datos.get("nombre"),
            repositorio.versionActual(tipo, id));
      }
      PdfTextoExtraidoDTO texto = extractor.extraer(bytes);
      Map<String, Object> contenido = new LinkedHashMap<>();
      contenido.put("metadatos_extraccion", SeaPdfTextoExtractor.metadatos());
      contenido.put("tipo_documento", tipo);
      contenido.put("id_documento", id);
      contenido.put("nombre", datos.get("nombre"));
      contenido.put("url_pdf", url);
      contenido.put("sha256_pdf", hash);
      contenido.put("total_paginas", texto.numeroPaginas());
      contenido.put("caracteres_extraidos", texto.caracteres());
      contenido.put("paginas_sin_texto", texto.paginasSinTexto());
      contenido.put("requiere_revision_ocr", texto.paginasSinTexto() > 0);
      contenido.put("paginas", texto.paginas());
      String json = mapper.writeValueAsString(contenido);
      Long nuevaId =
          tx.execute(status -> repositorio.guardarVersion(tipo, id, url, hash, bytes, texto, json));
      if (nuevaId == null) throw new IllegalStateException("No se pudo guardar el PDF.");
      Map<String, Object> resumen = new LinkedHashMap<>();
      resumen.put("version_id", nuevaId);
      resumen.put("sha256_pdf", hash);
      resumen.put("url_pdf", url);
      resumen.put("paginas", texto.numeroPaginas());
      resumen.put("paginas_sin_texto", texto.paginasSinTexto());
      resumen.put("caracteres", texto.caracteres());
      resumen.put("tamano_bytes", bytes.length);
      return respuesta("NUEVA_VERSION", tipo, id, datos.get("nombre"), resumen);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Descarga interrumpida", e);
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "No se pudo descargar o extraer el PDF del SEA: " + e.getMessage(),
          e);
    }
  }

  private Map<String, Object> respuesta(
      String estado, String tipo, int id, Object nombre, Map<String, Object> v) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("estado", estado);
    r.put("tipo", tipo);
    r.put("id_documento", id);
    r.put("nombre", nombre);
    r.putAll(v);
    r.put(
        "advertencia",
        ((Number) v.get("paginas_sin_texto")).intValue() > 0
            ? "Hay páginas sin texto extraíble; revisar si requieren OCR."
            : null);
    return r;
  }

  private static String sha256(byte[] pdf) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}

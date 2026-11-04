package com.mundusTest.pptest.repository.guias;

import com.mundusTest.pptest.dto.guias.PdfTextoExtraidoDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SeaPdfRepositorio {
  private final JdbcTemplate jdbc;
  private final JsonMapper mapper;

  public SeaPdfRepositorio(JdbcTemplate jdbc, JsonMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> obtenerDocumento(String tipo, int id) {
    List<Map<String, Object>> registros =
        jdbc.query(
            """
            SELECT datos_json::text AS datos, presente_en_indice
            FROM sea_documento WHERE tipo_documento=? AND id_documento=?
            """,
            (rs, n) -> {
              Map<String, Object> resultado = new LinkedHashMap<>();
              resultado.put("datos", mapper.readValue(rs.getString("datos"), Map.class));
              resultado.put("presente_en_indice", rs.getBoolean("presente_en_indice"));
              return resultado;
            },
            tipo,
            id);
    return registros.isEmpty() ? null : registros.get(0);
  }

  public Map<String, Object> versionActual(String tipo, int id) {
    List<Map<String, Object>> versiones =
        jdbc.query(
            """
            SELECT id, sha256_pdf, url_pdf, numero_paginas, paginas_sin_texto,
                   caracteres, bytes_pdf, procesado_en, verificado_en
            FROM sea_pdf_version
            WHERE tipo_documento=? AND id_documento=? AND es_actual=true
            """,
            (rs, n) -> infoVersion(rs),
            tipo,
            id);
    return versiones.isEmpty() ? null : versiones.get(0);
  }

  public Map<String, Object> versionPorHash(String tipo, int id, String hash) {
    List<Map<String, Object>> versiones =
        jdbc.query(
            """
            SELECT id, sha256_pdf, url_pdf, numero_paginas, paginas_sin_texto,
                   caracteres, bytes_pdf, procesado_en, verificado_en
            FROM sea_pdf_version
            WHERE tipo_documento=? AND id_documento=? AND sha256_pdf=?
            """,
            (rs, n) -> infoVersion(rs),
            tipo,
            id,
            hash);
    return versiones.isEmpty() ? null : versiones.get(0);
  }

  private Map<String, Object> infoVersion(java.sql.ResultSet rs) throws java.sql.SQLException {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("version_id", rs.getLong("id"));
    m.put("sha256_pdf", rs.getString("sha256_pdf"));
    m.put("url_pdf", rs.getString("url_pdf"));
    m.put("paginas", rs.getInt("numero_paginas"));
    m.put("paginas_sin_texto", rs.getInt("paginas_sin_texto"));
    m.put("caracteres", rs.getLong("caracteres"));
    m.put("tamano_bytes", rs.getLong("bytes_pdf"));
    m.put("procesado_en", rs.getTimestamp("procesado_en").toInstant().toString());
    m.put("verificado_en", rs.getTimestamp("verificado_en").toInstant().toString());
    return m;
  }

  public void marcarVerificado(long versionId, String url) {
    jdbc.update(
        "UPDATE sea_pdf_version SET verificado_en=NOW(), url_pdf=? WHERE id=?", url, versionId);
  }

  /** Debe ejecutarse dentro de una transacción. */
  public void activarExistente(String tipo, int id, long versionId, String url) {
    jdbc.update(
        """
        UPDATE sea_pdf_version SET es_actual=false
        WHERE tipo_documento=? AND id_documento=? AND es_actual=true
        """,
        tipo,
        id);
    jdbc.update(
        """
        UPDATE sea_pdf_version SET es_actual=true, verificado_en=NOW(), url_pdf=? WHERE id=?
        """,
        url,
        versionId);
  }

  /** Debe ejecutarse dentro de una transacción. */
  public long guardarVersion(
      String tipo,
      int id,
      String url,
      String sha256,
      byte[] pdf,
      PdfTextoExtraidoDTO texto,
      String json) {
    jdbc.update(
        """
        UPDATE sea_pdf_version SET es_actual=false
        WHERE tipo_documento=? AND id_documento=? AND es_actual=true
        """,
        tipo,
        id);
    Long versionId =
        jdbc.queryForObject(
            """
            INSERT INTO sea_pdf_version (
                tipo_documento, id_documento, url_pdf, sha256_pdf, pdf_original,
                bytes_pdf, numero_paginas, paginas_sin_texto, caracteres, datos_json, es_actual
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), true)
            RETURNING id
            """,
            Long.class,
            tipo,
            id,
            url,
            sha256,
            pdf,
            pdf.length,
            texto.numeroPaginas(),
            texto.paginasSinTexto(),
            texto.caracteres(),
            json);
    if (versionId == null) throw new IllegalStateException("No se obtuvo el ID de la versión PDF.");
    return versionId;
  }

  public byte[] pdfActual(String tipo, int id) {
    List<byte[]> pdfs =
        jdbc.query(
            """
            SELECT pdf_original FROM sea_pdf_version
            WHERE tipo_documento=? AND id_documento=? AND es_actual=true
            """,
            (rs, n) -> rs.getBytes(1),
            tipo,
            id);
    return pdfs.isEmpty() ? null : pdfs.get(0);
  }
}

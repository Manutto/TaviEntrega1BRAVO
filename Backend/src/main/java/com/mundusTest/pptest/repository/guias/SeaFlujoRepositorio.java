package com.mundusTest.pptest.repository.guias;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SeaFlujoRepositorio {
  private final JdbcTemplate jdbc;
  private final JsonMapper mapper;

  public SeaFlujoRepositorio(JdbcTemplate jdbc, JsonMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public long crear() {
    return jdbc.queryForObject("INSERT INTO sea_guia_ingesta(estado,etapa) VALUES ('EN_COLA','INICIO') RETURNING id", Long.class);
  }

  public void actualizar(long id, String estado, String etapa, Map<String, Object> resultado, String error) {
    jdbc.update("""
        UPDATE sea_guia_ingesta SET estado=?, etapa=?, resultado=?::jsonb, error=?,
          actualizado_en=NOW(), finalizado_en=CASE WHEN ? IN ('COMPLETADO','COMPLETADO_CON_ERRORES','COMPLETADO_CON_ALERTAS','ERROR','INTERRUMPIDO') THEN NOW() ELSE NULL END
        WHERE id=?
        """, estado, etapa, mapper.writeValueAsString(resultado), error, estado, id);
  }

  public List<Integer> guiasPresentes() {
    return jdbc.queryForList("SELECT id_documento FROM sea_documento WHERE tipo_documento='GUIA' AND presente_en_indice ORDER BY id_documento", Integer.class);
  }

  public Map<String, Object> obtener(long id) {
    var filas = leer("SELECT * FROM sea_guia_ingesta WHERE id=?", id);
    return filas.isEmpty() ? null : filas.get(0);
  }

  public List<Map<String, Object>> recientes() {
    return leer("SELECT * FROM sea_guia_ingesta ORDER BY id DESC LIMIT 10");
  }

  public void interrumpirPendientes() {
    jdbc.update("UPDATE sea_guia_ingesta SET estado='INTERRUMPIDO',error='El backend se reinició durante esta ingesta.',finalizado_en=NOW() WHERE estado IN ('EN_COLA','EN_PROCESO')");
  }

  public boolean versionActual(int id, String hash) {
    return Boolean.TRUE.equals(jdbc.queryForObject("""
        SELECT EXISTS(SELECT 1 FROM sea_pdf_version v JOIN sea_documento d
          ON d.tipo_documento=v.tipo_documento AND d.id_documento=v.id_documento
          WHERE v.tipo_documento='GUIA' AND v.id_documento=? AND v.sha256_pdf=? AND v.es_actual AND d.presente_en_indice)
        """, Boolean.class, id, hash));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> leer(String sql, Object... parametros) {
    return jdbc.query(sql, (rs, n) -> {
      Map<String, Object> fila = new java.util.LinkedHashMap<>();
      fila.put("id", rs.getLong("id"));
      fila.put("estado", rs.getString("estado"));
      fila.put("etapa", rs.getString("etapa"));
      fila.put("resultado", mapper.readValue(rs.getString("resultado"), Map.class));
      fila.put("error", rs.getString("error"));
      fila.put("iniciado_en", rs.getTimestamp("iniciado_en").toInstant().toString());
      fila.put("actualizado_en", rs.getTimestamp("actualizado_en").toInstant().toString());
      var fin = rs.getTimestamp("finalizado_en");
      fila.put("finalizado_en", fin == null ? null : fin.toInstant().toString());
      return fila;
    }, parametros);
  }
}

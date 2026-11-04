package com.mundusTest.pptest.repository.guias;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SeaExportacionRepositorio {
  private final JdbcTemplate jdbc;
  private final JsonMapper mapper;

  public SeaExportacionRepositorio(JdbcTemplate jdbc, JsonMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> documentos(String tipo, int id) {
    return jdbc.query(
        """
        SELECT d.nombre, d.datos_json::text AS catalogo,
               v.id AS version_id, v.sha256_pdf, v.url_pdf,
               v.datos_json::text AS extraccion, v.numero_paginas,
               p.algoritmo, p.estado
        FROM sea_documento d JOIN sea_pdf_version v
          ON v.tipo_documento=d.tipo_documento AND v.id_documento=d.id_documento AND v.es_actual
        LEFT JOIN sea_preparacion_version p ON p.version_id=v.id
        WHERE d.tipo_documento=? AND d.id_documento=?
        """,
        (rs, row) -> {
          Map<String, Object> r = new LinkedHashMap<>();
          r.put("nombre", rs.getString("nombre"));
          r.put("catalogo", mapper.readValue(rs.getString("catalogo"), Map.class));
          r.put("version_id", rs.getLong("version_id"));
          r.put("sha256_pdf", rs.getString("sha256_pdf"));
          r.put("url_pdf", rs.getString("url_pdf"));
          r.put("extraccion", mapper.readValue(rs.getString("extraccion"), Map.class));
          r.put("numero_paginas", rs.getInt("numero_paginas"));
          r.put("algoritmo", rs.getString("algoritmo"));
          r.put("estado", rs.getString("estado"));
          return r;
        },
        tipo,
        id);
  }

  public List<Map<String, Object>> paginas(long version) {
    return jdbc.query(
        """
        SELECT pagina,texto_limpio,sha256_texto_original,alertas::text AS alertas
        FROM sea_pagina_preparada WHERE version_id=? ORDER BY pagina
        """,
        (rs, row) -> {
          Map<String, Object> pagina = new LinkedHashMap<>();
          pagina.put("pagina_pdf", rs.getInt("pagina"));
          pagina.put("texto_limpio", rs.getString("texto_limpio"));
          pagina.put("sha256_texto_original", rs.getString("sha256_texto_original"));
          pagina.put("alertas", mapper.readValue(rs.getString("alertas"), List.class));
          return pagina;
        },
        version);
  }

  public List<Map<String, Object>> fragmentos(long version) {
    return jdbc.queryForList(
        """
        SELECT orden,pagina_inicio,pagina_fin,offset_inicio,offset_fin,texto,sha256_texto,algoritmo
        FROM sea_fragmento WHERE version_id=? ORDER BY orden
        """,
        version);
  }
}

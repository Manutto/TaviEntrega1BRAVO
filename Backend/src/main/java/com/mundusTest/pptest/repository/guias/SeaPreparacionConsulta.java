package com.mundusTest.pptest.repository.guias;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeaPreparacionConsulta {
  private final JdbcTemplate jdbc;

  public SeaPreparacionConsulta(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> guias(int limite) {
    return jdbc.queryForList(
        """
        SELECT d.tipo_documento, d.id_documento, d.nombre,
               v.id AS version_id, v.procesado_en AS pdf_descargado_en,
               p.procesado_en AS preparado_en, p.estado,
               COALESCE(p.total_paginas,v.numero_paginas) AS paginas,
               p.paginas_sin_texto, p.paginas_texto_escaso,
               p.paginas_alertas, p.total_fragmentos AS fragmentos,
               p.caracteres_limpios
        FROM sea_documento d LEFT JOIN sea_pdf_version v ON
            v.tipo_documento=d.tipo_documento AND v.id_documento=d.id_documento AND v.es_actual
        LEFT JOIN sea_preparacion_version p ON p.version_id=v.id
        WHERE d.presente_en_indice=true AND d.tipo_documento='GUIA'
        ORDER BY p.paginas_sin_texto DESC NULLS LAST, d.tipo_documento,d.id_documento LIMIT ?
        """,
        limite);
  }
}

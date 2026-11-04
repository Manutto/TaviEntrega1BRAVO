package com.mundusTest.pptest.repository.guias;

import com.mundusTest.pptest.dto.guias.GuiaPreparacionDTO.Fragmento;
import com.mundusTest.pptest.dto.guias.GuiaPreparacionDTO.Pagina;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class SeaPreparacionRepositorio {
  private final JdbcTemplate jdbc;
  private final JsonMapper mapper;

  public SeaPreparacionRepositorio(JdbcTemplate jdbc, JsonMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> versiones(long versionId) {
    return jdbc.query(
        """
        SELECT v.id, v.tipo_documento, v.id_documento, v.numero_paginas,
               v.datos_json::text AS contenido
        FROM sea_pdf_version v WHERE v.id=?
        """,
        (rs, n) -> {
          Map<String, Object> version = new LinkedHashMap<>();
          version.put("version_id", rs.getLong("id"));
          version.put("tipo", rs.getString("tipo_documento"));
          version.put("id_documento", rs.getInt("id_documento"));
          version.put("numero_paginas", rs.getInt("numero_paginas"));
          version.put("contenido", rs.getString("contenido"));
          return version;
        },
        versionId);
  }

  public String algoritmoAnterior(long versionId) {
    return jdbc.query(
        """
        SELECT algoritmo FROM sea_preparacion_version WHERE version_id=? AND estado <> 'ERROR'
        """,
        rs -> rs.next() ? rs.getString(1) : null,
        versionId);
  }

  @Transactional
  public void guardar(
      long versionId,
      String algoritmo,
      String estado,
      List<Pagina> paginas,
      List<Fragmento> fragmentos,
      int vacias,
      int escasas,
      int alertadas,
      long caracteres) {
    jdbc.update("DELETE FROM sea_fragmento WHERE version_id=?", versionId);
    jdbc.update("DELETE FROM sea_pagina_preparada WHERE version_id=?", versionId);

    jdbc.batchUpdate(
        """
        INSERT INTO sea_pagina_preparada
        (version_id,pagina,texto_limpio,sha256_texto_original,caracteres,alertas)
        VALUES (?,?,?,?,?,?::jsonb)
        """,
        paginas,
        100,
        (ps, pagina) -> {
          ps.setLong(1, versionId);
          ps.setInt(2, pagina.numero());
          ps.setString(3, pagina.texto());
          ps.setString(4, pagina.hash());
          ps.setInt(5, pagina.texto().length());
          ps.setString(6, mapper.writeValueAsString(pagina.alertas()));
        });

    jdbc.batchUpdate(
        """
        INSERT INTO sea_fragmento (version_id,orden,pagina_inicio,pagina_fin,
        offset_inicio,offset_fin,texto,sha256_texto,algoritmo)
        VALUES (?,?,?,?,?,?,?,?,?)
        """,
        fragmentos,
        100,
        (ps, fragmento) -> {
          ps.setLong(1, versionId);
          ps.setInt(2, fragmento.orden());
          ps.setInt(3, fragmento.pagina());
          ps.setInt(4, fragmento.pagina());
          ps.setInt(5, fragmento.inicio());
          ps.setInt(6, fragmento.fin());
          ps.setString(7, fragmento.texto());
          ps.setString(8, fragmento.sha256());
          ps.setString(9, algoritmo);
        });

    jdbc.update(
        """
        INSERT INTO sea_preparacion_version(version_id,algoritmo,estado,total_paginas,
        paginas_sin_texto,paginas_texto_escaso,paginas_alertas,
        caracteres_limpios,total_fragmentos,procesado_en,mensaje)
        VALUES (?,?,?,?,?,?,?,?,?,NOW(),NULL)
        ON CONFLICT (version_id) DO UPDATE SET
        algoritmo=excluded.algoritmo, estado=excluded.estado,
        total_paginas=excluded.total_paginas,
        paginas_sin_texto=excluded.paginas_sin_texto,
        paginas_texto_escaso=excluded.paginas_texto_escaso,
        paginas_alertas=excluded.paginas_alertas,
        caracteres_limpios=excluded.caracteres_limpios,
        total_fragmentos=excluded.total_fragmentos, procesado_en=NOW(), mensaje=NULL
        """,
        versionId,
        algoritmo,
        estado,
        paginas.size(),
        vacias,
        escasas,
        alertadas,
        caracteres,
        fragmentos.size());
  }
}

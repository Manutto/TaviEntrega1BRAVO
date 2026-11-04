package com.mundusTest.pptest.repository.guias;

import com.mundusTest.pptest.entity.SeaDocumento;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SeaRepositorio {
  private final JdbcTemplate jdbc;

  public SeaRepositorio(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long iniciar(String urlPagina) {
    return jdbc.queryForObject(
        """
        INSERT INTO sea_sincronizacion (estado, url_pagina)
        VALUES ('EN_PROCESO', ?) RETURNING id
        """,
        Long.class,
        urlPagina);
  }

  public void terminar(long id, String excelUrl, String shaExcel, Estadisticas e) {
    jdbc.update(
        """
        UPDATE sea_sincronizacion SET estado='OK', finalizada_en=NOW(),
        url_excel=?, sha256_excel=?, total=?, nuevos=?, actualizados=?, sin_cambios=?, ausentes=?
        WHERE id=?
        """,
        excelUrl,
        shaExcel,
        e.total(),
        e.nuevos(),
        e.actualizados(),
        e.sinCambios(),
        e.ausentes(),
        id);
  }

  public void error(long id, String mensaje) {
    jdbc.update(
        """
        UPDATE sea_sincronizacion SET estado='ERROR', finalizada_en=NOW(), mensaje=? WHERE id=?
        """,
        mensaje,
        id);
  }

  public Estadisticas guardar(
      List<SeaDocumento> documentos, List<String> json, List<String> hashes) {
    Map<String, String> existentes = new HashMap<>();
    jdbc.query(
        "SELECT tipo_documento, id_documento, sha256_registro FROM sea_documento",
        rs -> {
          existentes.put(rs.getString(1) + ":" + rs.getInt(2), rs.getString(3));
        });
    jdbc.update("UPDATE sea_documento SET presente_en_indice=false");
    int nuevos = 0, actualizados = 0, sinCambios = 0;
    for (int i = 0; i < documentos.size(); i++) {
      SeaDocumento d = documentos.get(i);
      String clave = d.tipoDocumento() + ":" + d.idDocumento();
      String previo = existentes.get(clave);
      if (previo == null) nuevos++;
      else if (!previo.equals(hashes.get(i))) actualizados++;
      else sinCambios++;

      jdbc.update(
          """
          INSERT INTO sea_documento
          (tipo_documento, id_documento, nombre, vigencia, anio_publicacion,
           datos_json, sha256_registro, presente_en_indice)
          VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, true)
          ON CONFLICT (tipo_documento, id_documento) DO UPDATE SET
              nombre = EXCLUDED.nombre,
              vigencia = EXCLUDED.vigencia,
              anio_publicacion = EXCLUDED.anio_publicacion,
              datos_json = EXCLUDED.datos_json,
              actualizado_en = CASE WHEN sea_documento.sha256_registro <> EXCLUDED.sha256_registro
                                   THEN NOW() ELSE sea_documento.actualizado_en END,
              sha256_registro = EXCLUDED.sha256_registro,
              presente_en_indice = true,
              revisado_en = NOW()
          """,
          d.tipoDocumento(),
          d.idDocumento(),
          d.nombre(),
          d.vigencia(),
          d.anioPublicacion(),
          json.get(i),
          hashes.get(i));
    }
    Integer ausentes =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sea_documento WHERE NOT presente_en_indice", Integer.class);
    return new Estadisticas(
        documentos.size(), nuevos, actualizados, sinCambios, ausentes == null ? 0 : ausentes);
  }

  public record Estadisticas(
      int total, int nuevos, int actualizados, int sinCambios, int ausentes) {}
}

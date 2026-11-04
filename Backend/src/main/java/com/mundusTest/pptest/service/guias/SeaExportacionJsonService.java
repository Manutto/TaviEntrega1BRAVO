package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.repository.guias.SeaExportacionRepositorio;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeaExportacionJsonService {
  private final SeaExportacionRepositorio repositorio;

  public SeaExportacionJsonService(SeaExportacionRepositorio repositorio) {
    this.repositorio = repositorio;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  @SuppressWarnings("unchecked")
  public Map<String, Object> exportar(String tipo, int id) {
    var encontrados = repositorio.documentos(tipo, id);
    if (encontrados.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay PDF para exportar.");
    }
    var dato = encontrados.get(0);
    if (dato.get("algoritmo") == null) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Prepara esta versión antes de exportarla.");
    }
    long version = ((Number) dato.get("version_id")).longValue();
    Map<String, Object> extraccion = (Map<String, Object>) dato.get("extraccion");
    Map<Integer, String> originales = new HashMap<>();
    Object raw = extraccion.get("paginas");
    if (!(raw instanceof List<?> paginasOriginales)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "La extracción original no tiene páginas.");
    }
    for (Object item : paginasOriginales) {
      Map<String, Object> pagina = (Map<String, Object>) item;
      originales.put(((Number) pagina.get("pagina")).intValue(), (String) pagina.get("texto"));
    }
    var paginas = repositorio.paginas(version);
    for (var pagina : paginas) {
      int numero = ((Number) pagina.get("pagina_pdf")).intValue();
      String original = originales.get(numero);
      if (original == null
          || !SeaTextoPreparador.sha256(original).equals(pagina.get("sha256_texto_original"))) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "La página preparada no coincide con su texto original.");
      }
      pagina.put("texto_original", original);
    }
    if (paginas.size() != ((Number) dato.get("numero_paginas")).intValue()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "La preparación no cubre todas las páginas.");
    }
    var fragmentos = repositorio.fragmentos(version);
    for (var fragmento : fragmentos) {
      fragmento.put(
          "id_fragmento",
          tipo
              + ":"
              + id
              + ":"
              + dato.get("sha256_pdf")
              + ":"
              + fragmento.remove("algoritmo")
              + ":"
              + fragmento.get("orden"));
    }
    Map<Integer, Object> alertas = new HashMap<>();
    Map<Integer, String> textos = new HashMap<>();
    paginas.forEach(p -> alertas.put(((Number) p.get("pagina_pdf")).intValue(), p.get("alertas")));
    paginas.forEach(
        p -> textos.put(((Number) p.get("pagina_pdf")).intValue(), (String) p.get("texto_limpio")));
    fragmentos.forEach(
        f -> {
          int inicioPagina = ((Number) f.get("pagina_inicio")).intValue();
          int finPagina = ((Number) f.get("pagina_fin")).intValue();
          int desde = ((Number) f.get("offset_inicio")).intValue();
          int hasta = ((Number) f.get("offset_fin")).intValue();
          String pagina = textos.get(inicioPagina);
          String texto = (String) f.get("texto");
          if (inicioPagina != finPagina
              || pagina == null
              || desde < 0
              || hasta <= desde
              || hasta > pagina.length()
              || !pagina.substring(desde, hasta).equals(texto)
              || !SeaTextoPreparador.sha256(texto).equals(f.get("sha256_texto"))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Fragmento inconsistente con su página de origen.");
          }
          f.put("alertas_pagina", alertas.get(inicioPagina));
        });

    Map<String, Object> json = new LinkedHashMap<>();
    json.put("schema_version", "mundus-rag-json-v1");
    json.put(
        "documento",
        Map.of(
            "tipo",
            tipo,
            "id",
            id,
            "nombre",
            dato.get("nombre"),
            "version_id",
            version,
            "sha256_pdf",
            dato.get("sha256_pdf"),
            "url_pdf",
            dato.get("url_pdf")));
    json.put("metadatos_catalogo", dato.get("catalogo"));
    json.put(
        "metadatos_extraccion",
        extraccion.getOrDefault(
            "metadatos_extraccion", Map.of("estado", "NO_REGISTRADO_EN_LA_VERSION_ORIGINAL")));
    json.put(
        "preparacion",
        Map.of(
            "algoritmo",
            dato.get("algoritmo"),
            "estado_heuristico",
            dato.get("estado"),
            "offset_unidad",
            "UTF-16",
            "offset_sobre",
            "texto_limpio",
            "offset_fin_exclusivo",
            true,
            "hash_texto_codificacion",
            "UTF-8"));
    json.put(
        "calidad",
        Map.of(
            "validado_para_llm",
            false,
            "ocr_aplicado",
            false,
            "estructura_visual_validada",
            false,
            "paginas_con_alertas",
            paginas.stream().filter(p -> !((List<?>) p.get("alertas")).isEmpty()).count()));
    json.put(
        "advertencias",
        List.of(
            "La extracción es texto plano: las columnas pueden mezclarse y las tablas/fórmulas"
                + " perder estructura.",
            "SIN_TEXTO no implica una página vacía: puede requerir OCR o lectura visual.",
            "LISTO solo significa ausencia de alertas textuales heurísticas, no validación humana.",
            "Usar las páginas y hashes para citar y contrastar con el PDF original antes de"
                + " incorporar datos a una LLM."));
    json.put("paginas", paginas);
    json.put("fragmentos", fragmentos);
    return json;
  }
}

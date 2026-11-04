package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.dto.guias.GuiaPreparacionDTO.Fragmento;
import com.mundusTest.pptest.dto.guias.GuiaPreparacionDTO.Pagina;
import com.mundusTest.pptest.repository.guias.SeaPreparacionRepositorio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SeaPreparacionService {
  private final SeaPreparacionRepositorio repositorio;
  private final JsonMapper mapper;

  public SeaPreparacionService(SeaPreparacionRepositorio repositorio, JsonMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  @SuppressWarnings("unchecked")
  public synchronized Map<String, Object> prepararVersion(long versionId, boolean forzar) {
    List<Map<String, Object>> versiones = repositorio.versiones(versionId);
    if (versiones.isEmpty())
      throw new IllegalArgumentException("No existe versión PDF: " + versionId);

    String algoritmoAnterior = repositorio.algoritmoAnterior(versionId);
    if (!forzar && SeaTextoPreparador.ALGORITMO.equals(algoritmoAnterior)) {
      return Map.of("estado", "YA_PREPARADA", "version_id", versionId);
    }

    Map<String, Object> v = versiones.get(0);
    Map<String, Object> json = mapper.readValue((String) v.get("contenido"), Map.class);
    Object contenido = json.get("paginas");
    if (!(contenido instanceof List<?> paginas)) {
      throw new IllegalStateException(
          "La versión " + versionId + " no incluye un arreglo 'paginas'.");
    }
    if (paginas.size() != ((Number) v.get("numero_paginas")).intValue()) {
      throw new IllegalStateException("El número de páginas no coincide con el PDF original.");
    }

    List<Pagina> salidaPaginas = new ArrayList<>();
    List<Fragmento> salidaFragmentos = new ArrayList<>();
    int vacias = 0, escasas = 0, alertadas = 0, ordinal = 0;
    long caracteres = 0;
    for (int i = 0; i < paginas.size(); i++) {
      if (!(paginas.get(i) instanceof Map<?, ?> pagina)) {
        throw new IllegalStateException("Estructura incorrecta de página " + (i + 1));
      }
      int numero = numero(pagina.get("pagina"));
      if (numero != i + 1) throw new IllegalStateException("Página fuera de orden: " + numero);
      Object campoTexto = pagina.get("texto");
      String bruto = campoTexto == null ? "" : String.valueOf(campoTexto);
      String limpio = SeaTextoPreparador.limpiar(bruto);
      List<String> alertas = SeaTextoPreparador.alertas(limpio);
      if (alertas.contains("SIN_TEXTO")) vacias++;
      if (alertas.contains("TEXTO_ESCASO")) escasas++;
      if (!alertas.isEmpty()) alertadas++;
      caracteres += limpio.length();
      salidaPaginas.add(new Pagina(numero, limpio, SeaTextoPreparador.sha256(bruto), alertas));
      for (SeaTextoPreparador.Fragmento fragmento : SeaTextoPreparador.fragmentar(numero, limpio)) {
        salidaFragmentos.add(
            new Fragmento(
                ++ordinal,
                fragmento.pagina(),
                fragmento.inicio(),
                fragmento.fin(),
                fragmento.texto(),
                fragmento.sha256()));
      }
    }
    final String estado =
        vacias == paginas.size() ? "SIN_TEXTO" : (alertadas > 0 ? "REVISAR" : "LISTO");
    repositorio.guardar(
        versionId,
        SeaTextoPreparador.ALGORITMO,
        estado,
        salidaPaginas,
        salidaFragmentos,
        vacias,
        escasas,
        alertadas,
        caracteres);

    Map<String, Object> respuesta = new LinkedHashMap<>();
    respuesta.put("estado", estado);
    respuesta.put("version_id", versionId);
    respuesta.put("tipo_documento", v.get("tipo"));
    respuesta.put("id_documento", v.get("id_documento"));
    respuesta.put("paginas", salidaPaginas.size());
    respuesta.put("paginas_sin_texto", vacias);
    respuesta.put("paginas_texto_escaso", escasas);
    respuesta.put("paginas_con_alertas", alertadas);
    respuesta.put("caracteres_limpios", caracteres);
    respuesta.put("fragmentos", salidaFragmentos.size());
    respuesta.put("algoritmo", SeaTextoPreparador.ALGORITMO);
    return respuesta;
  }

  private static int numero(Object n) {
    if (!(n instanceof Number valor)) throw new IllegalArgumentException("Falta número de página.");
    return valor.intValue();
  }
}

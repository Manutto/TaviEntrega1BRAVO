package com.mundusTest.pptest.service.consulta;

import java.util.List;
import java.util.Map;

public final class CatalogoConsulta {
  public record Region(String codigo, String nombre) {}

  public static final List<Region> REGIONES = List.of(
      new Region("XV", "Arica y Parinacota"),
      new Region("I", "Tarapacá"),
      new Region("II", "Antofagasta"),
      new Region("III", "Atacama"),
      new Region("IV", "Coquimbo"),
      new Region("V", "Valparaíso"),
      new Region("RM", "Metropolitana de Santiago"),
      new Region("VI", "O’Higgins"),
      new Region("VII", "Maule"),
      new Region("XVI", "Ñuble"),
      new Region("VIII", "Biobío"),
      new Region("IX", "La Araucanía"),
      new Region("XIV", "Los Ríos"),
      new Region("X", "Los Lagos"),
      new Region("XI", "Aysén"),
      new Region("XII", "Magallanes y de la Antártica Chilena"),
      new Region("Interregional", "Interregional"));

  private static final List<Map<String, String>> TIPOS = List.of(
      Map.of("codigo", "INMOBILIARIO", "nombre", "Proyecto inmobiliario"),
      Map.of("codigo", "ARIDOS", "nombre", "Extracción de áridos"),
      Map.of("codigo", "INDUSTRIAL", "nombre", "Instalación industrial"),
      Map.of("codigo", "OBRAS", "nombre", "Obras de construcción"),
      Map.of("codigo", "RESIDUOS", "nombre", "Manejo de residuos"),
      Map.of("codigo", "OTRO", "nombre", "Otro proyecto"));

  private CatalogoConsulta() {}

  public static Map<String, Object> catalogo() {
    return Map.of("regiones", REGIONES, "tiposProyecto", TIPOS);
  }
}

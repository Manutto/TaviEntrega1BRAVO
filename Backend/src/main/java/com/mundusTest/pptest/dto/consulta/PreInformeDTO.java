package com.mundusTest.pptest.dto.consulta;

import java.util.List;

public record PreInformeDTO(
    String resumen, List<Permiso> permisosPosibles, List<Alerta> alertas, String nivelRiesgo,
    List<String> sinEvidencia, String advertencia, List<FragmentoConsultaDTO> fuentes,
    Metadatos metadatos, String consultaId, List<FragmentoConsultaDTO> fragmentosConsultados) {

  public record Permiso(String pas, String motivo, List<Integer> citas) {}
  public record Alerta(String nivel, String descripcion, List<Integer> citas) {}
  public record Metadatos(String modelo, boolean usoRespaldo, long tokensEntrada,
      long tokensSalida, long latenciaMs) {}
}

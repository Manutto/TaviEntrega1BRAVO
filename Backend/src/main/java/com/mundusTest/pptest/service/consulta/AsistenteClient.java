package com.mundusTest.pptest.service.consulta;

import com.mundusTest.pptest.dto.consulta.*;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AsistenteClient {
  private final RestClient cliente;

  public AsistenteClient(@Qualifier("asistenteRestClient") RestClient cliente) {
    this.cliente = cliente;
  }

  public PreInformeDTO generar(ConsultaProyectoDTO proyecto, List<FragmentoConsultaDTO> fragmentos) {
    try {
      var informe = cliente.post().uri("/generar")
          .body(Map.of("proyecto", proyecto, "fragmentos", fragmentos))
          .retrieve().body(PreInformeDTO.class);
      if (informe == null) throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "El asistente devolvió una respuesta vacía.");
      return informe;
    } catch (RestClientResponseException error) {
      if (error.getStatusCode().value() == 503)
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "El asistente no puede ejecutar Qwen. Comprueba que Ollama y el modelo estén disponibles.", error);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Qwen no entregó un preinforme válido. Puedes ajustar la descripción y volver a consultar.", error);
    } catch (RestClientException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "No se pudo completar la consulta al asistente local. Comprueba que esté iniciado.", error);
    }
  }
}

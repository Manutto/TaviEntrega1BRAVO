package com.mundusTest.pptest.service.consulta;

import com.mundusTest.pptest.dto.consulta.*;
import com.mundusTest.pptest.service.guias.SeaVectorService;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsultaService {
  private static final Logger LOG = LoggerFactory.getLogger(ConsultaService.class);
  private static final Set<String> NIVELES = Set.of("ALTO", "MEDIO", "BAJO");
  private final SeaVectorService vectores;
  private final AsistenteClient asistente;

  public ConsultaService(SeaVectorService vectores, AsistenteClient asistente) {
    this.vectores = vectores;
    this.asistente = asistente;
  }

  public Map<String, Object> catalogo() {
    return CatalogoConsulta.catalogo();
  }

  public PreInformeDTO consultar(ConsultaProyectoDTO entrada) {
    var proyecto = validar(entrada);
    String id = UUID.randomUUID().toString();
    long inicio = System.nanoTime();
    LOG.info("consulta={} estado=INICIO", id);
    String busqueda = proyecto.tipoProyecto() + ". " + proyecto.descripcion();
    if (busqueda.codePointCount(0, busqueda.length()) > 1000)
      busqueda = busqueda.substring(0, busqueda.offsetByCodePoints(0, 1000));
    var respuesta = vectores.buscar(busqueda, 5);
    var fragmentos = adaptar(respuesta);
    if (fragmentos.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
        "No se encontraron fragmentos de guías para fundamentar la respuesta. Revisa la biblioteca y la descripción.");
    var informe = completar(asistente.generar(proyecto, fragmentos), fragmentos, id);
    LOG.info("consulta={} estado=OK modelo={} tokensEntrada={} tokensSalida={} totalMs={}",
        id, informe.metadatos().modelo(), informe.metadatos().tokensEntrada(),
        informe.metadatos().tokensSalida(), (System.nanoTime() - inicio) / 1_000_000);
    return informe;
  }

  private ConsultaProyectoDTO validar(ConsultaProyectoDTO entrada) {
    if (entrada == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Faltan datos del proyecto.");
    String tipo = texto(entrada.tipoProyecto(), "Tipo de proyecto", 1, 120);
    String region = texto(entrada.region(), "Región", 1, 100);
    String comuna = texto(entrada.comuna(), "Comuna", 1, 120);
    String descripcion = texto(entrada.descripcion(), "Descripción", 50, 2000);
    if (CatalogoConsulta.REGIONES.stream().noneMatch(r -> r.nombre().equals(region)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una región del catálogo.");
    return new ConsultaProyectoDTO(tipo, region, comuna, descripcion);
  }

  private String texto(String valor, String campo, int minimo, int maximo) {
    String limpio = valor == null ? "" : valor.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "");
    int largo = limpio.codePointCount(0, limpio.length());
    if (largo < minimo || largo > maximo)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          campo + ": usa entre " + minimo + " y " + maximo + " caracteres.");
    return limpio;
  }

  private List<FragmentoConsultaDTO> adaptar(Map<String, Object> respuesta) {
    if (!(respuesta.get("resultados") instanceof List<?> resultados)) throw invalida();
    List<FragmentoConsultaDTO> fragmentos = new ArrayList<>();
    Set<String> vistos = new HashSet<>();
    for (Object item : resultados) {
      if (!(item instanceof Map<?, ?> fila) || !(fila.get("documento") instanceof Map<?, ?> doc)
          || !"GUIA".equals(doc.get("tipo")) || !(doc.get("id") instanceof Number documentoId)
          || documentoId.intValue() < 1 || !(fila.get("pagina_inicio") instanceof Number pagina)
          || pagina.intValue() < 1 || !Objects.equals(fila.get("pagina_inicio"), fila.get("pagina_fin"))
          || !(fila.get("id_fragmento") instanceof String origen) || origen.isBlank()
          || !(fila.get("texto") instanceof String texto) || texto.isBlank()
          || !(doc.get("nombre") instanceof String titulo) || titulo.isBlank()
          || !(doc.get("url_pdf") instanceof String url)
          || !(doc.get("sha256_pdf") instanceof String sha)) throw invalida();
      if (!vistos.add(origen)) continue;
      Double similitud = fila.get("puntuacion") instanceof Number n ? n.doubleValue() : null;
      if (similitud != null && !Double.isFinite(similitud)) throw invalida();
      fragmentos.add(new FragmentoConsultaDTO(fragmentos.size() + 1, "GUIA", documentoId.intValue(),
          titulo, pagina.intValue(), texto, similitud, origen, url, sha));
    }
    return List.copyOf(fragmentos);
  }

  /** Las fuentes finales proceden de la búsqueda, no de nombres o páginas generados por la LLM. */
  private PreInformeDTO completar(PreInformeDTO informe, List<FragmentoConsultaDTO> fragmentos, String id) {
    if (informe.resumen() == null || informe.resumen().isBlank() || informe.permisosPosibles() == null
        || informe.alertas() == null || informe.nivelRiesgo() == null || !NIVELES.contains(informe.nivelRiesgo())
        || informe.sinEvidencia() == null || informe.advertencia() == null || informe.metadatos() == null
        || informe.metadatos().modelo() == null || !informe.metadatos().modelo().toLowerCase(Locale.ROOT).startsWith("qwen")
        || informe.metadatos().tokensEntrada() < 0 || informe.metadatos().tokensSalida() < 0) throw invalida();
    Set<Integer> citados = new TreeSet<>();
    var citas = Stream.concat(
        informe.permisosPosibles().stream().map(p -> p == null ? null : p.citas()),
        informe.alertas().stream().map(a -> a == null ? null : a.citas())).toList();
    for (var lista : citas) {
      if (lista == null || lista.isEmpty()) throw invalida();
      for (Integer cita : lista) {
        if (cita == null || cita < 1 || cita > fragmentos.size()) throw invalida();
        citados.add(cita);
      }
    }
    var fuentes = fragmentos.stream().filter(f -> citados.contains(f.fragmentoId())).toList();
    return new PreInformeDTO(informe.resumen(), informe.permisosPosibles(), informe.alertas(),
        informe.nivelRiesgo(), informe.sinEvidencia(), informe.advertencia(), fuentes,
        informe.metadatos(), id, fragmentos);
  }

  private ResponseStatusException invalida() {
    return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
        "La respuesta documental no tiene un formato o referencias verificables.");
  }
}

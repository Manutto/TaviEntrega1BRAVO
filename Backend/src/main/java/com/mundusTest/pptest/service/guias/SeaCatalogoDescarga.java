package com.mundusTest.pptest.service.guias;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SeaCatalogoDescarga {
  private static final int MAX_HTML = 2 * 1024 * 1024;
  private static final int MAX_EXCEL = 15 * 1024 * 1024;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(12))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final String urlPagina;
  private final String urlExcelDirecto;

  public SeaCatalogoDescarga(
      @Value("${sea.guias.url-pagina}") String urlPagina,
      @Value("${sea.guias.url-excel:}") String urlExcelDirecto) {
    this.urlPagina = urlPagina;
    this.urlExcelDirecto = urlExcelDirecto;
  }

  public String urlPagina() {
    return urlPagina;
  }

  public Descarga descargarIndice() throws IOException, InterruptedException {
    String excel;
    if (!urlExcelDirecto.isBlank()) {
      excel = urlExcelDirecto;
    } else {
      byte[] html = obtener(urlPagina, MAX_HTML);
      Document documento = Jsoup.parse(new String(html, StandardCharsets.UTF_8), urlPagina);
      List<String> candidatas = new ArrayList<>();
      for (Element enlace : documento.select("a[href]")) {
        String href = enlace.absUrl("href");
        if (!href.isBlank() && terminaExcel(href)) {
          String etiqueta = enlace.text().toLowerCase(Locale.ROOT);
          if (etiqueta.contains("índice")
              || etiqueta.contains("indice")
              || etiqueta.contains("documentos observables")) {
            candidatas.add(0, href);
          } else {
            candidatas.add(href);
          }
        }
      }
      if (candidatas.isEmpty()) {
        throw new IOException(
            "No se encontró un vínculo XLSX en la página del SEA; revisa su estructura.");
      }
      excel = candidatas.get(0);
    }
    return new Descarga(excel, obtener(excel, MAX_EXCEL));
  }

  private boolean terminaExcel(String url) {
    try {
      URI uri = URI.create(url);
      String ruta = uri.getPath();
      if (ruta == null || ruta.isBlank()) {
        return false;
      }
      return ruta.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private void validarUrl(String url) throws IOException {
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || host == null
          || !(host.equalsIgnoreCase("sea.gob.cl") || host.equalsIgnoreCase("www.sea.gob.cl"))
          || uri.getUserInfo() != null
          || (uri.getPort() != -1 && uri.getPort() != 443)) {
        throw new IOException("Solo se permiten URLs HTTPS oficiales del SEA: " + url);
      }
    } catch (IllegalArgumentException e) {
      throw new IOException("URL del SEA inválida: " + url, e);
    }
  }

  private byte[] obtener(String urlInicial, int maxBytes) throws IOException, InterruptedException {
    String url = urlInicial;
    for (int intento = 0; intento < 5; intento++) {
      validarUrl(url);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(45))
              .header("User-Agent", "MundusSEA-Ingesta/1.0 (+https://www.sea.gob.cl)")
              .GET()
              .build();
      HttpResponse<java.io.InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() == 301
            || response.statusCode() == 302
            || response.statusCode() == 303
            || response.statusCode() == 307
            || response.statusCode() == 308) {
          String location =
              response
                  .headers()
                  .firstValue("Location")
                  .orElseThrow(() -> new IOException("Redirección sin cabecera Location"));
          url = URI.create(url).resolve(location).toString();
          continue;
        }
        if (response.statusCode() != 200)
          throw new IOException(
              "El SEA respondió HTTP " + response.statusCode() + " al consultar " + url);
        byte[] resultado = body.readNBytes(maxBytes + 1);
        if (resultado.length > maxBytes)
          throw new IOException(
              "Descarga del SEA demasiado grande; máximo " + maxBytes + " bytes.");
        return resultado;
      }
    }
    throw new IOException("Demasiadas redirecciones del SEA.");
  }

  public record Descarga(String urlExcel, byte[] bytes) {}
}

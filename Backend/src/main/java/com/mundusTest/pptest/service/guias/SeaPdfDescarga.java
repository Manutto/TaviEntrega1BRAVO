package com.mundusTest.pptest.service.guias;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SeaPdfDescarga {
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(15))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final int maxBytes;

  public SeaPdfDescarga(@Value("${sea.pdf.max-bytes:31457280}") int maxBytes) {
    if (maxBytes < 1024 || maxBytes > 104857600) {
      throw new IllegalArgumentException("sea.pdf.max-bytes debe estar entre 1024 y 104857600.");
    }
    this.maxBytes = maxBytes;
  }

  static URI validarUrl(String url) throws IOException {
    try {
      URI uri = URI.create(url.strip());
      // Los hipervinculos del Excel incluyen a veces #page=N para el visor web.
      // El fragmento no es parte del recurso PDF y no debe enviarse en la petición.
      String ascii = uri.toASCIIString();
      int inicioFragmento = ascii.indexOf('#');
      if (inicioFragmento >= 0) {
        uri = URI.create(ascii.substring(0, inicioFragmento));
      }
      String host = uri.getHost();
      String esquema = uri.getScheme();
      boolean hostOficial =
          host != null
              && (host.equalsIgnoreCase("sea.gob.cl") || host.equalsIgnoreCase("www.sea.gob.cl"));
      boolean https = "https".equalsIgnoreCase(esquema);
      boolean http = "http".equalsIgnoreCase(esquema);

      if (!hostOficial
          || (!http && !https)
          || uri.getUserInfo() != null
          || (http && uri.getPort() != -1)
          || (https && uri.getPort() != -1 && uri.getPort() != 443)) {
        throw new IOException("El documento debe pertenecer al dominio oficial del SEA: " + url);
      }

      if (http) {
        String resto = uri.toASCIIString().substring(esquema.length() + 1);
        return URI.create("https:" + resto);
      }
      return uri;
    } catch (IllegalArgumentException e) {
      throw new IOException("URL del PDF invalida: " + url, e);
    }
  }

  public byte[] descargarPdf(String urlInicial) throws IOException, InterruptedException {
    String url = urlInicial;
    for (int n = 0; n < 5; n++) {
      URI uri = validarUrl(url);
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(90))
              .header("Accept", "application/pdf")
              .header("User-Agent", "MundusSEA-Ingesta/1.0 (+https://www.sea.gob.cl)")
              .GET()
              .build();
      HttpResponse<InputStream> response =
          http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream stream = response.body()) {
        int code = response.statusCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          String destino =
              response
                  .headers()
                  .firstValue("Location")
                  .orElseThrow(() -> new IOException("Redirección sin Location: " + uri));
          url = uri.resolve(destino).toString();
          continue;
        }
        if (code != 200) {
          throw new IOException("El SEA respondió HTTP " + code + " al descargar: " + uri);
        }
        // No confiar solo en Content-Length: limitar también lo leído realmente.
        byte[] pdf = stream.readNBytes(maxBytes + 1);
        if (pdf.length > maxBytes) {
          throw new IOException("El PDF excede el máximo de " + maxBytes + " bytes.");
        }
        if (!tieneCabeceraPdf(pdf)) {
          throw new IOException("El recurso descargado no parece ser un PDF (%PDF-).");
        }
        return pdf;
      }
    }
    throw new IOException("El servidor SEA redirigió demasiadas veces la descarga del PDF.");
  }

  static boolean tieneCabeceraPdf(byte[] bytes) {
    // La cabecera PDF suele comenzar en el byte 0, pero el estándar permite un prefijo breve.
    int max = Math.min(1024, bytes.length - 4);
    for (int i = 0; i < max; i++) {
      if (bytes[i] == '%'
          && bytes[i + 1] == 'P'
          && bytes[i + 2] == 'D'
          && bytes[i + 3] == 'F'
          && bytes[i + 4] == '-') return true;
    }
    return false;
  }
}

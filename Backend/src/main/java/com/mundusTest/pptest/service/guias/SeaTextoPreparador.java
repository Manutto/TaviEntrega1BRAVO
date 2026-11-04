package com.mundusTest.pptest.service.guias;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class SeaTextoPreparador {
  public static final String ALGORITMO = "sea-texto-nfc-v1-chunk1600-overlap180";
  public static final int MAX_FRAGMENTO = 1600;
  public static final int SOLAPAMIENTO = 180;
  public static final int UMBRAL_TEXTO_ESCASO = 60;

  private SeaTextoPreparador() {}

  public static String limpiar(String original) {
    if (original == null || original.isEmpty()) return "";
    String texto =
        Normalizer.normalize(original, Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .replace("\u00ad", "")
            .replace("\ufeff", "")
            .replace("\u200b", "");
    texto =
        texto
            .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
            .replaceAll("(?m)[ \\t]+$", "")
            .replaceAll("\\n{4,}", "\n\n\n");
    return texto.strip();
  }

  public static List<String> alertas(String texto) {
    List<String> resultado = new ArrayList<>();
    if (texto.isBlank()) resultado.add("SIN_TEXTO");
    else if (texto.codePointCount(0, texto.length()) < UMBRAL_TEXTO_ESCASO)
      resultado.add("TEXTO_ESCASO");
    if (texto.indexOf('\ufffd') >= 0) resultado.add("CARACTER_REEMPLAZO");
    int casos = contar(texto, "Ã") + contar(texto, "Â") + contar(texto, "â€");
    if (casos >= 3) resultado.add("POSIBLE_CODIFICACION");
    return resultado;
  }

  private static int contar(String texto, String aguja) {
    int cantidad = 0, desde = 0;
    while ((desde = texto.indexOf(aguja, desde)) >= 0) {
      cantidad++;
      desde += aguja.length();
    }
    return cantidad;
  }

  // Las posiciones usan UTF-16 de Java; fin es exclusivo.
  public record Fragmento(int pagina, int inicio, int fin, String texto, String sha256) {}

  public static List<Fragmento> fragmentar(int pagina, String texto) {
    List<Fragmento> resultado = new ArrayList<>();
    if (texto == null || texto.isBlank()) return resultado;
    int n = texto.length();
    int inicio = 0;
    while (inicio < n) {
      while (inicio < n && Character.isWhitespace(texto.charAt(inicio))) inicio++;
      if (inicio >= n) break;
      int fin = Math.min(inicio + MAX_FRAGMENTO, n);
      if (fin < n) fin = buscarCorte(texto, inicio, fin);
      if (fin <= inicio) fin = Math.min(inicio + MAX_FRAGMENTO, n);
      if (fin < n
          && Character.isHighSurrogate(texto.charAt(fin - 1))
          && Character.isLowSurrogate(texto.charAt(fin))) fin--;
      int derecha = fin;
      while (derecha > inicio && Character.isWhitespace(texto.charAt(derecha - 1))) derecha--;
      String contenido = texto.substring(inicio, derecha);
      if (!contenido.isEmpty())
        resultado.add(new Fragmento(pagina, inicio, derecha, contenido, sha256(contenido)));
      if (fin >= n) break;
      int siguiente = Math.max(inicio + 1, fin - SOLAPAMIENTO);
      if (siguiente > 0 && siguiente < n && !Character.isWhitespace(texto.charAt(siguiente - 1))) {
        int espacio = siguiente;
        while (espacio < Math.min(fin, siguiente + 50)
            && !Character.isWhitespace(texto.charAt(espacio))) espacio++;
        if (espacio < fin) siguiente = espacio + 1;
      }
      inicio = siguiente;
    }
    return resultado;
  }

  private static int buscarCorte(String texto, int inicio, int limite) {
    int minimo = inicio + (int) (MAX_FRAGMENTO * 0.60);
    int p = texto.lastIndexOf("\n\n", limite);
    if (p >= minimo) return p + 2;
    p = texto.lastIndexOf('\n', limite);
    if (p >= minimo) return p + 1;
    p = texto.lastIndexOf(". ", limite);
    if (p >= minimo) return p + 2;
    p = texto.lastIndexOf(' ', limite);
    return p >= inicio + 1 ? p + 1 : limite;
  }

  public static String sha256(String texto) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).toLowerCase(Locale.ROOT);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible", e);
    }
  }
}

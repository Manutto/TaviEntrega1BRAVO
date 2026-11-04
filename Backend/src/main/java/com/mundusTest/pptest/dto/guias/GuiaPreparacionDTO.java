package com.mundusTest.pptest.dto.guias;

import java.util.List;

public final class GuiaPreparacionDTO {
  private GuiaPreparacionDTO() {}

  public record Pagina(int numero, String texto, String hash, List<String> alertas) {}

  public record Fragmento(
      int orden, int pagina, int inicio, int fin, String texto, String sha256) {}
}

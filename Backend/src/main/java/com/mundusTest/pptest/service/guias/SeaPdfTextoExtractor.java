package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.dto.guias.PdfTextoExtraidoDTO;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SeaPdfTextoExtractor {
  private final int maxPaginas;

  public SeaPdfTextoExtractor(@Value("${sea.pdf.max-paginas:600}") int maxPaginas) {
    this.maxPaginas = maxPaginas;
  }

  public static Map<String, Object> metadatos() {
    String version = PDDocument.class.getPackage().getImplementationVersion();
    return Map.of(
        "motor",
        "Apache PDFBox",
        "version",
        version == null ? "no_disponible" : version,
        "orden",
        "posicion_izquierda_derecha_arriba_abajo",
        "ocr_aplicado",
        false,
        "estructura_visual_validada",
        false);
  }

  public PdfTextoExtraidoDTO extraer(byte[] pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
        throw new IOException("El PDF no permite extraer texto.");
      }
      int numeroPaginas = doc.getNumberOfPages();
      if (numeroPaginas <= 0 || numeroPaginas > maxPaginas) {
        throw new IOException(
            "Cantidad de páginas fuera del límite (" + numeroPaginas + "/" + maxPaginas + ").");
      }

      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      List<Map<String, Object>> paginas = new ArrayList<>();
      long caracteres = 0;
      int paginasSinTexto = 0;
      for (int i = 1; i <= numeroPaginas; i++) {
        stripper.setStartPage(i);
        stripper.setEndPage(i);
        String texto = stripper.getText(doc);
        boolean tieneTexto = !texto.isBlank();
        if (!tieneTexto) paginasSinTexto++;
        caracteres += texto.length();
        Map<String, Object> pagina = new LinkedHashMap<>();
        pagina.put("pagina", i);
        pagina.put("texto", texto);
        pagina.put("caracteres", texto.length());
        pagina.put("tiene_texto", tieneTexto);
        paginas.add(pagina);
      }
      return new PdfTextoExtraidoDTO(numeroPaginas, caracteres, paginasSinTexto, paginas);
    }
  }
}

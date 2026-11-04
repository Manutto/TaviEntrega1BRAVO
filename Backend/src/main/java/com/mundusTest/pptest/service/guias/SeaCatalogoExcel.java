package com.mundusTest.pptest.service.guias;

import com.mundusTest.pptest.entity.SeaDocumento;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

@Service
public class SeaCatalogoExcel {
  // IDGuía usa el resultado calculado guardado en el XLSX.
  private final DataFormatter formatter;

  public SeaCatalogoExcel() {
    formatter = new DataFormatter(Locale.forLanguageTag("es-CL"));
    formatter.setUseCachedValuesForFormulaCells(true);
  }

  public List<SeaDocumento> leer(byte[] xlsx) throws IOException {
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(xlsx))) {
      List<SeaDocumento> documentos = new ArrayList<>();
      leerHoja(workbook, "Guías", "GUIA", documentos);
      if (documentos.isEmpty()) {
        throw new IllegalArgumentException("El archivo no contiene guías válidas.");
      }
      return documentos;
    }
  }

  private void leerHoja(Workbook libro, String nombre, String tipo, List<SeaDocumento> destino) {
    Sheet hoja = libro.getSheet(nombre);
    if (hoja == null) throw new IllegalArgumentException("No existe la hoja requerida: " + nombre);
    int filaEncabezado = -1;
    for (int i = hoja.getFirstRowNum(); i <= Math.min(hoja.getLastRowNum(), 20); i++) {
      Row fila = hoja.getRow(i);
      if (fila != null && texto(fila.getCell(0)).toLowerCase(Locale.ROOT).startsWith("id")) {
        if (texto(fila.getCell(3)).equalsIgnoreCase("NOMBRE")) {
          filaEncabezado = i;
          break;
        }
      }
    }
    if (filaEncabezado == -1)
      throw new IllegalArgumentException("No se encontraron encabezados SEA en " + nombre);

    for (int i = filaEncabezado + 1; i <= hoja.getLastRowNum(); i++) {
      Row fila = hoja.getRow(i);
      if (fila == null || texto(fila.getCell(0)).isBlank()) continue;
      String idTexto = texto(fila.getCell(0));
      int id;
      try {
        id = Integer.parseInt(idTexto.replaceAll("\\.0$", ""));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "ID inválido en " + nombre + " fila " + (i + 1) + ": " + idTexto, e);
      }
      if (id <= 0)
        throw new IllegalArgumentException("ID no positivo en " + nombre + " fila " + (i + 1));
      String nombreDocumento = texto(fila.getCell(3));
      if (nombreDocumento.isBlank())
        throw new IllegalArgumentException(
            "Documento sin nombre en " + nombre + " fila " + (i + 1));
      String anioTexto = texto(fila.getCell(2));
      Integer anio = null;
      if (!anioTexto.isBlank()) {
        try {
          anio = Integer.parseInt(anioTexto.replaceAll("\\.0$", ""));
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Año inválido en " + nombre + " fila " + (i + 1), e);
        }
      }

      Map<String, Object> enlaces = new LinkedHashMap<>();
      ponerEnlace(enlaces, "documento_pdf", fila.getCell(3));
      ponerEnlace(enlaces, "resolucion", fila.getCell(4));
      ponerEnlace(enlaces, "diario_oficial", fila.getCell(6));
      ponerEnlace(enlaces, "afiche_ciudadano", fila.getCell(7));

      Map<String, Object> datos = new LinkedHashMap<>();
      datos.put("tipo_documento", tipo);
      datos.put("id_documento", id);
      datos.put("hoja_excel", nombre);
      datos.put("fila_excel", i + 1);
      datos.put("tipo_categoria", texto(fila.getCell(1)));
      datos.put("anio_publicacion", anio);
      datos.put("nombre", nombreDocumento);
      datos.put("resolucion", texto(fila.getCell(4)));
      datos.put("vigencia", texto(fila.getCell(5)));
      datos.put("extracto_diario_oficial", texto(fila.getCell(6)));
      datos.put("afiche_ciudadano", texto(fila.getCell(7)));
      datos.put("enlaces", enlaces);

      destino.add(new SeaDocumento(tipo, id, nombreDocumento, texto(fila.getCell(5)), anio, datos));
    }
  }

  private String texto(Cell celda) {
    return celda == null ? "" : formatter.formatCellValue(celda).trim().replace('\u00a0', ' ');
  }

  private void ponerEnlace(Map<String, Object> enlaces, String campo, Cell celda) {
    // Las URL del SEA están embebidas como Hyperlink de Excel, no en el valor de las celdas.
    if (celda != null && celda.getHyperlink() != null) {
      String url = celda.getHyperlink().getAddress();
      if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
        enlaces.put(campo, url);
      }
    }
  }
}

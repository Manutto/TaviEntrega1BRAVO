package com.mundusTest.pptest.dto.guias;

import java.util.List;
import java.util.Map;

public record PdfTextoExtraidoDTO(
    int numeroPaginas, long caracteres, int paginasSinTexto, List<Map<String, Object>> paginas) {}

package com.mundusTest.pptest.dto.consulta;

public record FragmentoConsultaDTO(
    int fragmentoId, String documentoTipo, int documentoId, String titulo, int pagina,
    String texto, Double similitud, String idOrigen, String urlPdf, String sha256Pdf) {}

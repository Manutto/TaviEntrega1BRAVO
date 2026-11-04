package com.mundusTest.pptest.entity;

import java.util.Map;

public record SeaDocumento(
    String tipoDocumento,
    int idDocumento,
    String nombre,
    String vigencia,
    Integer anioPublicacion,
    Map<String, Object> datos) {}

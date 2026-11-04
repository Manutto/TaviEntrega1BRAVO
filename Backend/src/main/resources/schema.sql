CREATE TABLE IF NOT EXISTS sea_guia_ingesta (
    id BIGSERIAL PRIMARY KEY,
    estado VARCHAR(32) NOT NULL,
    etapa VARCHAR(32) NOT NULL,
    resultado JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT,
    iniciado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizado_en TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS sea_guia_ingesta_activa_idx ON sea_guia_ingesta((true))
    WHERE estado IN ('EN_COLA','EN_PROCESO');

CREATE TABLE IF NOT EXISTS sea_documento (
    tipo_documento VARCHAR(12) NOT NULL,
    id_documento INTEGER NOT NULL,
    nombre TEXT NOT NULL,
    vigencia TEXT,
    anio_publicacion INTEGER,
    datos_json JSONB NOT NULL,
    sha256_registro CHAR(64) NOT NULL,
    presente_en_indice BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revisado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tipo_documento, id_documento)
);
CREATE INDEX IF NOT EXISTS sea_documento_vigencia_idx ON sea_documento(vigencia);
CREATE INDEX IF NOT EXISTS sea_documento_tipo_idx ON sea_documento(tipo_documento);
CREATE INDEX IF NOT EXISTS sea_documento_json_gin_idx ON sea_documento USING GIN(datos_json);

CREATE TABLE IF NOT EXISTS sea_sincronizacion (
    id BIGSERIAL PRIMARY KEY,
    iniciada_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finalizada_en TIMESTAMPTZ,
    estado VARCHAR(15) NOT NULL DEFAULT 'EN_PROCESO',
    url_pagina TEXT,
    url_excel TEXT,
    sha256_excel CHAR(64),
    total INTEGER NOT NULL DEFAULT 0,
    nuevos INTEGER NOT NULL DEFAULT 0,
    actualizados INTEGER NOT NULL DEFAULT 0,
    sin_cambios INTEGER NOT NULL DEFAULT 0,
    ausentes INTEGER NOT NULL DEFAULT 0,
    mensaje TEXT
);
CREATE INDEX IF NOT EXISTS sea_sincronizacion_inicio_idx ON sea_sincronizacion(iniciada_en DESC);

CREATE TABLE IF NOT EXISTS sea_pdf_version (
    id BIGSERIAL PRIMARY KEY,
    tipo_documento VARCHAR(12) NOT NULL,
    id_documento INTEGER NOT NULL,
    url_pdf TEXT NOT NULL,
    sha256_pdf CHAR(64) NOT NULL,
    pdf_original BYTEA NOT NULL,
    bytes_pdf BIGINT NOT NULL,
    numero_paginas INTEGER NOT NULL,
    paginas_sin_texto INTEGER NOT NULL,
    caracteres BIGINT NOT NULL,
    datos_json JSONB NOT NULL,
    es_actual BOOLEAN NOT NULL DEFAULT TRUE,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verificado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tipo_documento, id_documento, sha256_pdf),
    FOREIGN KEY (tipo_documento, id_documento)
        REFERENCES sea_documento (tipo_documento, id_documento)
);
CREATE UNIQUE INDEX IF NOT EXISTS sea_pdf_actual_unica_idx
    ON sea_pdf_version(tipo_documento, id_documento) WHERE es_actual;
CREATE INDEX IF NOT EXISTS sea_pdf_documento_idx
    ON sea_pdf_version(tipo_documento, id_documento, procesado_en DESC);

CREATE TABLE IF NOT EXISTS sea_preparacion_version (
    version_id BIGINT PRIMARY KEY REFERENCES sea_pdf_version(id) ON DELETE CASCADE,
    algoritmo VARCHAR(100) NOT NULL,
    estado VARCHAR(24) NOT NULL,
    total_paginas INTEGER NOT NULL DEFAULT 0,
    paginas_sin_texto INTEGER NOT NULL DEFAULT 0,
    paginas_texto_escaso INTEGER NOT NULL DEFAULT 0,
    paginas_alertas INTEGER NOT NULL DEFAULT 0,
    caracteres_limpios BIGINT NOT NULL DEFAULT 0,
    total_fragmentos INTEGER NOT NULL DEFAULT 0,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    mensaje TEXT
);
CREATE TABLE IF NOT EXISTS sea_pagina_preparada (
    version_id BIGINT NOT NULL REFERENCES sea_pdf_version(id) ON DELETE CASCADE,
    pagina INTEGER NOT NULL,
    texto_limpio TEXT NOT NULL,
    sha256_texto_original CHAR(64) NOT NULL,
    caracteres INTEGER NOT NULL,
    alertas JSONB NOT NULL DEFAULT '[]'::jsonb,
    PRIMARY KEY (version_id, pagina)
);
CREATE TABLE IF NOT EXISTS sea_fragmento (
    version_id BIGINT NOT NULL REFERENCES sea_pdf_version(id) ON DELETE CASCADE,
    orden INTEGER NOT NULL,
    pagina_inicio INTEGER NOT NULL,
    pagina_fin INTEGER NOT NULL,
    offset_inicio INTEGER NOT NULL,
    offset_fin INTEGER NOT NULL,
    texto TEXT NOT NULL,
    sha256_texto CHAR(64) NOT NULL,
    algoritmo VARCHAR(100) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (version_id, orden)
);
CREATE INDEX IF NOT EXISTS sea_fragmento_pagina_idx ON sea_fragmento(version_id, pagina_inicio);

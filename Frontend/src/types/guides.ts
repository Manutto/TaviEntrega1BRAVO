export interface GuideSummary {
  tipo_documento: 'GUIA';
  id_documento: number;
  nombre: string;
  version_id: number | null;
  estado: string | null;
  paginas: number | null;
  fragmentos: number | null;
  paginas_sin_texto: number | null;
}

export interface GuidePage {
  pagina_pdf: number;
  texto_limpio: string;
  texto_original?: string;
  alertas: string[];
}

export interface GuideChunk {
  id_fragmento: string;
  texto: string;
  pagina_inicio: number;
  pagina_fin: number;
  offset_inicio: number;
  offset_fin: number;
  sha256_texto: string;
}

export interface GuideDocument {
  tipo: 'GUIA';
  id: number;
  nombre: string;
  url_pdf: string;
  sha256_pdf: string;
  version_id: number;
}

export interface GuideJson {
  schema_version: string;
  documento: GuideDocument;
  metadatos_catalogo: Record<string, unknown>;
  calidad: Record<string, unknown>;
  advertencias: string[];
  paginas: GuidePage[];
  fragmentos: GuideChunk[];
}

export interface IngestionJob {
  id: number;
  estado: string;
  etapa: string;
  resultado?: Record<string, unknown> | null;
  error?: string | null;
  iniciado_en?: string | null;
  finalizado_en?: string | null;
}

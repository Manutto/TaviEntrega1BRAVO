export interface TipoProyectoConsulta {
  codigo: string;
  nombre: string;
}

export interface FiltrosConsulta {
  regiones: { codigo: string; nombre: string }[];
  tiposProyecto?: TipoProyectoConsulta[];
}

export interface ConsultaRequest {
  tipoProyecto: string;
  region: string;
  comuna: string;
  descripcion: string;
}

export interface Fragmento {
  fragmentoId: number;
  documentoTipo: 'GUIA';
  documentoId: number;
  titulo: string;
  pagina: number;
  texto: string;
  similitud: number | null;
  // Identificador original del fragmento; fragmentoId numera las citas de esta consulta.
  idOrigen?: string;
  urlPdf?: string;
  sha256Pdf?: string;
}

export type NivelRiesgo = 'ALTO' | 'MEDIO' | 'BAJO';

export interface PermisoPosible {
  pas: string;
  motivo: string;
  citas: number[];
}

export interface AlertaConsulta {
  nivel: NivelRiesgo;
  descripcion: string;
  citas: number[];
}

export interface MetadatosPreInforme {
  modelo: string;
  usoRespaldo: boolean;
  tokensEntrada: number;
  tokensSalida: number;
  latenciaMs: number;
}

export interface PreInforme {
  resumen: string;
  permisosPosibles: PermisoPosible[];
  alertas: AlertaConsulta[];
  nivelRiesgo: NivelRiesgo;
  sinEvidencia: string[];
  advertencia: string;
  fuentes: Fragmento[];
  metadatos: MetadatosPreInforme;
  consultaId?: string;
  fragmentosConsultados?: Fragmento[];
}

export interface PreInformeProps {
  preInforme: PreInforme;
}

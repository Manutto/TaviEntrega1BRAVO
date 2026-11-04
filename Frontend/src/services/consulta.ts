import { api } from './http';
import { routes } from './routes';
import type {
  ConsultaRequest,
  PreInforme,
  FiltrosConsulta,
  TipoProyectoConsulta,
} from '../types/consulta';

export interface CatalogoConsulta {
  regiones: FiltrosConsulta['regiones'];
  tipos: TipoProyectoConsulta[];
}

export async function cargarCatalogoConsulta(signal: AbortSignal): Promise<CatalogoConsulta> {
  const catalogo = await api<FiltrosConsulta>(routes.consultationCatalog, { signal });
  return { regiones: catalogo.regiones, tipos: catalogo.tiposProyecto ?? [] };
}

export function enviarConsulta(body: ConsultaRequest, signal: AbortSignal): Promise<PreInforme> {
  return api<PreInforme>(routes.consulta, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
    signal,
  });
}

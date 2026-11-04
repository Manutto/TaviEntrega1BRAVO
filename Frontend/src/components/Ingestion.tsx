import { Play, RefreshCw } from 'lucide-react';
import { fmtDate, type IngestionJob } from '../api';
import type { useIngestion } from '../hooks/useIngestion';

const steps = [
  ['Página del SEA', 'Se encuentra el enlace del catálogo oficial.'],
  ['Índice Excel', 'Se leen los registros y enlaces de las guías.'],
  ['Archivos PDF', 'Se descargan los PDF y se conserva cada original con su versión.'],
  ['Texto y JSON', 'Se extrae el texto por página y se preparan fragmentos.'],
  ['Búsqueda', 'Se prepara el contenido para las consultas del asistente.'],
];

const stageNames: Record<string, string> = {
  INICIO: 'Iniciando',
  EXCEL: 'Descargando y leyendo el índice Excel',
  PDF_JSON: 'Descargando PDF y preparando JSON',
  VECTORES: 'Preparando la búsqueda',
  FINALIZADO: 'Finalizado',
};

function JobDetail({ job }: { job: IngestionJob }) {
  return (
    <article className="job-detail">
      <div className="job-heading">
        <h3>Ingesta #{job.id}</h3>
        <span className={`status-pill ${job.estado.includes('ERROR') ? 'failed' : ''}`}>
          {job.estado.replaceAll('_', ' ')}
        </span>
      </div>
      <p>
        Etapa: <strong>{stageNames[job.etapa] ?? job.etapa.replaceAll('_', ' ')}</strong>
      </p>
      {job.resultado && typeof job.resultado.total_guias === 'number' && (
        <p>
          Guías procesadas: {String(job.resultado.procesadas ?? 0)} de {job.resultado.total_guias}.{' '}
          JSON exportados: {String(job.resultado.json_exportados ?? 0)}.
        </p>
      )}
      {job.iniciado_en && <small>Inicio: {fmtDate(job.iniciado_en)}</small>}
      {job.error && (
        <p className="message error" role="alert">
          {job.error}
        </p>
      )}
      {job.resultado && Object.keys(job.resultado).length > 0 && (
        <details>
          <summary>Ver resultado de la ejecución</summary>
          <pre className="json-text">{JSON.stringify(job.resultado, null, 2)}</pre>
        </details>
      )}
    </article>
  );
}

export default function Ingestion({
  jobs,
  active,
  loading,
  starting,
  error,
  start,
  refresh,
}: ReturnType<typeof useIngestion>) {
  return (
    <section>
      <div className="section-heading">
        <div>
          <p className="eyebrow">DESCARGA Y EXTRACCIÓN</p>
          <h1>Procesamiento de guías</h1>
          <p>Descarga las guías oficiales y prepara su contenido para las consultas.</p>
        </div>
        <button className="button secondary" disabled={loading} onClick={() => void refresh()}>
          <RefreshCw size={16} /> Actualizar
        </button>
      </div>

      <div className="panel flow-panel">
        <ol className="pipeline">
          {steps.map(([title, description], index) => (
            <li key={title}>
              <span className="step-number">{index + 1}</span>
              <div>
                <strong>{title}</strong>
                <p>{description}</p>
              </div>
            </li>
          ))}
        </ol>

        <div className="flow-action">
          <button
            className="button primary"
            disabled={starting || !!active}
            onClick={() => void start()}
          >
            <Play size={16} />
            {starting
              ? 'Iniciando…'
              : active
                ? 'Procesamiento en curso…'
                : 'Procesar guías del SEA'}
          </button>
          <p>
            Se consultan fuentes oficiales y se utilizan los modelos disponibles en el equipo. Los
            errores quedan registrados en la ejecución.
          </p>
        </div>
      </div>

      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}

      <section className="panel history">
        <h2>Ejecuciones recientes</h2>
        {loading && !jobs.length && <p className="empty">Consultando ejecuciones…</p>}
        {!loading && !jobs.length && (
          <p className="empty">Todavía no hay ejecuciones registradas.</p>
        )}
        {jobs.map((job) => (
          <JobDetail key={job.id} job={job} />
        ))}
      </section>
    </section>
  );
}

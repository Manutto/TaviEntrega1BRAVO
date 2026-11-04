import { Download, FileText } from 'lucide-react';
import type { PreInformeProps } from '../../types/consulta';
import { routes } from '../../services/routes';
import './consulta.css';

export default function PreInformeResult({ preInforme }: PreInformeProps) {
  const fragmentos = preInforme.fragmentosConsultados ?? preInforme.fuentes;

  function descargar() {
    const archivo = new Blob([JSON.stringify(preInforme, null, 2)], {
      type: 'application/json;charset=utf-8',
    });
    const url = URL.createObjectURL(archivo);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = `preinforme-${preInforme.consultaId ?? 'proyecto'}.json`;
    enlace.click();
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  function citas(ids: number[]) {
    return (
      <div className="consulta-citas" aria-label="Fragmentos citados">
        {ids.map((id) => (
          <a key={id} href={`#consulta-fuente-${id}`}>
            Fragmento {id}
          </a>
        ))}
      </div>
    );
  }

  return (
    <section className="panel intake-panel consulta-resultado" aria-label="Preinforme documental">
      <div className="section-heading">
        <div>
          <div className="eyebrow">RESULTADO DOCUMENTAL</div>
          <h3>Preinforme del proyecto</h3>
        </div>
        <button type="button" className="button secondary" onClick={descargar}>
          <Download size={16} /> Descargar JSON
        </button>
      </div>

      <p className="consulta-resumen">{preInforme.resumen}</p>
      <p className="consulta-riesgo">
        Nivel estimado por el modelo: <strong>{preInforme.nivelRiesgo}</strong>
      </p>

      <h4>Permisos posibles según los fragmentos</h4>
      {preInforme.permisosPosibles.length === 0 ? (
        <p>No se identificaron PAS explícitos en la respuesta. Esto no descarta otros permisos.</p>
      ) : (
        <ul className="consulta-items">
          {preInforme.permisosPosibles.map((permiso, index) => (
            <li key={`${permiso.pas}-${index}`}>
              <strong>{permiso.pas}</strong>
              <p>{permiso.motivo}</p>
              {citas(permiso.citas)}
            </li>
          ))}
        </ul>
      )}

      <h4>Alertas documentales</h4>
      {preInforme.alertas.length === 0 ? (
        <p>La respuesta no incluye alertas respaldadas por citas.</p>
      ) : (
        <ul className="consulta-items">
          {preInforme.alertas.map((alerta, index) => (
            <li key={index}>
              <strong>{alerta.nivel}</strong>
              <p>{alerta.descripcion}</p>
              {citas(alerta.citas)}
            </li>
          ))}
        </ul>
      )}

      <h4>Información que falta</h4>
      {preInforme.sinEvidencia.length ? (
        <ul>
          {preInforme.sinEvidencia.map((texto, index) => (
            <li key={index}>{texto}</li>
          ))}
        </ul>
      ) : (
        <p>
          El modelo no señaló información faltante; revisa igualmente los fragmentos consultados.
        </p>
      )}

      <h4>Fragmentos consultados ({fragmentos.length})</h4>
      <p>Abre cada fragmento para contrastar la respuesta con su texto y la página del PDF.</p>
      <div className="consulta-fuentes">
        {fragmentos.map((fuente) => (
          <article key={fuente.fragmentoId} id={`consulta-fuente-${fuente.fragmentoId}`}>
            <div className="consulta-fuente-titulo">
              <FileText size={18} />
              <strong>
                Fragmento {fuente.fragmentoId} · {fuente.titulo}
              </strong>
            </div>
            <p>Página PDF {fuente.pagina}</p>
            <a
              href={`${routes.pdf(fuente.documentoId)}#page=${fuente.pagina}`}
              target="_blank"
              rel="noreferrer"
            >
              Abrir página del PDF
            </a>
            <details>
              <summary>Ver texto original del fragmento</summary>
              <blockquote>{fuente.texto}</blockquote>
              {fuente.idOrigen && (
                <small className="consulta-origen">Referencia: {fuente.idOrigen}</small>
              )}
            </details>
          </article>
        ))}
      </div>

      <p className="consulta-advertencia">{preInforme.advertencia}</p>
      <dl className="consulta-metricas">
        <div>
          <dt>Modelo</dt>
          <dd>{preInforme.metadatos.modelo}</dd>
        </div>
        <div>
          <dt>Tokens de entrada</dt>
          <dd>{preInforme.metadatos.tokensEntrada}</dd>
        </div>
        <div>
          <dt>Tokens de salida</dt>
          <dd>{preInforme.metadatos.tokensSalida}</dd>
        </div>
        <div>
          <dt>Generación</dt>
          <dd>{(preInforme.metadatos.latenciaMs / 1000).toFixed(1)} s</dd>
        </div>
      </dl>
      {preInforme.consultaId && <small>Consulta: {preInforme.consultaId}</small>}
    </section>
  );
}

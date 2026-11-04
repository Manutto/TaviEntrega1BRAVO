import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Download, ExternalLink, FileJson } from 'lucide-react';
import { api, fmt, routes, safeExternalUrl, type GuideJson, type GuideSummary } from '../../api';

type Tab = 'texto' | 'fragmentos' | 'metadatos';

export default function DocumentDetail({ guide }: { guide: GuideSummary }) {
  const [document, setDocument] = useState<GuideJson | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tab, setTab] = useState<Tab>('texto');
  const [pageIndex, setPageIndex] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    if (guide.version_id === null) {
      setLoading(false);
      return () => controller.abort();
    }

    void api<GuideJson>(routes.json(guide.id_documento), { signal: controller.signal })
      .then((result) => {
        if (!controller.signal.aborted) setDocument(result);
      })
      .catch((cause: Error) => {
        if (!controller.signal.aborted) setError(cause.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [guide.id_documento, guide.version_id]);

  const page = document?.paginas[pageIndex];
  const source = safeExternalUrl(document?.documento.url_pdf);
  const chunks =
    document?.fragmentos.filter((chunk) => chunk.pagina_inicio === page?.pagina_pdf) ?? [];

  return (
    <article>
      <p className="eyebrow">GUÍA #{guide.id_documento}</p>
      <h2>{guide.nombre}</h2>

      <div className="actions">
        {guide.version_id !== null && (
          <a
            className="button secondary"
            href={routes.pdf(guide.id_documento)}
            target="_blank"
            rel="noreferrer"
          >
            <Download size={16} /> PDF original
          </a>
        )}
        {document && (
          <a className="button primary" href={routes.json(guide.id_documento)}>
            <FileJson size={16} /> Descargar JSON
          </a>
        )}
        {source && (
          <a className="text-link" href={source} target="_blank" rel="noreferrer">
            Fuente SEA <ExternalLink size={14} />
          </a>
        )}
      </div>

      {loading && <p className="empty">Leyendo JSON de la guía…</p>}
      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}
      {!loading && guide.version_id === null && (
        <p className="message">Esta guía está en el catálogo. Su PDF aún no se ha descargado.</p>
      )}

      {document && (
        <>
          <div className="metrics">
            <div>
              <strong>{fmt(document.paginas.length)}</strong>
              <span>Páginas</span>
            </div>
            <div>
              <strong>{fmt(document.fragmentos.length)}</strong>
              <span>Fragmentos</span>
            </div>
            <div>
              <strong>
                {fmt(document.paginas.filter((item) => item.alertas.length > 0).length)}
              </strong>
              <span>Páginas con alertas</span>
            </div>
          </div>

          <p className="message">
            Las páginas escaneadas o con tablas pueden requerir revisión del PDF original.
          </p>

          <div className="tabs" role="tablist" aria-label="Contenido de la guía">
            {(['texto', 'fragmentos', 'metadatos'] as const).map((value) => (
              <button
                key={value}
                role="tab"
                aria-selected={tab === value}
                className={tab === value ? 'active' : ''}
                onClick={() => setTab(value)}
              >
                {value === 'texto'
                  ? 'Texto por página'
                  : value === 'fragmentos'
                    ? 'Fragmentos'
                    : 'Metadatos'}
              </button>
            ))}
          </div>

          {tab !== 'metadatos' && (
            <div className="page-controls">
              <button
                className="icon-button"
                disabled={pageIndex === 0}
                onClick={() => setPageIndex((value) => value - 1)}
                aria-label="Página anterior"
              >
                <ChevronLeft size={19} />
              </button>
              <span>
                Página PDF {page?.pagina_pdf ?? '—'} de {document.paginas.length}
              </span>
              <button
                className="icon-button"
                disabled={pageIndex >= document.paginas.length - 1}
                onClick={() => setPageIndex((value) => value + 1)}
                aria-label="Página siguiente"
              >
                <ChevronRight size={19} />
              </button>
            </div>
          )}

          {tab === 'texto' && page && (
            <>
              {page.alertas.length > 0 && (
                <p className="message warning">{page.alertas.join(' · ')}</p>
              )}
              <pre className="page-text">
                {page.texto_limpio || 'No se encontró texto extraíble en esta página.'}
              </pre>
            </>
          )}

          {tab === 'fragmentos' && (
            <div>
              {chunks.length === 0 && <p className="empty">No hay fragmentos en esta página.</p>}
              {chunks.map((chunk) => (
                <article className="chunk" key={chunk.id_fragmento}>
                  <small>
                    Página {chunk.pagina_inicio} · posiciones {chunk.offset_inicio}–
                    {chunk.offset_fin}
                  </small>
                  <p>{chunk.texto}</p>
                </article>
              ))}
            </div>
          )}

          {tab === 'metadatos' && (
            <pre className="json-text">
              {JSON.stringify(
                {
                  documento: document.documento,
                  catalogo: document.metadatos_catalogo,
                  calidad: document.calidad,
                  advertencias: document.advertencias,
                },
                null,
                2,
              )}
            </pre>
          )}
        </>
      )}
    </article>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { BookOpen, FileText, RefreshCw, Search } from 'lucide-react';
import { api, fmt, routes, type GuideSummary } from '../api';
import DocumentDetail from './library/DocumentDetail';

export default function Library({ revision }: { revision: number }) {
  const [guides, setGuides] = useState<GuideSummary[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);

    void api<GuideSummary[]>(routes.guides, { signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return;

        setGuides(result);
        setSelected((current) =>
          result.some((guide) => guide.id_documento === current)
            ? current
            : (result[0]?.id_documento ?? null),
        );
        setError('');
      })
      .catch((cause: Error) => {
        if (!controller.signal.aborted) setError(cause.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [revision, refresh]);

  const filtered = useMemo(
    () =>
      guides.filter((guide) =>
        `${guide.nombre} ${guide.id_documento}`
          .toLocaleLowerCase('es-CL')
          .includes(query.toLocaleLowerCase('es-CL')),
      ),
    [guides, query],
  );
  const current = guides.find((guide) => guide.id_documento === selected);

  return (
    <section>
      <div className="section-heading">
        <div>
          <p className="eyebrow">FUENTE OFICIAL · SEA</p>
          <h1>Biblioteca de guías</h1>
          <p>Consulta los PDF originales y el texto extraído de cada guía.</p>
        </div>
        <button
          className="button secondary"
          disabled={loading}
          onClick={() => setRefresh((v) => v + 1)}
        >
          <RefreshCw size={16} /> Actualizar
        </button>
      </div>

      {error && (
        <p className="message error" role="alert">
          {error}
        </p>
      )}

      <div className="library-layout">
        <aside className="panel catalog">
          <div className="catalog-heading">
            <BookOpen size={19} />
            <strong>{fmt(filtered.length)} guías</strong>
          </div>

          <label className="search-input">
            <Search size={17} />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Filtrar por nombre o ID"
              aria-label="Filtrar guías por nombre o ID"
            />
          </label>

          <div className="guide-list">
            {loading && <p className="empty">Cargando catálogo…</p>}
            {!loading && !filtered.length && <p className="empty">No hay guías para mostrar.</p>}

            {filtered.map((guide) => (
              <button
                className={`guide-row ${selected === guide.id_documento ? 'selected' : ''}`}
                key={guide.id_documento}
                aria-pressed={selected === guide.id_documento}
                onClick={() => setSelected(guide.id_documento)}
              >
                <FileText size={19} />
                <span>
                  <strong>{guide.nombre}</strong>
                  <small>
                    #{guide.id_documento} ·{' '}
                    {guide.paginas === null ? 'PDF pendiente' : `${fmt(guide.paginas)} páginas`}
                  </small>
                  <small>{guide.estado ?? 'Sin preparar'}</small>
                </span>
              </button>
            ))}
          </div>
        </aside>

        <section className="panel document-panel">
          {current ? (
            <DocumentDetail
              key={`${current.id_documento}-${revision}-${refresh}`}
              guide={current}
            />
          ) : (
            <div className="empty">
              <BookOpen size={32} />
              <h2>Selecciona una guía</h2>
              <p>Inicia el procesamiento para descargar el catálogo del SEA.</p>
            </div>
          )}
        </section>
      </div>
    </section>
  );
}

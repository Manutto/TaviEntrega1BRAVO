import { useEffect, useRef, useState, type FormEvent } from 'react';
import { FilePlus2, RotateCcw } from 'lucide-react';
import type { ConsultaRequest, PreInforme } from '../../api';
import {
  cargarCatalogoConsulta,
  enviarConsulta,
  type CatalogoConsulta,
} from '../../services/consulta';
import PreInformeResult from './PreInformeResult';
import './formulario.css';

const EMPTY: ConsultaRequest = { tipoProyecto: '', region: '', comuna: '', descripcion: '' };
type Campo = keyof ConsultaRequest;
type Errores = Partial<Record<Campo, string>>;
const CAMPOS: Campo[] = ['tipoProyecto', 'region', 'comuna', 'descripcion'];
const charCount = (text: string) => Array.from(text.trim()).length;

export default function ConsultaProyecto() {
  const [values, setValues] = useState<ConsultaRequest>(EMPTY);
  const [errors, setErrors] = useState<Errores>({});
  const [serverError, setServerError] = useState('');
  const [catalogo, setCatalogo] = useState<CatalogoConsulta | null>(null);
  const [catalogoError, setCatalogoError] = useState('');
  const [catalogoLoading, setCatalogoLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [resultado, setResultado] = useState<PreInforme | null>(null);
  const request = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setCatalogo(null);
    setCatalogoError('');
    setCatalogoLoading(true);
    void cargarCatalogoConsulta(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setCatalogo(data);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted)
          setCatalogoError(
            error instanceof Error ? error.message : 'No se pudo cargar el catálogo.',
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setCatalogoLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  useEffect(() => () => request.current?.abort(), []);

  function limpiar() {
    request.current?.abort();
    request.current = null;
    setValues(EMPTY);
    setErrors({});
    setServerError('');
    setResultado(null);
    setSubmitting(false);
  }

  function change(campo: Campo, value: string) {
    setValues((previous) => ({ ...previous, [campo]: value }));
    setErrors((previous) => ({ ...previous, [campo]: undefined }));
    setResultado(null);
    setServerError('');
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (request.current || catalogoLoading || !catalogo) return;
    setResultado(null);
    setServerError('');
    const body: ConsultaRequest = {
      tipoProyecto: values.tipoProyecto.trim(),
      region: values.region.trim(),
      comuna: values.comuna.trim(),
      descripcion: values.descripcion.trim(),
    };
    const next: Errores = {};
    for (const campo of CAMPOS) {
      if (!body[campo]) next[campo] = 'Este campo es obligatorio.';
    }
    if (
      body.descripcion &&
      (charCount(body.descripcion) < 50 || charCount(body.descripcion) > 2000)
    ) {
      next.descripcion = 'La descripción debe tener entre 50 y 2.000 caracteres.';
    }
    if (body.tipoProyecto && !catalogo.tipos.some((tipo) => tipo.nombre === body.tipoProyecto))
      next.tipoProyecto = 'Selecciona un tipo del catálogo.';
    if (body.region && !catalogo.regiones.some((r) => r.nombre === body.region))
      next.region = 'Selecciona una región del catálogo.';
    setErrors(next);
    if (Object.keys(next).length) {
      document.getElementById(`consulta-${CAMPOS.find((campo) => next[campo])}`)?.focus();
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setSubmitting(true);
    try {
      const data = await enviarConsulta(body, controller.signal);
      if (!controller.signal.aborted && request.current === controller) setResultado(data);
    } catch (error: unknown) {
      if (!controller.signal.aborted && request.current === controller) {
        setServerError(error instanceof Error ? error.message : 'No se pudo realizar la consulta.');
      }
    } finally {
      if (request.current === controller) {
        request.current = null;
        setSubmitting(false);
      }
    }
  }

  const a11y = (campo: Campo) => ({
    id: `consulta-${campo}`,
    required: true,
    'aria-invalid': errors[campo] ? true : undefined,
    'aria-describedby': errors[campo] ? `consulta-${campo}-error` : undefined,
  });
  const fieldError = (campo: Campo) =>
    errors[campo] && (
      <p className="intake-error" id={`consulta-${campo}-error`}>
        {errors[campo]}
      </p>
    );
  const disponible = !!catalogo?.regiones.length && !!catalogo?.tipos.length;

  return (
    <div className="main-in intake-view">
      <div className="section-heading">
        <div>
          <div className="eyebrow">CONSULTA</div>
          <h2>Consulta de proyecto</h2>
          <p>
            Describe el proyecto para consultar las guías y generar un preinforme con Qwen local.
          </p>
        </div>
        <div className="header-chip">
          <FilePlus2 size={16} />
          Consulta
        </div>
      </div>
      <section className="panel intake-panel">
        {catalogoLoading && <p role="status">Cargando catálogos…</p>}
        {catalogoError && (
          <div role="alert">
            <p>{catalogoError}</p>
            <button
              type="button"
              className="button secondary"
              onClick={() => setRetry((n) => n + 1)}
            >
              Reintentar catálogos
            </button>
          </div>
        )}
        {catalogo && !catalogo.tipos.length && (
          <div role="alert">
            <p>
              El catálogo no incluye tipos de proyecto. La consulta estará disponible cuando se
              habiliten.
            </p>
            <button
              type="button"
              className="button secondary"
              onClick={() => setRetry((n) => n + 1)}
            >
              Reintentar catálogos
            </button>
          </div>
        )}
        {catalogo && !catalogo.regiones.length && (
          <p role="alert">El catálogo no incluye regiones.</p>
        )}
        {serverError && (
          <div className="inline-error" role="alert">
            {serverError}
          </div>
        )}
        <form
          onSubmit={(e) => void submit(e)}
          noValidate
          aria-busy={submitting}
          aria-label="Consulta de proyecto"
        >
          <fieldset className="intake-fields" disabled={submitting || catalogoLoading}>
            <label className="form-label" htmlFor="consulta-tipoProyecto">
              Tipo de proyecto
            </label>
            <select
              {...a11y('tipoProyecto')}
              className="project-name-input"
              value={values.tipoProyecto}
              disabled={!catalogo?.tipos.length}
              onChange={(e) => change('tipoProyecto', e.target.value)}
            >
              <option value="">Selecciona un tipo</option>
              {catalogo?.tipos.map((tipo) => (
                <option key={tipo.codigo} value={tipo.nombre}>
                  {tipo.nombre}
                </option>
              ))}
            </select>
            {fieldError('tipoProyecto')}
            <label className="form-label" htmlFor="consulta-region">
              Región
            </label>
            <select
              {...a11y('region')}
              className="project-name-input"
              value={values.region}
              disabled={!catalogo?.regiones.length}
              onChange={(e) => change('region', e.target.value)}
            >
              <option value="">Selecciona una región</option>
              {catalogo?.regiones.map((region) => (
                <option key={region.codigo} value={region.nombre}>
                  {region.nombre}
                </option>
              ))}
            </select>
            {fieldError('region')}
            <label className="form-label" htmlFor="consulta-comuna">
              Comuna
            </label>
            <input
              {...a11y('comuna')}
              className="project-name-input"
              value={values.comuna}
              onChange={(e) => change('comuna', e.target.value)}
            />
            {fieldError('comuna')}
            <label className="form-label" htmlFor="consulta-descripcion">
              Descripción
            </label>
            <textarea
              {...a11y('descripcion')}
              className="project-name-input intake-textarea"
              rows={6}
              value={values.descripcion}
              onChange={(e) => change('descripcion', e.target.value)}
            />
            <p className="intake-hint">
              {charCount(values.descripcion)} / 2.000 caracteres · mínimo 50
            </p>
            {fieldError('descripcion')}
          </fieldset>
          <div className="intake-actions">
            <button
              type="submit"
              className="button primary"
              disabled={submitting || catalogoLoading || !disponible}
            >
              {submitting ? 'Analizando documentación…' : 'Consultar proyecto'}
            </button>
            <button type="button" className="button secondary" onClick={limpiar}>
              <RotateCcw size={15} /> Limpiar
            </button>
          </div>
          {submitting && <p role="status">Analizando documentación…</p>}
        </form>
      </section>
      {resultado && <PreInformeResult preInforme={resultado} />}
    </div>
  );
}

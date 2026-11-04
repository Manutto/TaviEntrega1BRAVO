const dateFormatter = new Intl.DateTimeFormat('es-CL', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'America/Santiago',
});
const numberFormatter = new Intl.NumberFormat('es-CL');
export function fmtDate(value?: string | null) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : dateFormatter.format(date);
}
export function fmt(n?: number | null) {
  return numberFormatter.format(n ?? 0);
}

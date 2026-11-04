export const routes = {
  guides: '/api/guias',
  ingestions: '/api/guias/ingestas',
  ingestion: (id: number) => `/api/guias/ingestas/${id}`,
  json: (id: number) => `/api/guias/GUIA/${id}/json`,
  pdf: (id: number) => `/api/guias/GUIA/${id}/pdf`,
  consultationCatalog: '/api/consulta/catalogo',
  consulta: '/api/consulta',
};

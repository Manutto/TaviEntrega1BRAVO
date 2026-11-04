import { useState } from 'react';
import { BookOpen, FilePlus2, Workflow } from 'lucide-react';
import Library from './components/Library';
import Ingestion from './components/Ingestion';
import { useIngestion } from './hooks/useIngestion';
import ConsultaProyecto from './components/consulta/ConsultaProyecto';
import type { View } from './types/navigation';

const navigation = [
  { key: 'consulta', label: 'Consulta de proyecto', Icon: FilePlus2 },
  { key: 'biblioteca', label: 'Biblioteca de guías', Icon: BookOpen },
  { key: 'ingesta', label: 'Procesamiento', Icon: Workflow },
] as const;

export default function App() {
  const [view, setView] = useState<View>('consulta');
  const ingestion = useIngestion();

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-symbol">M</span>
          <div>
            <strong>MUNDUS</strong>
            <small>GUÍAS AMBIENTALES</small>
          </div>
        </div>

        <p className="sidebar-label">CONSULTA Y DOCUMENTACIÓN</p>

        <nav className="nav-list" aria-label="Navegación principal">
          {navigation.map(({ key, label, Icon }) => (
            <button
              key={key}
              className={`nav-btn ${view === key ? 'active' : ''}`}
              aria-current={view === key ? 'page' : undefined}
              onClick={() => setView(key)}
            >
              <Icon size={19} />
              {label}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <BookOpen size={21} />
          <p>Guías del SEA para apoyar la consulta de proyectos.</p>
          <small>Revisa las fuentes y páginas de cada respuesta.</small>
        </div>
      </aside>

      <div className="right-side">
        <header className="topbar">
          <span>Mundus · Guías del SEA</span>
          {ingestion.active && <span className="status-pill">Procesamiento en curso</span>}
        </header>

        <main className="main-content">
          {view === 'consulta' && <ConsultaProyecto />}
          {view === 'biblioteca' && <Library revision={ingestion.revision} />}
          {view === 'ingesta' && <Ingestion {...ingestion} />}
        </main>

        <footer>Fuente documental: Servicio de Evaluación Ambiental de Chile</footer>
      </div>
    </div>
  );
}

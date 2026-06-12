import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useLogout } from '../../hooks/useLogout';
import { PRIVATE_ROUTES } from '../../routes/routeConfig';
import MiniCalendar from '../Calendar/MiniCalendar';

import './Sidebar.css';
import logoIcf from '../../assets/logo_icf.png';

const SALAS = [
  { label: 'Auditorio',           color: '#2563eb' },
  { label: 'Aula I - Multimodal', color: '#16a34a' },
  { label: 'Aula II - Multimodal',color: '#9333ea' },
  { label: 'Aula III',            color: '#ea580c' },
  { label: 'Aula IV',             color: '#f59e0b' },
];

export default function Sidebar() {
  const { user } = useAuth();
  const { handleLogout } = useLogout();
  const [calOpen, setCalOpen]     = useState(true);
  const [salasOpen, setSalasOpen] = useState(true);

  const navItems = PRIVATE_ROUTES.filter(
    ({ sidebar, allowedRoles }) => sidebar.show && allowedRoles.includes(user.role)
  );

  const initials = (user.name || user.role)
    .split(' ')
    .map(w => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  return (
    <aside className="sidebar">
      {/* Brand */}
      <div className="sidebar__brand">
        <img src={logoIcf} alt="ICF Aulas" className="sidebar__logo" />
        <span className="sidebar__brand-name">Aulas ICF</span>
      </div>

      {/* Nav */}
      <nav className="sidebar__nav">
        {navItems.map(({ path, sidebar }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) =>
              `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
            }
          >
            <i className={`bi ${sidebar.icon} sidebar__icon`} />
            <span>{sidebar.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Nueva Reserva */}
      <div className="sidebar__action">
        <button className="sidebar__new-reserva-btn">
          <i className="bi bi-plus-lg" />
          <span>Nueva Reserva</span>
        </button>
      </div>

      {/* Mini Calendario */}
      <div className="sidebar__section">
        <button
          className="sidebar__section-header"
          onClick={() => setCalOpen(o => !o)}
          aria-expanded={calOpen}
        >
          <span>Mini Calendario</span>
          <i className={`bi bi-chevron-${calOpen ? 'up' : 'down'}`} />
        </button>
        {calOpen && <MiniCalendar />}
      </div>

      {/* Salas */}
      <div className="sidebar__section">
        <button
          className="sidebar__section-header"
          onClick={() => setSalasOpen(o => !o)}
          aria-expanded={salasOpen}
        >
          <span>Salas</span>
          <i className={`bi bi-chevron-${salasOpen ? 'up' : 'down'}`} />
        </button>
        {salasOpen && (
          <ul className="sidebar__salas">
            {SALAS.map(({ label, color }) => (
              <li key={label} className="sidebar__sala-item">
                <span className="sidebar__sala-dot" style={{ background: color }} />
                <span>{label}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Footer */}
      <div className="sidebar__footer">
        <div className="sidebar__user">
          <div className="sidebar__avatar">{initials}</div>
          <div className="sidebar__user-info">
            <span className="sidebar__user-name">{user.name}</span>
            <span className="sidebar__user-role">{user.role}</span>
          </div>
        </div>
        <button className="sidebar__logout-btn" onClick={handleLogout}>
          <i className="bi bi-box-arrow-right" />
          <span>Cerrar Sesión</span>
        </button>
      </div>
    </aside>
  );
}

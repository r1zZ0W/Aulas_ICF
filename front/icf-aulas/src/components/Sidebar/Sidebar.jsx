import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useLogout } from '../../hooks/useLogout';
import { PRIVATE_ROUTES } from '../../routes/routeConfig';
import MiniCalendar from '../Calendar/MiniCalendar';
import { useReservation } from '../../context/ReservationContext';
import { DISPLAY_ROLE } from '../../utils/roles';
import LoadingOverlay from '../LoadingOverlay/LoadingOverlay';

import './Sidebar.css';
import aulasHeader from '../../assets/aulas_header.png';

export default function Sidebar() {
  const { user } = useAuth();
  const { handleLogout, loggingOut } = useLogout();
  const { openModal, rooms, visibleRooms, toggleRoom } = useReservation();
  const [calOpen, setCalOpen] = useState(true);
  const [roomsOpen, setRoomsOpen] = useState(true);

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
    <>
      <aside className="sidebar">
        {loggingOut && <LoadingOverlay label="Cerrando sesión..." />}
        {/* Brand */}
        <div className="sidebar__brand">
          <NavLink to="/reservations" className="sidebar__link">
            <img src={aulasHeader} alt="ICF Aulas" className="sidebar__logo" />
          </NavLink>
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
              <span className="sidebar__icon">{sidebar.icon}</span>
              <span>{sidebar.label}</span>
            </NavLink>
          ))}
        </nav>

        {/* Nueva Reserva */}
        <div className="sidebar__action">
          <button className="sidebar__new-reserva-btn" onClick={() => openModal()}>
            <i className="bi bi-plus-lg" />
            <span>Nueva Reserva</span>
          </button>
        </div>

        {/* Mini Calendar */}
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

        {/* Aulas */}
        <div className="sidebar__section">
          <button
            className="sidebar__section-header"
            onClick={() => setRoomsOpen(o => !o)}
            aria-expanded={roomsOpen}
          >
            <span>Aulas</span>
            <i className={`bi bi-chevron-${roomsOpen ? 'up' : 'down'}`} />
          </button>
          {roomsOpen && (
            <ul className="sidebar__salas">
              {rooms.length === 0 ? (
                <li className="sidebar__sala-empty">Sin aulas registradas</li>
              ) : (
                rooms.map(({ uuid, label, color }) => {
                  const visible = visibleRooms.has(uuid);
                  return (
                    <li key={uuid} className="sidebar__sala-item">
                      <button
                        type="button"
                        className={`sidebar__sala-toggle${visible ? ' sidebar__sala-toggle--on' : ''}`}
                        onClick={() => toggleRoom(uuid)}
                        aria-pressed={visible}
                        aria-label={`${visible ? 'Ocultar' : 'Mostrar'} ${label}`}
                        style={{ '--sala-color': color }}
                      >
                        <span
                          className="sidebar__sala-dot"
                          style={{ background: visible ? color : 'transparent', borderColor: color }}
                        />
                        <span className={`sidebar__sala-label${visible ? '' : ' sidebar__sala-label--off'}`}>
                          {label}
                        </span>
                      </button>
                    </li>
                  );
                })
              )}
            </ul>
          )}
        </div>

        {/* Footer */}
        <div className="sidebar__footer">
          <div className="sidebar__user">
            <div className="sidebar__avatar">{initials}</div>
            <div className="sidebar__user-info">
              <span className="sidebar__user-name">{user.name}</span>
              <span className="sidebar__user-role">{DISPLAY_ROLE[user.role]}</span>
            </div>
          </div>
          <button
            className="sidebar__logout-btn"
            onClick={handleLogout}
            disabled={loggingOut}
          >
            <i className="bi bi-box-arrow-right" />
            <span>{loggingOut ? 'Cerrando…' : 'Cerrar Sesión'}</span>
          </button>
        </div>
      </aside>
    </>
  );
}

import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useLogout } from '../../hooks/useLogout';
import { PRIVATE_ROUTES } from '../../routes/routeConfig';
import './Sidebar.css';

/**
 * Role-aware navigation sidebar.
 *
 * Derives the visible menu items by filtering PRIVATE_ROUTES against the
 * current user's role. No hardcoded role checks live here — adding a new
 * route to routeConfig.jsx automatically surfaces it in the sidebar for the
 * right roles without touching this component.
 */
export default function Sidebar() {
  const { user } = useAuth();
  const { handleLogout } = useLogout();

  const navItems = PRIVATE_ROUTES.filter(
    ({ sidebar, allowedRoles }) => sidebar.show && allowedRoles.includes(user.role)
  );

  return (
    <aside className="sidebar">
      <div className="sidebar__brand">
        <span className="sidebar__brand-name">ICF Aulas</span>
      </div>

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

      <div className="sidebar__footer">
        <div className="sidebar__user">
          <i className="bi bi-person-circle sidebar__icon" />
          <div className="sidebar__user-info">
            <span className="sidebar__user-name">{user.name}</span>
            <span className="sidebar__user-role">{user.role}</span>
          </div>
        </div>
        <button className="sidebar__logout-btn" onClick={handleLogout}>
          <i className="bi bi-box-arrow-left" />
          <span>Cerrar sesión</span>
        </button>
      </div>
    </aside>
  );
}

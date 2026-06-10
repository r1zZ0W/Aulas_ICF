import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar/Sidebar';
import './PrivateLayout.css';

/**
 * Shell for all authenticated pages.
 * Renders the Sidebar on the left and the matched route via <Outlet> on the right.
 */
export default function PrivateLayout() {
  return (
    <div className="private-layout">
      <Sidebar />
      <main className="private-layout__content">
        <Outlet />
      </main>
    </div>
  );
}

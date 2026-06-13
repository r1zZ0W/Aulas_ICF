import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar/Sidebar';
import ReservaModal from '../components/ReservaModal/ReservaModal';
import ReservaInfoModal from '../components/ReservaInfoModal/ReservaInfoModal';
import ReasignarModal from '../components/ReasignarModal/ReasignarModal';
import { ReservationProvider, useReservation } from '../context/ReservationContext';
import './PrivateLayout.css';

function Layout() {
  const {
    modalOpen, closeModal, modalSlot,
    infoModalOpen, selectedReservation, closeInfoModal, openReasignar,
    reasignarOpen, closeReasignar,
  } = useReservation();

  return (
    <div className="private-layout">
      <Sidebar />
      <main className="private-layout__content">
        <Outlet />
      </main>

      <ReservaModal
        open={modalOpen}
        onClose={closeModal}
        initialStart={modalSlot.start}
        initialEnd={modalSlot.end}
      />

      <ReservaInfoModal
        open={infoModalOpen}
        onClose={closeInfoModal}
        reservation={selectedReservation}
        onEdit={openReasignar}
      />

      <ReasignarModal
        open={reasignarOpen}
        onClose={closeReasignar}
        reservation={selectedReservation}
      />
    </div>
  );
}

export default function PrivateLayout() {
  return (
    <ReservationProvider>
      <Layout />
    </ReservationProvider>
  );
}

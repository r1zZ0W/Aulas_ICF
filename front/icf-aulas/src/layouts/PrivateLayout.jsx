import { Outlet } from 'react-router-dom';
import { Toaster } from 'sonner';
import Sidebar from '../components/Sidebar/Sidebar';
import ReservaModal from '../components/ReservaModal/ReservaModal';
import ReservaInfoModal from '../components/ReservaInfoModal/ReservaInfoModal';
import ReasignarModal from '../components/ReasignarModal/ReasignarModal';
import { ReservationProvider, useReservation } from '../context/ReservationContext';
import './PrivateLayout.css';

function Layout() {
  const {
    modalOpen, closeModal, modalSlot,
    infoModalOpen, selectedReservation, closeInfoModal, openReschedule,
    rescheduleOpen, closeReschedule,
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
        onEdit={openReschedule}
      />

      <ReasignarModal
        open={rescheduleOpen}
        onClose={closeReschedule}
        reservation={selectedReservation}
      />

      <Toaster position="bottom-right" richColors={false} />
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

import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar/Sidebar';
import ReservaModal from '../components/ReservaModal/ReservaModal';
import ReservaInfoModal from '../components/ReservaInfoModal/ReservaInfoModal';
import ReasignarModal from '../components/ReasignarModal/ReasignarModal';
import ReservaStudentsModal from '../components/ReservaStudentsModal/ReservaStudentsModal';
import { ReservationProvider, useReservation } from '../context/ReservationContext';
import './PrivateLayout.css';

function Layout() {
  const {
    modalOpen, closeModal, modalSlot,
    infoModalOpen, selectedReservation, closeInfoModal, openReschedule,
    rescheduleOpen, closeReschedule,
    studentsModalOpen, closeStudentsModal,
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

      <ReservaStudentsModal
        open={studentsModalOpen}
        onClose={closeStudentsModal}
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

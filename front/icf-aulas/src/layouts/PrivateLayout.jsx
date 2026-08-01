import { lazy, Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import Sidebar from '../components/Sidebar/Sidebar';
import { ReservationProvider, useReservation } from '../context/ReservationContext';
import './PrivateLayout.css';

// Lazy + conditionally rendered (not just `open={false}`): each of these modals mounts
// its own useQuery calls (e.g. ReservaModal → /semesters/active, ReasignarModal →
// /timeslots) that would otherwise fire on every private page even while the modal is
// closed. Modal.jsx already no-ops on `open=false`, so gating the mount itself costs
// nothing visually — it just stops the eager network/chunk fetch on every page.
const ReservaModal = lazy(() => import('../components/ReservaModal/ReservaModal'));
const ReservaInfoModal = lazy(() => import('../components/ReservaInfoModal/ReservaInfoModal'));
const ReasignarModal = lazy(() => import('../components/ReasignarModal/ReasignarModal'));
const ReservaStudentsModal = lazy(() => import('../components/ReservaStudentsModal/ReservaStudentsModal'));

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

      {/*
        Local Suspense boundary: the nearest one otherwise is App.jsx's, whose fallback is a
        full-screen LoadingOverlay. Without this, opening any modal for the first time (chunk
        not yet fetched) would blank the entire page — sidebar and calendar included — instead
        of just the modal not appearing yet. fallback={null} means "nothing" for that brief gap,
        which is preferable to wiping the page behind it.
      */}
      <Suspense fallback={null}>
        {modalOpen && (
          <ReservaModal
            open={modalOpen}
            onClose={closeModal}
            initialStart={modalSlot.start}
            initialEnd={modalSlot.end}
          />
        )}

        {infoModalOpen && (
          <ReservaInfoModal
            open={infoModalOpen}
            onClose={closeInfoModal}
            reservation={selectedReservation}
            onEdit={openReschedule}
          />
        )}

        {rescheduleOpen && (
          <ReasignarModal
            open={rescheduleOpen}
            onClose={closeReschedule}
            reservation={selectedReservation}
          />
        )}

        {studentsModalOpen && (
          <ReservaStudentsModal
            open={studentsModalOpen}
            onClose={closeStudentsModal}
            reservation={selectedReservation}
          />
        )}
      </Suspense>
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

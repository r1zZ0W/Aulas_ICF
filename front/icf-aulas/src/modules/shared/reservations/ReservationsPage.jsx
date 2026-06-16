import CalendarView from '../../../components/Calendar/CalendarView';

export default function ReservationsPage() {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
      <CalendarView />
    </div>
  );
}
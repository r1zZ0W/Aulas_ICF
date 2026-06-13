import { X, Info, Users, Monitor, Cast, Clock, Pencil, Repeat } from 'lucide-react';
import Modal from '../Modal/Modal';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../utils/roles';
import { SALA_BY_ID } from '../../utils/salas';
import '../ReservaModal/ReservaModal.css';
import './ReservaInfoModal.css';

function fmtTime(date) {
  if (!date) return '—';
  const d = date instanceof Date ? date : new Date(date);
  return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export default function ReservaInfoModal({ open, onClose, reservation, onEdit }) {
  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.ADMIN;

  if (!open || !reservation) return null;

  const sala = SALA_BY_ID[reservation.salaId];

  return (
    <Modal open={open} className="reserva-info-modal">
      <div className="reserva-modal__inner">

        <header className="reserva-modal__header">
          <h2 className="reserva-modal__title">Información de la reserva</h2>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        <div className="reserva-modal__body">
          {reservation.recurrente && (
            <div className="reserva-info-modal__recurrente-badge">
              <Repeat size={14} />
              <span>Recurrente todo el semestre</span>
            </div>
          )}

          <div className="reserva-modal__field">
            <label className="reserva-modal__label">Nombre de la clase*</label>
            <input
              type="text"
              className="reserva-modal__input reserva-info-modal__readonly"
              value={reservation.title}
              readOnly
            />
          </div>

          {sala && (
            <div className="reserva-modal__sala-card">
              <div className="reserva-modal__sala-card-header">
                <Info size={16} />
                <span>Información del aula: {sala.label}</span>
              </div>
              <div className="reserva-modal__sala-card-details">
                <span><Users size={15} /> Capacidad: {sala.capacidad} personas</span>
                <span><Monitor size={15} /> Computadoras: {sala.computadoras}</span>
                <span><Cast size={15} /> Proyectores: {sala.proyectores}</span>
              </div>
            </div>
          )}

          <div className="reserva-modal__row">
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Inicio*</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <input
                  type="text"
                  className="reserva-modal__select reserva-modal__select--time reserva-info-modal__readonly"
                  value={fmtTime(reservation.start)}
                  readOnly
                />
              </div>
            </div>
            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Fin*</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <input
                  type="text"
                  className="reserva-modal__select reserva-modal__select--time reserva-info-modal__readonly"
                  value={fmtTime(reservation.end)}
                  readOnly
                />
              </div>
            </div>
          </div>
        </div>

        <footer className="reserva-modal__footer">
          <button
            type="button"
            className="reserva-modal__btn reserva-modal__btn--cancel"
            onClick={onClose}
          >
            {isAdmin ? 'Cancelar' : 'Cerrar'}
          </button>
          {isAdmin && (
            <button
              type="button"
              className="reserva-modal__btn reserva-modal__btn--submit"
              onClick={onEdit}
            >
              <Pencil size={18} />
              Editar
            </button>
          )}
        </footer>
      </div>
    </Modal>
  );
}

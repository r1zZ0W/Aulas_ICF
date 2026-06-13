import { useState, useEffect } from 'react';
import { X, Info, Users, Monitor, Cast, Clock, ChevronDown, Plus } from 'lucide-react';
import Modal from '../Modal/Modal';
import { useReservation } from '../../context/ReservationContext';
import { SALAS, SALA_BY_ID } from '../../utils/salas';
import '../ReservaModal/ReservaModal.css';
import './ReasignarModal.css';

const toMins = (h, m) => h * 60 + m;
const fmt = (h, m) => `${h}:${String(m).padStart(2, '0')}`;

function getAllStartSlots() {
  const slots = [];
  for (let h = 7; h <= 19; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 19 && m === 30) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

function getEndSlots(startH, startM) {
  const startMins = toMins(startH, startM);
  const slots = [];
  for (let h = 7; h <= 20; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 20 && m > 0) break;
      if (toMins(h, m) <= startMins) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

function timeToLabel(date) {
  if (!date) return '';
  const d = date instanceof Date ? date : new Date(date);
  return fmt(d.getHours(), d.getMinutes() >= 30 ? 30 : 0);
}

const START_SLOTS = getAllStartSlots();

export default function ReasignarModal({ open, onClose, reservation }) {
  const { visibleSalas, updateReservation } = useReservation();
  const availableSalas = SALAS.filter(s => visibleSalas.has(s.id));

  const [salaId, setSalaId] = useState('');
  const [startLabel, setStartLabel] = useState('');
  const [endLabel, setEndLabel] = useState('');

  useEffect(() => {
    if (!open || !reservation) return;
    setSalaId(String(reservation.salaId ?? ''));
    setStartLabel(timeToLabel(reservation.start));
    setEndLabel(timeToLabel(reservation.end));
  }, [open, reservation]);

  const startSlot = START_SLOTS.find(s => s.label === startLabel) ?? null;
  const endSlots = startSlot ? getEndSlots(startSlot.h, startSlot.m) : [];
  const sala = SALAS.find(s => String(s.id) === salaId) ?? null;

  const handleSalaChange = val => setSalaId(val);;

  const handleStartChange = val => {
    setStartLabel(val);
    const slot = START_SLOTS.find(s => s.label === val);
    if (!slot) return;
    const eSlots = getEndSlots(slot.h, slot.m);
    if (!eSlots.find(s => s.label === endLabel)) setEndLabel(eSlots[0]?.label ?? '');
  };

  const canSubmit =
    Boolean(salaId) &&
    Boolean(startLabel) &&
    Boolean(endLabel)

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!canSubmit || !reservation) return;

    const [sh, sm] = startLabel.split(':').map(Number);
    const [eh, em] = endLabel.split(':').map(Number);
    const base = reservation.start instanceof Date
      ? reservation.start
      : new Date(reservation.start);

    const newStart = new Date(base.getFullYear(), base.getMonth(), base.getDate(), sh, sm, 0);
    const newEnd = new Date(base.getFullYear(), base.getMonth(), base.getDate(), eh, em, 0);

    updateReservation(reservation.id, {
      salaId: Number(salaId),
      start: newStart,
      end: newEnd,
    });
    onClose();
  };

  return (
    <Modal open={open} className="reasignar-modal">
      <div className="reserva-modal__inner">

        <header className="reserva-modal__header">
          <h2 className="reserva-modal__title">Reasignar Aula</h2>
          <button
            type="button"
            className="reserva-modal__close"
            onClick={onClose}
            aria-label="Cerrar"
          >
            <X size={20} strokeWidth={2.5} />
          </button>
        </header>

        <form onSubmit={handleSubmit} className="reserva-modal__body" noValidate>

          <div className="reserva-modal__field">
            <label className="reserva-modal__label">Seleccionar Sala*</label>
            <div className="reserva-modal__select-wrap">
              <select
                className="reserva-modal__select"
                value={salaId}
                onChange={e => handleSalaChange(e.target.value)}
                required
              >
                <option value="" />
                {availableSalas.map(s => (
                  <option key={s.id} value={s.id}>{s.label}</option>
                ))}
              </select>
              <ChevronDown size={18} className="reserva-modal__chevron" />
            </div>
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
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={startLabel}
                  onChange={e => handleStartChange(e.target.value)}
                  required
                >
                  {START_SLOTS.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
            </div>

            <div className="reserva-modal__field">
              <label className="reserva-modal__label">Hora de Fin*</label>
              <div className="reserva-modal__select-wrap reserva-modal__select-wrap--time">
                <Clock size={16} className="reserva-modal__time-icon" />
                <select
                  className="reserva-modal__select reserva-modal__select--time"
                  value={endLabel}
                  onChange={e => setEndLabel(e.target.value)}
                  required
                  disabled={!startLabel || endSlots.length === 0}
                >
                  {endSlots.map(s => (
                    <option key={s.label} value={s.label}>{s.label}</option>
                  ))}
                </select>
                <ChevronDown size={18} className="reserva-modal__chevron" />
              </div>
            </div>
          </div>

          <footer className="reserva-modal__footer">
            <button
              type="button"
              className="reserva-modal__btn reserva-modal__btn--cancel"
              onClick={onClose}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className="reserva-modal__btn reserva-modal__btn--submit"
              disabled={!canSubmit}
            >
              <Plus size={20} />
              Reservar Aula
            </button>
          </footer>
        </form>
      </div>
    </Modal>
  );
}

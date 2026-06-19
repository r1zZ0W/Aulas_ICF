import Input from '../../../components/Input/Input';
import Select from '../../../components/Select/Select';
import { CLASSROOM_TYPES } from '../../../schemas/classroom';

const STATUS_OPTIONS = [
  { value: 'true',  label: 'Disponible' },
  { value: 'false', label: 'No disponible' },
];

/**
 * Shared form body for the create and edit classroom modals.
 * Receives controlled state from useClassroomsForm — no internal state.
 *
 * @param {object}   props
 * @param {'create'|'edit'} props.mode
 * @param {object}   props.form           - Current form state.
 * @param {function} props.onField        - (field: string, value: any) => void
 * @param {Array<{value: string, label: string}>} [props.parentOptions=[]]
 *   Options for the "Aula padre" selector. Computed by the parent component
 *   using `buildParentOptions` — already excludes the current aula and its
 *   descendants to prevent cycles.
 */
export default function ClassroomFormFields({ mode, form, onField, parentOptions = [] }) {
  const isCreate = mode === 'create';

  return (
    <div className="classrooms-page__form-grid">
      <Input
        label="Nombre"
        value={form.name}
        onChange={(e) => onField('name', e.target.value)}
        placeholder="Ej. Aula Magna A"
        required
      />

      <Input
        label="Capacidad"
        type="number"
        min={1}
        max={500}
        value={form.capacity}
        onChange={(e) => onField('capacity', Number(e.target.value))}
        required
      />

      <div className="classrooms-page__form-grid--full">
        <Select
          label="Tipo"
          value={form.type}
          onChange={(v) => onField('type', v)}
          options={CLASSROOM_TYPES}
          required
        />
      </div>

      <div className="classrooms-page__form-grid--full">
        <Input
          label="Descripción"
          value={form.description ?? ''}
          onChange={(e) => onField('description', e.target.value)}
          placeholder="Descripción opcional del aula (máx. 500 caracteres)"
        />
      </div>

      {/* Aula padre — shown only when options are available (catalog loaded) */}
      {parentOptions.length > 0 && (
        <div className="classrooms-page__form-grid--full">
          <Select
            label="Aula vinculada (padre)"
            value={form.linkedRoomUuid ?? ''}
            onChange={(v) => onField('linkedRoomUuid', v || null)}
            options={parentOptions}
          />
          <span className="classrooms-page__form-help">
            Si esta aula forma parte de otra (ej. un bloque o edificio), selecciona el aula padre.
          </span>
        </div>
      )}

      {!isCreate && (
        <Select
          label="Estado"
          value={String(form.isActive)}
          onChange={(v) => onField('isActive', v === 'true')}
          options={STATUS_OPTIONS}
        />
      )}
    </div>
  );
}

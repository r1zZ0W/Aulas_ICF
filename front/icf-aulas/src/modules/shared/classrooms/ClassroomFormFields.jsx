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
 * @param {object}   props.form           - Current form state (incl. `childUuids` in edit mode).
 * @param {function} props.onField        - (field: string, value: any) => void
 * @param {Array<{value: string, label: string}>} [props.parentOptions=[]]
 *   Options for the "Aula padre" selector, already filtered to exclude cycles.
 * @param {Array<{uuid: string, name: string, linkedRoomUuid?: string|null}>} [props.childOptions=[]]
 *   Eligible classrooms for the "Aulas hijas" multi-checkbox (edit mode only).
 *   Filtered by buildChildOptions to exclude self and ancestors. Active only.
 */
export default function ClassroomFormFields({
  mode,
  form,
  onField,
  parentOptions = [],
  childOptions  = [],
}) {
  const isCreate = mode === 'create';

  function toggleChild(uuid, checked) {
    const next = checked
      ? [...(form.childUuids ?? []), uuid]
      : (form.childUuids ?? []).filter((u) => u !== uuid);
    onField('childUuids', next);
  }

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

      {/* Aula padre — a qué aula pertenece o está contenida esta */}
      {parentOptions.length > 0 && (
        <div className="classrooms-page__form-grid--full">
          <Select
            label="Aula padre (a la que pertenece esta aula)"
            value={form.linkedRoomUuid ?? ''}
            onChange={(v) => onField('linkedRoomUuid', v || null)}
            options={parentOptions}
          />
          <span className="classrooms-page__form-help">
            Selecciona si esta aula está físicamente dentro de otra (ej. un laboratorio dentro
            de un edificio). Deja en blanco si es independiente.
          </span>
        </div>
      )}

      {/* Aulas hijas — qué aulas contiene esta (solo en modo edición) */}
      {!isCreate && childOptions.length > 0 && (
        <div className="classrooms-page__form-grid--full">
          <fieldset className="classroom-children">
            <legend className="classroom-children__legend">
              Aulas hijas (contenidas en esta)
              {(form.childUuids?.length ?? 0) > 0 && (
                <span className="classroom-children__count">
                  {' '}— {form.childUuids.length} seleccionada{form.childUuids.length !== 1 ? 's' : ''}
                </span>
              )}
            </legend>
            <p className="classrooms-page__form-help" style={{ marginTop: 2, marginBottom: 8 }}>
              Marca las aulas que pertenecen a o están contenidas dentro de esta.
              Los cambios se aplicarán al guardar el formulario.
            </p>
            <div className="classroom-children__list">
              {childOptions.map((c) => {
                const isChecked = (form.childUuids ?? []).includes(c.uuid);
                const hasDifferentParent =
                  c.linkedRoomUuid && c.linkedRoomUuid !== (form.uuid ?? '');
                return (
                  <label key={c.uuid} className="classroom-children__item">
                    <input
                      type="checkbox"
                      className="classroom-children__checkbox"
                      checked={isChecked}
                      onChange={(e) => toggleChild(c.uuid, e.target.checked)}
                    />
                    <span className="classroom-children__name">{c.name}</span>
                    {hasDifferentParent && !isChecked && (
                      <span className="classroom-children__hint">(tiene otro padre)</span>
                    )}
                  </label>
                );
              })}
            </div>
          </fieldset>
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

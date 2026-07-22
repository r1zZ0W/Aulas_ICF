import Input from '../../../components/Input/Input';

/**
 * Shared form body for the create and edit global resource modals.
 * Receives controlled state from useResourcesForm — no internal state.
 *
 * @param {object}   props
 * @param {'create'|'edit'} props.mode
 * @param {object}   props.form    - Current form state ({ name, description, quantity }).
 * @param {function} props.onField - (field: string, value: any) => void
 * @param {object}   props.errors  - Zod-derived field error map.
 */
export default function ResourceFormFields({ mode, form, onField, errors }) {
  const isCreate = mode === 'create';

  return (
    <div className="resources-page__form-grid">
      <div className="resources-page__form-grid--full">
        <Input
          label="Nombre del Recurso"
          value={form.name}
          onChange={(e) => onField('name', e.target.value)}
          placeholder={isCreate ? 'Ej. Proyector Epson' : undefined}
          error={errors.name}
          required
        />
      </div>

      <div className="resources-page__form-grid--full">
        <Input
          label="Descripción"
          value={form.description ?? ''}
          onChange={(e) => onField('description', e.target.value)}
          placeholder="Ej. Proyector 4K para aulas grandes"
          error={errors.description}
        />
      </div>

      <Input
        label="Cantidad Total"
        type="number"
        min={1}
        value={form.quantity}
        onChange={(e) => onField('quantity', e.target.value)}
        error={errors.quantity}
        required
      />
    </div>
  );
}

/**
 * Stateless form body for the semester create/edit modal.
 * Receives controlled state from useSemestersForm — no internal state.
 *
 * Date inputs use native `type="date"` whose value is already "YYYY-MM-DD",
 * which matches the SemesterRequestSchema format exactly — no conversion needed.
 *
 * @param {object}   props
 * @param {object}   props.form        - { name, startDate, endDate }
 * @param {function} props.onField     - (field: string, value: string) => void
 * @param {function} [props.onBlurField] - (field: string) => void — triggers real-time validation.
 * @param {object}   [props.errors]    - { name?, startDate?, endDate? }
 */
import Input from '../../../components/Input/Input';

export default function SemesterFormFields({ form, onField, onBlurField, errors = {} }) {
  return (
    <div className="semester-form__grid">
      <div className="semester-form__grid--full">
        <Input
          label="Nombre del semestre"
          placeholder="Ej. 2026-2"
          value={form.name}
          onChange={(e) => onField('name', e.target.value)}
          onBlur={() => onBlurField?.('name')}
          error={errors.name}
          required
        />
      </div>

      <Input
        label="Fecha de inicio"
        type="date"
        value={form.startDate}
        onChange={(e) => onField('startDate', e.target.value)}
        onBlur={() => onBlurField?.('startDate')}
        error={errors.startDate}
        required
      />

      <Input
        label="Fecha de fin"
        type="date"
        value={form.endDate}
        onChange={(e) => onField('endDate', e.target.value)}
        onBlur={() => onBlurField?.('endDate')}
        error={errors.endDate}
        required
      />
    </div>
  );
}

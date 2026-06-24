import Input from '../../../components/Input/Input';
import Select from '../../../components/Select/Select';

const STATUS_OPTIONS = [
  { value: 'true', label: 'Activo' },
  { value: 'false', label: 'Inactivo' },
];

/**
 * Shared form body for the create and edit user modals.
 *
 * @param {object}   props
 * @param {'create'|'edit'} props.mode
 * @param {object}   props.form        - Current form state.
 * @param {function} props.onField     - (field, value) => void
 * @param {object}   props.errors      - Zod-derived field error map.
 * @param {Array<{value: string, label: string}>} props.roleOptions
 */
export default function UserFormFields({ mode, form, onField, errors, roleOptions }) {
  const isCreate = mode === 'create';

  return (
    <div className="users-page__form-grid">
      <Input
        label="Nombre(s)"
        value={form.firstName}
        onChange={(e) => onField('firstName', e.target.value)}
        placeholder={isCreate ? 'Ej. María' : undefined}
        error={errors.firstName}
        required
      />
      <Input
        label="Apellidos"
        value={form.lastNames}
        onChange={(e) => onField('lastNames', e.target.value)}
        placeholder={isCreate ? 'Ej. García López' : undefined}
        error={errors.lastNames}
        required
      />
      <Input
        label="Nombre de usuario"
        value={form.username}
        onChange={(e) => onField('username', e.target.value)}
        placeholder={isCreate ? 'Ej. mgarcia' : undefined}
        error={errors.username}
        required
      />
      <Input
        label={isCreate ? 'Correo institucional' : 'Correo electrónico'}
        type="email"
        value={form.email}
        onChange={(e) => onField('email', e.target.value)}
        placeholder={isCreate ? 'usuario@icf.unam.mx' : undefined}
        error={errors.email}
        required
      />

      {isCreate ? (
        <Input
          label="Contraseña"
          type="password"
          value={form.password}
          onChange={(e) => onField('password', e.target.value)}
          placeholder="Mín. 8 caracteres"
          error={errors.password}
          required
        />
      ) : (
        <Select
          label="Estado"
          value={String(form.isActive)}
          onChange={(v) => onField('isActive', v === 'true')}
          options={STATUS_OPTIONS}
          error={errors.isActive}
        />
      )}

      <div className="users-page__form-grid--full">
        <Select
          label="Rol"
          value={String(form.roleId)}
          onChange={(v) => onField('roleId', v)}
          options={roleOptions}
          placeholder="Seleccionar rol..."
          error={errors.roleId}
          required
        />
      </div>
    </div>
  );
}

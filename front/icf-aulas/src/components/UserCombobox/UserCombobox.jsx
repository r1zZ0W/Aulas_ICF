import AsyncCombobox from '../AsyncCombobox/AsyncCombobox';
import { getUsers } from '../../api/users';

/**
 * Thin, semantic wrapper over {@link AsyncCombobox} for searching and picking a user
 * (teacher or admin) by name or email. Used by the admin-only "Reservar para otro
 * usuario" flow in `ReservaModal`.
 *
 * Reuses `getUsers` (`src/api/users.js`) as-is: it is already admin-only on the
 * backend and already filters by name/email/username/matrícula. It always excludes
 * the authenticated admin's own row — intentional, since booking for oneself just
 * means leaving the toggle off (the existing default behaviour).
 *
 * @param {{
 *   value:       object|null,
 *   onChange:    (user: object|null) => void,
 *   placeholder?: string,
 *   disabled?:   boolean,
 * }} props
 */
export default function UserCombobox({ value, onChange, placeholder, disabled }) {
  return (
    <AsyncCombobox
      value={value}
      onChange={onChange}
      disabled={disabled}
      placeholder={placeholder ?? 'Buscar por nombre o correo…'}
      queryFn={(search) => getUsers({ search, size: 8 }).then((r) => r.items)}
      getLabel={(u) => `${u.firstName} ${u.lastNames}`.trim()}
      getSecondaryLabel={(u) => u.email}
      getBadge={(u) => (u.roleName === 'ADMIN' ? 'Administrador' : 'Maestro')}
      getValue={(u) => u.uuid}
    />
  );
}

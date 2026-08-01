/**
 * @fileoverview Single source of truth for the Historial de Reservas "Estado" filter.
 *
 * The URL carries exactly one param — `status` — holding a *selection key* from the table
 * below (not a raw `ReservInstanceStatus`). This module is the only place that translates a
 * selection key into the actual API params (`status`, `reassigned`, `timeframe`) and into the
 * label shown in the dropdown, so the mapping can never drift between what the user sees and
 * what the server is asked for.
 *
 * | key                  | label (maestro)      | label (admin)         | API params                                  |
 * |----------------------|----------------------|------------------------|----------------------------------------------|
 * | ''                   | Todos los estados     | Todos los estados      | (none)                                        |
 * | ACTIVE               | Activa                | Activa                 | status=ACTIVE, reassigned=false, timeframe=UPCOMING |
 * | REASSIGNED           | Reasignada            | Reasignada              | status=ACTIVE, reassigned=true, timeframe=UPCOMING  |
 * | FINISHED             | Finalizada            | Finalizada              | status=ACTIVE, timeframe=PAST                 |
 * | CANCELLED            | Cancelada             | Cancelada (todas)       | status=[CANCELLED_BY_USER, CANCELLED_BY_ADMIN]|
 * | CANCELLED_BY_USER    | (not offered)         | Cancelada por maestro   | status=CANCELLED_BY_USER                      |
 * | CANCELLED_BY_ADMIN   | (not offered)         | Cancelada por admin     | status=CANCELLED_BY_ADMIN                     |
 *
 * `CANCELLED` (both statuses, generic badge) is the one deliberate exception to "each option's
 * label matches exactly what it returns": a maestro only cares that a class was cancelled, not
 * who did it, so `reservationBadge()` still distinguishes CANCELLED_BY_USER vs
 * CANCELLED_BY_ADMIN case by case even though the filter groups them.
 */
import { ROLES } from '../../../utils/roles.js';

/**
 * @typedef {'' | 'ACTIVE' | 'REASSIGNED' | 'FINISHED' | 'CANCELLED' | 'CANCELLED_BY_USER' | 'CANCELLED_BY_ADMIN'} StatusFilterKey
 */

const ENTRIES = {
  '': {
    labels: { [ROLES.ADMIN]: 'Todos los estados', [ROLES.MAESTRO]: 'Todos los estados' },
    params: {},
  },
  ACTIVE: {
    labels: { [ROLES.ADMIN]: 'Activa', [ROLES.MAESTRO]: 'Activa' },
    params: { status: 'ACTIVE', reassigned: false, timeframe: 'UPCOMING' },
  },
  REASSIGNED: {
    labels: { [ROLES.ADMIN]: 'Reasignada', [ROLES.MAESTRO]: 'Reasignada' },
    params: { status: 'ACTIVE', reassigned: true, timeframe: 'UPCOMING' },
  },
  FINISHED: {
    labels: { [ROLES.ADMIN]: 'Finalizada', [ROLES.MAESTRO]: 'Finalizada' },
    params: { status: 'ACTIVE', timeframe: 'PAST' },
  },
  CANCELLED: {
    labels: { [ROLES.ADMIN]: 'Cancelada (todas)', [ROLES.MAESTRO]: 'Cancelada' },
    params: { status: ['CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'] },
  },
  CANCELLED_BY_USER: {
    labels: { [ROLES.ADMIN]: 'Cancelada por maestro' },
    params: { status: 'CANCELLED_BY_USER' },
  },
  CANCELLED_BY_ADMIN: {
    labels: { [ROLES.ADMIN]: 'Cancelada por admin' },
    params: { status: 'CANCELLED_BY_ADMIN' },
  },
};

/** Keys every role may select, in dropdown display order. */
const KEYS_BY_ROLE = {
  [ROLES.ADMIN]: ['', 'ACTIVE', 'REASSIGNED', 'FINISHED', 'CANCELLED', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'],
  [ROLES.MAESTRO]: ['', 'ACTIVE', 'REASSIGNED', 'FINISHED', 'CANCELLED'],
};

/**
 * Returns the `{ value, label }` options for the given role's dropdown, in display order.
 *
 * @param {string} role - `ROLES.ADMIN` or `ROLES.MAESTRO`.
 * @returns {{ value: StatusFilterKey, label: string }[]}
 */
export function optionsFor(role) {
  const keys = KEYS_BY_ROLE[role] ?? KEYS_BY_ROLE[ROLES.MAESTRO];
  return keys.map((key) => ({ value: key, label: ENTRIES[key].labels[role] ?? ENTRIES[key].labels[ROLES.ADMIN] }));
}

/**
 * Translates a selection key into the API params it represents.
 * Unknown keys return `{}` (no restriction) — callers should normalize with
 * {@link normalizeKey} first so an unknown key never reaches here from raw user input.
 *
 * @param {string} key
 * @returns {{ status?: string|string[], reassigned?: boolean, timeframe?: string }}
 */
export function toQueryParams(key) {
  return ENTRIES[key]?.params ?? {};
}

/**
 * Sanitizes a raw URL value against the keys valid for the given role.
 * Called at render time (not in a `useEffect`) so a bad or role-inappropriate value never
 * reaches the API for even one render cycle — see HistoryPage's render-phase sanitization.
 *
 * @param {string|null|undefined} rawValue - The raw `status` query param from the URL.
 * @param {string} role - `ROLES.ADMIN` or `ROLES.MAESTRO`.
 * @returns {StatusFilterKey} `rawValue` if valid for the role, otherwise `''`.
 */
export function normalizeKey(rawValue, role) {
  const keys = KEYS_BY_ROLE[role] ?? KEYS_BY_ROLE[ROLES.MAESTRO];
  return keys.includes(rawValue) ? rawValue : '';
}

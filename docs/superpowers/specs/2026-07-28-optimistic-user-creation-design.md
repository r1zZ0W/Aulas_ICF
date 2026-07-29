# Optimistic UI for user creation

**Date:** 2026-07-28
**Status:** Approved

## Problem

Creating a user in the admin panel feels slow: the "Crear Usuario" modal stays
open with a spinner for ~200-500ms while the backend hashes the password with
BCrypt (cost factor 12, `AuthConfig.java`) — a deliberately expensive
operation, unlike every other create action in the app (aulas, reservaciones),
which only run a plain `INSERT` and return near-instantly.

BCrypt's cost cannot and should not be reduced (it's the intended defense
against offline brute-force if the DB ever leaks). The fix is perceptual:
make the UI stop waiting on it.

## Current state (as found)

- `UserService.save()` (`back/aulas`) checks email/username uniqueness via two
  cheap `SELECT`s **before** calling `passwordEncoder.encode(...)` — so a
  duplicate-conflict (409) response is fast (no BCrypt on that path); only the
  success path pays the ~200-500ms BCrypt cost.
- `useUsersForm.handleCreateSubmit` (`front/icf-aulas`) awaits
  `createMutation.mutateAsync(payload)` before closing the modal on success,
  or leaves it open with `setServerErrors` on failure. This is the source of
  the perceived latency.
- `useUsers.js`'s `createMutation` is a plain `useApiMutation` call:
  `invalidateKey: ['users']` (refetches both the paginated list and
  `/users/stats` on success), `successMessage` toasts on success, and the
  hook's built-in `onError` already toasts `err.message` — which is already a
  translated, descriptive string per `errorCatalog.js` (e.g. `USER_EMAIL_TAKEN
  → "Ese correo ya está registrado."`), not a generic "Bad Request". No
  changes needed to that error-message plumbing.
- `UsersPage` renders users via the generic `DataTable` component
  (`rowKey={row => row.uuid}`), sorted `createdAt desc`, paginated
  server-side via `usePagination` (URL-synced `page`/`search`). `DataTable`
  has no concept of per-row styling today.
- `toast.jsx` / `ToastWithProgress.jsx` only support `success`/`error`
  variants (green/red, tied to a specific icon set).

## Decision

1. **Close the create modal immediately on submit**, without waiting for the
   server. Don't reset the form yet — only reset on confirmed success. On
   error, reopen the modal with the form data intact and re-run the existing
   `setServerErrors` field-highlight path.
2. **Insert an optimistic row into the React Query cache**, but only when
   `page === 0 && !search` — the one combination where the new user's
   position (top of the list, `createdAt desc`) is fully deterministic. Any
   other page/filter combination is left untouched to avoid phantom totals or
   rows that don't match the active search.
3. **Do not touch `/users/stats` optimistically.** Stats represent confirmed
   server state; the UX win of bumping them a few hundred ms early is judged
   not worth the risk of a visible flicker back down on error. Stats update
   normally via the existing `invalidateKey: ['users']` refetch on success.
4. **Guard against a stale submission clobbering a newer one** with a
   `useRef` generation counter (`createSessionRef`), bumped only in
   `openCreate()`. A submission's success/error handler only touches
   `createOpen`/form state if the counter still matches what it captured at
   submit time — otherwise it's a no-op (the user has since moved on to a new
   create session). The mutation-level toast still always fires regardless of
   this guard.
5. **Add a delayed "still working" toast** as a safety net for the rare case
   where even the fast duplicate-check path is slow (server under load,
   network latency): a 600ms timer started in `onMutate`, showing `toast.info
   ("Creando usuario…")` only if the mutation hasn't settled by then. 600ms is
   chosen deliberately above BCrypt's normal 200-500ms window, so it does
   **not** fire on the ordinary successful path (which would otherwise stack
   an "in progress" toast right before the "success" one on every create).
   The toast is dismissed by id as soon as the mutation settles (success or
   error), so it's never visible at the same time as the outcome toast.
6. **Add a `toast.info` variant** (blue, spinner icon) instead of reusing
   `success` styling for the "in progress" message — semantically a
   green "success" toast followed by another green toast reads as two
   successive successes, which is misleading. This also leaves the toast
   library ready for future "operation in progress" cases (PDF export,
   report generation, etc.).
7. **Add a generic `rowClassName(row)` prop to `DataTable`** rather than
   hardcoding a "pending user" concept into the shared component. `UsersPage`
   uses it to dim the optimistic row (`row.pending === true`) until it's
   reconciled with the real server row.

## Changes

### `front/icf-aulas/src/utils/toast.jsx`
- `toast(...)` returns the id `sonnerToast.custom(...)` gives back (currently
  discarded).
- Add `toast.info(message, duration)`.
- Add `toast.dismiss(id)`.

### `front/icf-aulas/src/components/ToastWithProgress/ToastWithProgress.jsx` + `.css`
- Add an `info` branch: `Loader2` icon (lucide-react) with a CSS spin
  animation, in place of the `success` image icon or the `error`
  `AlertCircle`.
- Add `.toast-progress--info` styling (blue border/icon/message/bar),
  mirroring how `--error` overrides the base styles today.

### `front/icf-aulas/src/components/DataTable/DataTable.jsx`
- Add optional prop `rowClassName?: (row) => string | undefined`, appended to
  each `<tr>`'s existing className. No other behavior changes.

### `front/icf-aulas/src/modules/admin/users/hooks/useUsers.js`
- `createMutation` gains:
  - `onMutate(payload)`: cancel in-flight list query; snapshot the current
    list page; if `page === 0 && !search`, build an optimistic row (temp
    `uuid: "optimistic-" + crypto.randomUUID()`, fields from `payload`,
    `roleName` resolved from the already-loaded `roles` array — defaulting to
    `'TEACHER'` when `payload.roleId` is absent, matching the backend's
    default-role rule — `pending: true`) and prepend it to the cached list
    (re-slicing to `size`, bumping `totalElements`); start the 600ms
    "Creando usuario…" timer.
  - `onError(err, payload, context)`: clear the timer/dismiss the info toast;
    restore the snapshot taken in `onMutate`.
  - `onSuccess(data, payload, context)`: clear the timer/dismiss the info
    toast. (Cache reconciliation itself needs no new code — the existing
    `invalidateKey: ['users']` refetch already replaces the optimistic row
    with the real one.)
- No changes to `useApiMutation.js` itself — it already forwards an
  arbitrary `onMutate` through its `...options` spread.

### `front/icf-aulas/src/modules/admin/users/hooks/useUsersForm.js`
- Add `const createSessionRef = useRef(0)`.
- `openCreate()`: increment `createSessionRef.current` before resetting/opening.
- `handleCreateSubmit()`: capture `session = createSessionRef.current` before
  calling `setCreateOpen(false)`; gate the post-await `reset()` (success) and
  `setCreateOpen(true)` + `setServerErrors()` (error) on
  `createSessionRef.current === session`.

### `front/icf-aulas/src/modules/admin/users/UsersPage.jsx`
- Pass `rowClassName={(row) => row.pending ? 'users-page__row--pending' : undefined}`
  to `DataTable`.
- Add `.users-page__row--pending { opacity: 0.6; }` (or similar) to
  `UsersPage.css`.

## Out of scope

- Edit/delete flows — unchanged, keep the existing await-then-close pattern.
  Neither has a fast/slow split like create's duplicate-check-before-hash, so
  optimistic UI there would need separate analysis.
- Any backend change. The BCrypt cost factor and the uniqueness-check-before-hash
  ordering are correct as they are.
- A dedicated fast "check availability" endpoint — rejected as unnecessary
  scope: the existing duplicate check is already fast, we just weren't
  benefiting from that speed on the frontend.

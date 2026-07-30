# Teacher-owner visibility of the student roster

**Date:** 2026-07-30
**Status:** Approved

## Problem

A teacher who owns a reservation cannot currently see the list of students
registered for it — only ADMIN users can. The "Ver lista completa" button in
`ReservaInfoModal` is hidden from them even when they are the owner. The
request is to let a teacher view the roster (and PDF export) of their own
reservations, while other teachers (non-owners) continue to see nothing
beyond the attendee count, exactly as today. Admins keep full access
regardless of ownership.

## Prior context

This exact feature was designed once before (2026-07-28) but never
implemented — the spec file was deleted in an unrelated cleanup commit
(`feat: README`) before the code changes landed. That earlier spec found,
via a repo-wide search, that `GET .../students/pdf` has **no UI consumer for
any role** — no "download PDF" button exists anywhere in the frontend — and
recommended deleting the PDF feature instead of extending its auth. That
recommendation was reconsidered this round: the decision here is to keep the
PDF feature and extend it the same way as the JSON list, in case it's needed
later, rather than delete it now.

## Current state (as found)

- `ReservaInfoModal.jsx` already computes `isOwner` (`user.uuid ===
  reservation.userUuid`) but only uses it to gate the "Cancelar" button. The
  "Ver lista completa" button that opens the student roster modal is gated on
  `isAdmin` alone.
- `GET /api/v1/reservations/groups/{groupUuid}/students` (JSON roster,
  `StudentListController.list`) is `@PreAuthorize("hasRole('ADMIN')")` with no
  ownership branch.
- `GET .../students/pdf` (`StudentListController.downloadPdf`) is likewise
  ADMIN-only, and is not called from the frontend anywhere.
- The `POST .../students` (upload/re-upload) endpoint already implements the
  target pattern: `isAdmin || group.getUser().getUuid().equals(principalUuid)`,
  enforced in `ReservationStudentService.upload`.
- `GET .../students/exists` has no `@PreAuthorize` — open to any authenticated
  user already; unaffected by this change.

## Decision

1. **Grant owner-teacher read access to the JSON roster and the PDF export.**
   Extend both `GET /students` and `GET /students/pdf` with the same
   owner-or-admin check already used by `upload`, instead of the current
   ADMIN-only `@PreAuthorize`.
2. **Leave `GET /students/exists` unchanged.**
3. **Frontend gate the existing "Ver lista completa" button on `isAdmin ||
   isOwner`** instead of `isAdmin` alone. No new UI is built; the roster modal
   (`ReservaStudentsModal`) is already a read-only, role-agnostic display
   component driven entirely by its caller.

## Changes

### Backend (`back/aulas`)

- `StudentListController`
  - `list(...)`: drop `@PreAuthorize("hasRole('ADMIN')")`; add
    `@AuthenticationPrincipal UserDetailsImp principal`; compute `isAdmin`
    the same way `upload` does; pass `principal.getUuid()` and `isAdmin` to
    the service.
  - `downloadPdf(...)`: same change — drop `@PreAuthorize("hasRole('ADMIN')")`,
    add the principal parameter, pass ownership info to the service.
  - Update both methods' Javadoc from "Requires ADMIN role" to describe
    owner-or-admin access.
- `ReservationStudentService`
  - `listStudents(UUID groupUuid, UUID principalUuid, boolean isAdmin)`: add
    the ownership check used in `upload`:
    ```java
    if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
        throw new AccessDeniedException("You can only view the roster of your own reservation groups");
    ```
  - `generatePdf(UUID groupUuid, UUID principalUuid, boolean isAdmin)`: same
    ownership check, added right after the group lookup (before the
    file-not-found check, so a non-owner gets 403 rather than a 404 that
    would leak whether a roster exists).
  - Update class/method Javadoc that currently says "admin-only" to describe
    owner-or-admin access.

### Frontend (`front/icf-aulas`)

- `ReservaInfoModal.jsx`: change the "Ver lista completa" button's guard from
  `isAdmin &&` to `(isAdmin || isOwner) &&`; update the file's visibility-rules
  doc comment accordingly (currently documents "Admin → Ver estudiantes
  always" and "Teacher (owner) → Cancelar only" — needs to add "Ver
  estudiantes" to the owner row).
- `ReservaStudentsModal.jsx`: update the "Admin-only" doc comment to reflect
  owner-or-admin access. No logic changes — already role-agnostic/caller-driven.

## Out of scope

- No new "download PDF" UI is built (none existed before this change, for any
  role) — only the backend auth is relaxed.
- No change to the upload endpoint's existing owner-or-admin logic — it
  already does the right thing and is the pattern being replicated.
- No change to non-owner teachers' visibility: they still see only the
  attendee count, no button, and still get 403 if they hit either endpoint
  directly.

## Testing

- Backend: unit/integration coverage on `ReservationStudentService` /
  `StudentListController` for `listStudents` and `generatePdf`: owner teacher
  can access; non-owner teacher gets 403; admin can access any group;
  unauthenticated is rejected at the filter-chain level.
- Frontend: verify the "Ver lista completa" button renders for the owning
  teacher and stays hidden for a non-owner teacher, and that the roster modal
  loads correctly when opened by an owner.

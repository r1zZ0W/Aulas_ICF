# Teacher-owner visibility of the student roster

**Date:** 2026-07-30
**Status:** Approved

## Problem

A teacher who owns a reservation cannot currently see the list of students
registered for it — only ADMIN users can. The "Ver lista completa" button in
`ReservaInfoModal` is hidden from them even when they are the owner. The
request is to let a teacher view the roster of their own reservations, while
other teachers (non-owners) continue to see nothing beyond the attendee
count, exactly as today. Admins keep full access regardless of ownership.

## Prior context

This exact feature was designed once before (2026-07-28) but never
implemented — the spec file was deleted in an unrelated cleanup commit
(`feat: README`) before the code changes landed. That earlier spec found,
via a repo-wide search, that `GET .../students/pdf` has **no UI consumer for
any role** — no "download PDF" button exists anywhere in the frontend — and
recommended deleting the PDF feature instead of extending its auth. This
round re-confirmed that finding (grep across `back/aulas` and
`front/icf-aulas` turns up no caller of `downloadPdf`/`generatePdf` outside
the PDF stack's own files, and no frontend reference beyond the orphaned
`ROSTER_NOT_FOUND` catalog entry) and adopts the original recommendation:
delete the PDF feature rather than extend it.

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

1. **Grant owner-teacher read access to the JSON roster.** Extend
   `GET /students` with the same owner-or-admin check already used by
   `upload`, instead of the current ADMIN-only `@PreAuthorize`.
2. **Delete the PDF export feature entirely** (endpoint, service method,
   generator classes, dedicated exception/error code, the `openpdf` Maven
   dependency, and the now-dead `ROSTER_NOT_FOUND` frontend error-catalog
   entry) rather than also opening it to owners. It has no UI consumer for
   any role, so extending its auth would only be maintaining dead code.
3. **Leave `GET /students/exists` unchanged.**
4. **Frontend gate the existing "Ver lista completa" button on `isAdmin ||
   isOwner`** instead of `isAdmin` alone. No new UI is built; the roster modal
   (`ReservaStudentsModal`) is already a read-only, role-agnostic display
   component driven entirely by its caller.

## Changes

### Backend (`back/aulas`)

- `StudentListController`
  - `list(...)`: drop `@PreAuthorize("hasRole('ADMIN')")`; add
    `@AuthenticationPrincipal UserDetailsImp principal`; compute `isAdmin`
    the same way `upload` does; pass `principal.getUuid()` and `isAdmin` to
    the service. Update its Javadoc from "Requires ADMIN role" to describe
    owner-or-admin access.
  - `downloadPdf(...)`: **delete** the method and its `/pdf` route, and the
    now-unused imports it pulled in (`ContentDisposition`, `HttpHeaders`).
- `ReservationStudentService`
  - `listStudents(UUID groupUuid, UUID principalUuid, boolean isAdmin)`: add
    the ownership check used in `upload`:
    ```java
    if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
        throw new AccessDeniedException("You can only view the roster of your own reservation groups");
    ```
  - `generatePdf(...)`: **delete**, along with `resolveGroupLabel` if it has
    no other caller after the deletion (currently only used by `generatePdf`).
  - Update class Javadoc (currently describes itself as backing "the
    admin-only 'view students' JSON endpoint") to describe owner-or-admin
    access and drop the PDF references.
- **Delete:** `StudentListPdfGenerator`, `OpenPdfStudentListGenerator`,
  `StudentRosterContext`, `StudentListNotFoundException`.
- `ErrorCode`: remove `ROSTER_NOT_FOUND`.
- `pom.xml`: remove the `openpdf` dependency and its version property.
- Sweep `GlobalExceptionHandler` and any Javadoc `@throws` references to
  `StudentListNotFoundException`/`ROSTER_NOT_FOUND` left dangling by the
  deletion.

### Frontend (`front/icf-aulas`)

- `ReservaInfoModal.jsx`: change the "Ver lista completa" button's guard from
  `isAdmin &&` to `(isAdmin || isOwner) &&`; update the file's visibility-rules
  doc comment accordingly (currently documents "Admin → Ver estudiantes
  always" and "Teacher (owner) → Cancelar only" — needs to add "Ver
  estudiantes" to the owner row).
- `ReservaStudentsModal.jsx`: update the "Admin-only" doc comment to reflect
  owner-or-admin access. No logic changes — already role-agnostic/caller-driven.
- `errors/errorCatalog.js`: remove the now-orphaned `ROSTER_NOT_FOUND` entry.

## Out of scope

- No "download PDF" UI is built — none existed before this change, for any
  role, and the feature is being removed rather than extended.
- No change to the upload endpoint's existing owner-or-admin logic — it
  already does the right thing and is the pattern being replicated.
- No change to non-owner teachers' visibility: they still see only the
  attendee count, no button, and still get 403 if they hit the JSON endpoint
  directly.

## Testing

- Backend: unit/integration coverage on `ReservationStudentService` /
  `StudentListController` for `listStudents`: owner teacher can access;
  non-owner teacher gets 403; admin can access any group; unauthenticated is
  rejected at the filter-chain level. Confirm the project still builds clean
  after the PDF-stack deletion (no dangling references).
- Frontend: verify the "Ver lista completa" button renders for the owning
  teacher and stays hidden for a non-owner teacher, and that the roster modal
  loads correctly when opened by an owner.

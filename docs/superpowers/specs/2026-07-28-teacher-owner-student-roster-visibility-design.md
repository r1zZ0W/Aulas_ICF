# Teacher-owner visibility of the student roster

**Date:** 2026-07-28
**Status:** Approved

## Problem

A teacher who owns a reservation cannot currently see the list of students
registered for it — only ADMIN users can. The request is to let a teacher
view the roster of their own reservations, while other teachers (non-owners)
continue to see nothing beyond the attendee count, exactly as today.

## Current state (as found)

- `ReservaInfoModal.jsx` already computes `isOwner` (`user.uuid ===
  reservation.userUuid`) but only uses it to gate the "Cancelar" button. The
  "Ver lista completa" button that opens the student roster modal is gated on
  `isAdmin` alone.
- `GET /api/v1/reservations/groups/{groupUuid}/students` (JSON roster,
  `StudentListController.list`) is `@PreAuthorize("hasRole('ADMIN')")` with no
  ownership branch.
- `GET .../students/pdf` (`StudentListController.downloadPdf`) is likewise
  ADMIN-only, and is **not called from the frontend anywhere** — no "download
  PDF" button exists in the UI, not even for admins.
- The `POST .../students` (upload/re-upload) endpoint already implements the
  target pattern: `isAdmin || group.getUser().getUuid().equals(principalUuid)`,
  enforced in `ReservationStudentService.upload`.
- The PDF export path (`generatePdf`, `StudentListPdfGenerator` /
  `OpenPdfStudentListGenerator`, `StudentRosterContext`,
  `StudentListNotFoundException`, `ErrorCode.ROSTER_NOT_FOUND`) and the
  `openpdf` Maven dependency exist solely to serve that unused PDF endpoint —
  confirmed via repo-wide search, nothing else references them.

## Decision

1. **Grant owner-teacher read access to the JSON roster.** Extend
   `GET /students` with the same owner-or-admin check already used by
   `upload`, instead of the current ADMIN-only `@PreAuthorize`.
2. **Delete the PDF export feature entirely** (endpoint, service method,
   generator classes, dedicated exception/error code, the `openpdf`
   dependency, and the now-dead `ROSTER_NOT_FOUND` frontend error-catalog
   entry) rather than also opening it to owners. It has no UI consumer today,
   so extending its auth would be maintaining dead code.
3. **Leave `GET /students/exists` unchanged** — already open to any
   authenticated user, not PDF-specific, cheap to keep.
4. **Frontend gate the existing "Ver lista completa" button on `isAdmin ||
   isOwner`** instead of `isAdmin` alone. No new UI is built; the roster modal
   (`ReservaStudentsModal`) is already a read-only, role-agnostic display
   component driven entirely by its caller.

## Changes

### Backend (`back/aulas`)

- `StudentListController`
  - `list(...)`: drop `@PreAuthorize("hasRole('ADMIN')")`; add
    `@AuthenticationPrincipal UserDetailsImp principal`; pass
    `principal.getUuid()` and `isAdmin` to the service.
  - `downloadPdf(...)`: **delete** the method and its `/pdf` route.
  - Javadoc updated to describe owner-or-admin access.
- `ReservationStudentService`
  - `listStudents(UUID groupUuid, UUID principalUuid, boolean isAdmin)`: add
    the ownership check used in `upload`:
    ```java
    if (!isAdmin && !group.getUser().getUuid().equals(principalUuid))
        throw new AccessDeniedException("You can only view the roster of your own reservation groups");
    ```
  - `generatePdf(...)`: **delete**.
- **Delete:** `StudentListPdfGenerator`, `OpenPdfStudentListGenerator`,
  `StudentRosterContext`, `StudentListNotFoundException`.
- `ErrorCode`: remove `ROSTER_NOT_FOUND`.
- `pom.xml`: remove the `openpdf` dependency and `openpdf.version` property.

### Frontend (`front/icf-aulas`)

- `ReservaInfoModal.jsx`: change the "Ver lista completa" button's guard from
  `isAdmin &&` to `(isAdmin || isOwner) &&`; update the file's visibility-rules
  doc comment accordingly.
- `ReservaStudentsModal.jsx`, `ReservationContext.jsx`, `api/reservations.js`
  (`getReservationStudents`): update "admin-only" doc comments to reflect
  owner-or-admin access. No logic changes — these are already
  role-agnostic/caller-driven.
- `errors/errorCatalog.js`: remove the now-orphaned `ROSTER_NOT_FOUND` entry.

## Out of scope

- No new "download PDF" UI is built (none existed before this change, for any
  role).
- No change to the upload endpoint's existing owner-or-admin logic — it
  already does the right thing and is the pattern being replicated.
- No change to non-owner teachers' visibility: they still see only the
  attendee count, no button, no access if they hit the endpoint directly
  (still 403).

## Testing

- Backend: unit/integration test on `StudentListService`/`StudentListController`
  covering: owner teacher can list; non-owner teacher gets 403; admin can
  list any group; unauthenticated is rejected at the filter-chain level.
- Frontend: verify the "Ver lista completa" button renders for the owning
  teacher and stays hidden for a non-owner teacher, and that the roster modal
  loads correctly when opened by an owner.

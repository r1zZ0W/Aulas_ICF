import { useEffect, useMemo, useState, lazy, Suspense } from 'react';
import { Plus, Eye, Pencil, Trash2, Building2, CheckCircle2, XCircle, ToggleLeft, ToggleRight, Boxes } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { ROLES } from '../../../utils/roles';
import { useClassrooms, useAllClassrooms } from './hooks/useClassrooms';
import { useClassroomsForm } from './hooks/useClassroomsForm';
import { usePagination } from '../../../hooks/usePagination';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { typeLabel } from '../../../schemas/classroom';
import { buildParentOptions, buildChildOptions, getChildren } from '../../../utils/classroomTree';

import Button from '../../../components/Button/Button';
import Card from '../../../components/Card/Card';
import Buscador from '../../../components/Buscador/Buscador';
import Select from '../../../components/Select/Select';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner';
import Pagination from '../../../components/Pagination/Pagination';
import ActiveSemesterButton from '../semesters/ActiveSemesterButton';

// ── Lazy-loaded conditional modal components ─────────────────────────────────
const FormModal = lazy(() => import('../../../components/FormModal/FormModal'));
const ConfirmDeleteModal = lazy(() => import('../../../components/ConfirmDeleteModal/ConfirmDeleteModal'));
const ClassroomFormFields = lazy(() => import('./ClassroomFormFields'));
const ClassroomInfoModal = lazy(() => import('./ClassroomInfoModal'));
const ClassroomResourcesModal = lazy(() => import('./ClassroomResourcesModal'));

import './ClassroomsPage.css';

// ── Status filter options ──────────────────────────────────────────────────────
// Client-side filter (all classrooms are fetched from the server for ADMIN).
// TODO: when GET /api/v1/classrooms accepts ?status=, remove client-side filter
// and pass the param to useClassrooms (see classrooms-children-array-request.md §2).
const STATUS_FILTER_OPTIONS = [
  { value: 'all', label: 'Todas' },
  { value: 'active', label: 'Activas' },
  { value: 'inactive', label: 'Inactivas' },
];

// ── Component ──────────────────────────────────────────────────────────────────

export default function ClassroomsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.ADMIN;

  // ── Pagination + search ───────────────────────────────────────────────────────
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300 });

  // ── Client-side status filter (admin sees all, then filters locally) ──────────
  const [statusFilter, setStatusFilter] = useState('all');

  // ── Recursos del aula (modal admin) ────────────────────────────────────────────
  const [resourcesTarget, setResourcesTarget] = useState(null);

  // ── Server state (paginated) ──────────────────────────────────────────────────
  const {
    classrooms: rawClassrooms,
    totalElements,
    totalPages,
    stats,
    loading,
    classroomsError,
    refetchClassrooms,
    createMutation,
    updateMutation,
    deleteMutation,
    toggleStatusMutation,
    setChildrenMutation,
  } = useClassrooms({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name',
    direction: 'asc',
  });

  // ── Modal / form state ────────────────────────────────────────────────────────
  const {
    createOpen, editTarget, viewTarget, deleteTarget,
    form,
    errors,
    openCreate, closeCreate,
    openEdit, openDelete,
    closeEdit,
    openView, closeView,
    setDeleteTarget,
    onField,
    onFieldBlur,
    handleCreateSubmit,
    handleEditSubmit,
  } = useClassroomsForm({
    createMutation,
    updateMutation,
    setChildrenMutation,
  });

  const isAnyModalOpen = Boolean(createOpen || editTarget || viewTarget || deleteTarget || resourcesTarget);

  // ── Full catalog (for parent/child selectors + InfoModal children list) ────────
  const { allClassrooms } = useAllClassrooms({ enabled: isAnyModalOpen });

  // ── Keep the URL page in range after the total shrinks (e.g. deleting the last
  // row of the last page) — otherwise the query keeps requesting a page that no
  // longer exists and the pagination footer shows a broken count.
  useEffect(() => {
    if (!loading && page > 0 && page >= totalPages) {
      setPage(Math.max(0, totalPages - 1));
    }
  }, [loading, page, totalPages, setPage]);

  // ── Apply client-side status filter ──────────────────────────────────────────
  // Only meaningful for admins (teachers already receive active-only from server).
  const classrooms = useMemo(() => {
    if (!isAdmin || statusFilter === 'all') return rawClassrooms;
    if (statusFilter === 'active') return rawClassrooms.filter((r) => r.isActive);
    if (statusFilter === 'inactive') return rawClassrooms.filter((r) => !r.isActive);
    return rawClassrooms;
  }, [rawClassrooms, statusFilter, isAdmin]);

  // ── Parent options (cycles excluded) ─────────────────────────────────────────
  const parentOptions = useMemo(
    () => buildParentOptions(allClassrooms, { excludeUuid: editTarget?.uuid ?? null }),
    [allClassrooms, editTarget]
  );

  // ── Child options (ancestors and self excluded) ───────────────────────────────
  const childOptions = useMemo(
    () => buildChildOptions(allClassrooms, { selfUuid: editTarget?.uuid ?? null }),
    [allClassrooms, editTarget]
  );

  // ── Deactivate modal — count direct children ──────────────────────────────────
  const deleteTargetChildren = useMemo(
    () => (deleteTarget ? getChildren(deleteTarget.uuid, allClassrooms) : []),
    [deleteTarget, allClassrooms]
  );

  // ── Table columns ─────────────────────────────────────────────────────────────
  const columns = [
    {
      key: 'name',
      header: 'Nombre',
      width: '26%',
      render: (r) => r.name,
    },
    {
      key: 'type',
      header: 'Tipo',
      width: '14%',
      render: (r) => typeLabel(r.type),
    },
    {
      key: 'capacity',
      header: 'Capacidad',
      width: '10%',
      render: (r) => r.capacity ?? '—',
    },
    {
      key: 'description',
      header: 'Descripción',
      width: '26%',
      render: (r) => (
        <span
          className="classrooms__description"
          title={r.description || undefined}
        >
          {r.description || '—'}
        </span>
      ),
    },
    {
      key: 'isActive',
      header: 'Estado',
      width: '10%',
      render: (r) => (
        <Badge variant={r.isActive ? 'success' : 'danger'}>
          {r.isActive ? 'Disponible' : 'No disponible'}
        </Badge>
      ),
    },
    {
      key: 'acciones',
      header: 'Acciones',
      width: isAdmin ? '18%' : '8%',
      align: 'right',
      render: (r) => (
        <div className="classrooms__actions">
          {/* Ver información — available to all */}
          <button
            type="button"
            className="classrooms__action-btn"
            title={`Ver información de ${r.name}`}
            aria-label={`Ver información de ${r.name}`}
            onClick={() => openView(r)}
          >
            <Eye size={16} />
          </button>

          {isAdmin && (
            <>
              {/* Editar */}
              <button
                type="button"
                className="classrooms__action-btn"
                title={`Editar ${r.name}`}
                aria-label={`Editar ${r.name}`}
                onClick={() => openEdit(r)}
              >
                <Pencil size={16} />
              </button>

              {/* Recursos del aula */}
              <button
                type="button"
                className="classrooms__action-btn"
                title={`Recursos de ${r.name}`}
                aria-label={`Recursos de ${r.name}`}
                onClick={() => setResourcesTarget(r)}
              >
                <Boxes size={16} />
              </button>

              {/* Toggle status (activate / deactivate) */}
              <button
                type="button"
                className={`classrooms__action-btn ${r.isActive ? 'classrooms__action-btn--warning' : 'classrooms__action-btn--success'}`}
                title={r.isActive ? `Desactivar ${r.name}` : `Activar ${r.name}`}
                aria-label={r.isActive ? `Desactivar ${r.name}` : `Activar ${r.name}`}
                disabled={toggleStatusMutation.isPending}
                onClick={() => toggleStatusMutation.mutateAsync(r.uuid)}
              >
                {r.isActive ? <ToggleRight size={16} /> : <ToggleLeft size={16} />}
              </button>

              {/* Eliminar (always available to admin) */}
              <button
                type="button"
                className="classrooms__action-btn classrooms__action-btn--danger"
                title={`Eliminar ${r.name}`}
                aria-label={`Eliminar ${r.name}`}
                onClick={() => openDelete(r)}
              >
                <Trash2 size={16} />
              </button>
            </>
          )}
        </div>
      ),
    },
  ];

  // ── Render ────────────────────────────────────────────────────────────────────
  return (
    <div className="classrooms-page">

      {/* Header */}
      <div className="classrooms-page__header">
        <div>
          <h1 className="classrooms-page__title">
            {isAdmin ? 'Gestión de Aulas' : 'Catálogo de Aulas'}
          </h1>
          <p className="classrooms-page__subtitle">
            {isAdmin
              ? 'Administra las aulas y su disponibilidad'
              : 'Consulta las aulas disponibles para reservación'}
          </p>
        </div>

        <div className="classrooms-page__header-actions">
          {/* Semester split-button — visible to all; edit/create gated by isAdmin inside */}
          <ActiveSemesterButton isAdmin={isAdmin} />

          {isAdmin && (
            <Button
              variant="primary"
              size="small"
              iconLeft={<Plus size={18} />}
              onClick={openCreate}
            >
              Nueva Aula
            </Button>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="classrooms-page__stats">
        <Card className="classrooms-page__stat-card">
          <span className="classrooms-page__stat-icon classrooms-page__stat-icon--blue">
            <Building2 size={24} />
          </span>
          <div>
            <p className="classrooms-page__stat-label">Total de aulas</p>
            <p className="classrooms-page__stat-value">{totalElements}</p>
          </div>
        </Card>

        <Card className="classrooms-page__stat-card">
          <span className="classrooms-page__stat-icon classrooms-page__stat-icon--green">
            <CheckCircle2 size={24} />
          </span>
          <div>
            <p className="classrooms-page__stat-label">Disponibles</p>
            <p className="classrooms-page__stat-value">{stats?.available ?? '—'}</p>
          </div>
        </Card>

        <Card className="classrooms-page__stat-card">
          <span className="classrooms-page__stat-icon classrooms-page__stat-icon--red">
            <XCircle size={24} />
          </span>
          <div>
            <p className="classrooms-page__stat-label">No disponibles</p>
            <p className="classrooms-page__stat-value">{stats?.notAvailable ?? '—'}</p>
          </div>
        </Card>
      </div>

      {/* Table card */}
      <div className="classrooms-page__table-card">
        {classroomsError && (
          <ErrorBanner
            message="No se pudo cargar la lista de aulas."
            onDismiss={() => refetchClassrooms()}
          />
        )}

        {/* Toolbar */}
        <div className="classrooms-page__table-toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar aulas por nombre o descripción..."
            style={{ maxWidth: 448 }}
          />
          {/* Status filter (admin only — teachers only see active, no filter needed) */}
          {isAdmin && (
            <Select
              value={statusFilter}
              onChange={setStatusFilter}
              options={STATUS_FILTER_OPTIONS}
              style={{ minWidth: 130 }}
              aria-label="Filtrar por estado"
            />
          )}
        </div>

        {/* Table */}
        <DataTable
          columns={columns}
          rows={classrooms}
          rowKey={(r) => r.uuid}
          loading={loading}
          loadingMessage="Cargando aulas…"
          emptyState={
            <EmptyState
              hasSearch={!!search}
              message="No hay aulas registradas"
              searchMessage="No se encontraron aulas que coincidan con la búsqueda."
              actionLabel={isAdmin ? 'Nueva Aula' : undefined}
              onAction={isAdmin ? openCreate : undefined}
            />
          }
        />

        {/* Pagination */}
        {!loading && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            pageSize={DEFAULT_PAGE_SIZE}
            total={totalElements}
            noun="aula"
            searchActive={!!search}
          />
        )}
      </div>

      {/* ── Modal: Ver información ──────────────────────────────────────────── */}
      {viewTarget && (
        <Suspense fallback={null}>
          <ClassroomInfoModal
            open={!!viewTarget}
            onClose={closeView}
            classroom={viewTarget}
            allClassrooms={allClassrooms}
          />
        </Suspense>
      )}

      {/* ── Modales de administración (solo ADMIN) ──────────────────────────── */}
      {isAdmin && (
        <Suspense fallback={null}>
          {/* Recursos del aula */}
          {resourcesTarget && (
            <ClassroomResourcesModal
              open={!!resourcesTarget}
              onClose={() => setResourcesTarget(null)}
              classroom={resourcesTarget}
            />
          )}

          {/* Crear */}
          {createOpen && (
            <FormModal
              open={createOpen}
              onClose={closeCreate}
              title="Nueva Aula"
              subtitle="Completa la información para registrar una nueva aula."
              submitLabel="Crear Aula"
              submitIcon={<Plus size={18} />}
              loading={createMutation.isPending}
              onSubmit={handleCreateSubmit}
            >
              <ClassroomFormFields
                mode="create"
                form={form}
                onField={onField}
                onBlurField={onFieldBlur}
                errors={errors}
                parentOptions={parentOptions}
              />
            </FormModal>
          )}

          {/* Editar */}
          {editTarget && (
            <FormModal
              open={!!editTarget}
              onClose={closeEdit}
              title="Editar Aula"
              subtitle={editTarget?.name ?? ''}
              submitLabel="Guardar cambios"
              loading={updateMutation.isPending || setChildrenMutation.isPending}
              onSubmit={handleEditSubmit}
            >
              <ClassroomFormFields
                mode="edit"
                form={form}
                onField={onField}
                onBlurField={onFieldBlur}
                errors={errors}
                parentOptions={parentOptions}
                childOptions={childOptions}
              />
            </FormModal>
          )}

          {/* Eliminar */}
          {deleteTarget && (
            <ConfirmDeleteModal
              open={!!deleteTarget}
              onClose={() => setDeleteTarget(null)}
              onConfirm={() => deleteMutation.mutateAsync(deleteTarget?.uuid)}
              title="¿Eliminar esta aula?"
              message={
                deleteTarget
                  ? deleteTargetChildren.length > 0
                    ? `Se eliminará "${deleteTarget.name}" de forma permanente. ` +
                    `⚠️ Esta aula es padre de ${deleteTargetChildren.length} aula${deleteTargetChildren.length !== 1 ? 's' : ''} ` +
                    `(${deleteTargetChildren.map((c) => c.name).join(', ')}); ` +
                    `al eliminarla quedarán desvinculadas. ` +
                    `Esta acción no se puede deshacer.`
                    : `Se eliminará "${deleteTarget.name}" de forma permanente. Esta acción no se puede deshacer.`
                  : 'Esta acción eliminará el aula seleccionada.'
              }
              confirmLabel="Eliminar"
              cancelLabel="Cancelar"
            />
          )}
        </Suspense>
      )}
    </div>
  );
}

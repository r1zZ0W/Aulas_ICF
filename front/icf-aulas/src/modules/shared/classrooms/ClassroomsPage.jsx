import { useMemo, useState } from 'react';
import { Plus, Eye, Pencil, Trash2, Building2, CheckCircle2, XCircle, RotateCcw } from 'lucide-react';

import { useAuth } from '../../../context/AuthContext';
import { ROLES } from '../../../utils/roles';
import { useClassrooms, useAllClassrooms } from '../../../hooks/useClassrooms';
import { useClassroomsForm } from '../../../hooks/useClassroomsForm';
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
import Pagination from '../../../components/Pagination/Pagination';
import FormModal from '../../../components/FormModal/FormModal';
import ConfirmDeleteModal from '../../../components/ConfirmDeleteModal/ConfirmDeleteModal';
import ClassroomFormFields from './ClassroomFormFields';
import ClassroomInfoModal from './ClassroomInfoModal';
import ActiveSemesterButton from '../semesters/ActiveSemesterButton';

import './ClassroomsPage.css';

// ── Status filter options ──────────────────────────────────────────────────────
// Client-side filter (all classrooms are fetched from the server for ADMIN).
// TODO: when GET /api/v1/classrooms accepts ?status=, remove client-side filter
// and pass the param to useClassrooms (see classrooms-children-array-request.md §2).
const STATUS_FILTER_OPTIONS = [
  { value: 'all',      label: 'Todas'      },
  { value: 'active',   label: 'Activas'    },
  { value: 'inactive', label: 'Inactivas'  },
];

// ── Component ──────────────────────────────────────────────────────────────────

export default function ClassroomsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.ADMIN;

  // ── Pagination + search ───────────────────────────────────────────────────────
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300 });

  // ── Client-side status filter (admin sees all, then filters locally) ──────────
  const [statusFilter, setStatusFilter] = useState('all');

  // ── Server state (paginated) ──────────────────────────────────────────────────
  const {
    classrooms: rawClassrooms,
    totalElements,
    totalPages,
    stats,
    loading,
    createMutation,
    updateMutation,
    deactivateMutation,
    reactivateMutation,
    setChildrenMutation,
  } = useClassrooms({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort:      'name',
    direction: 'asc',
  });

  // ── Full catalog (for parent/child selectors + InfoModal children list) ────────
  const { allClassrooms } = useAllClassrooms();

  // ── Apply client-side status filter ──────────────────────────────────────────
  // Only meaningful for admins (teachers already receive active-only from server).
  const classrooms = useMemo(() => {
    if (!isAdmin || statusFilter === 'all') return rawClassrooms;
    if (statusFilter === 'active')   return rawClassrooms.filter((r) => r.isActive);
    if (statusFilter === 'inactive') return rawClassrooms.filter((r) => !r.isActive);
    return rawClassrooms;
  }, [rawClassrooms, statusFilter, isAdmin]);

  // ── Modal / form state ────────────────────────────────────────────────────────
  const {
    createOpen, editTarget, viewTarget, deleteTarget,
    form,
    openCreate, closeCreate,
    openEdit,   closeEdit,
    openView,   closeView,
    setDeleteTarget,
    onField,
    handleCreateSubmit,
    handleEditSubmit,
  } = useClassroomsForm({
    createMutation,
    updateMutation,
    setChildrenMutation,
    allClassrooms,
  });

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
      key:   'name',
      header:'Nombre',
      width: '26%',
      render:(r) => r.name,
    },
    {
      key:   'type',
      header:'Tipo',
      width: '14%',
      render:(r) => typeLabel(r.type),
    },
    {
      key:   'capacity',
      header:'Capacidad',
      width: '10%',
      render:(r) => r.capacity ?? '—',
    },
    {
      key:   'description',
      header:'Descripción',
      width: '26%',
      render:(r) => (
        <span
          className="classrooms__description"
          title={r.description || undefined}
        >
          {r.description || '—'}
        </span>
      ),
    },
    {
      key:   'isActive',
      header:'Estado',
      width: '10%',
      render:(r) => (
        <Badge variant={r.isActive ? 'success' : 'danger'}>
          {r.isActive ? 'Disponible' : 'No disponible'}
        </Badge>
      ),
    },
    {
      key:   'acciones',
      header:'Acciones',
      width: isAdmin ? '14%' : '8%',
      align: 'right',
      render:(r) => (
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

              {/* Dar de baja (active only) / Reactivar (inactive only) */}
              {r.isActive ? (
                <button
                  type="button"
                  className="classrooms__action-btn classrooms__action-btn--danger"
                  title={`Dar de baja ${r.name}`}
                  aria-label={`Dar de baja ${r.name}`}
                  onClick={() => setDeleteTarget(r)}
                >
                  <Trash2 size={16} />
                </button>
              ) : (
                <button
                  type="button"
                  className="classrooms__action-btn classrooms__action-btn--success"
                  title={`Reactivar ${r.name}`}
                  aria-label={`Reactivar ${r.name}`}
                  disabled={reactivateMutation.isPending}
                  onClick={() => reactivateMutation.mutateAsync(r.uuid)}
                >
                  <RotateCcw size={16} />
                </button>
              )}
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
            showing={classrooms.length}
            total={totalElements}
            noun="aula"
            searchActive={!!search}
          />
        )}
      </div>

      {/* ── Modal: Ver información ──────────────────────────────────────────── */}
      <ClassroomInfoModal
        open={!!viewTarget}
        onClose={closeView}
        classroom={viewTarget}
        allClassrooms={allClassrooms}
      />

      {/* ── Modales de administración (solo ADMIN) ──────────────────────────── */}
      {isAdmin && (
        <>
          {/* Crear */}
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
              parentOptions={parentOptions}
            />
          </FormModal>

          {/* Editar */}
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
              parentOptions={parentOptions}
              childOptions={childOptions}
            />
          </FormModal>

          {/* Dar de baja */}
          <ConfirmDeleteModal
            open={!!deleteTarget}
            onClose={() => setDeleteTarget(null)}
            onConfirm={() => deactivateMutation.mutateAsync(deleteTarget?.uuid)}
            title="¿Dar de baja esta aula?"
            message={
              deleteTarget
                ? deleteTargetChildren.length > 0
                  ? `Se desactivará "${deleteTarget.name}". ` +
                    `⚠️ Esta aula es padre de ${deleteTargetChildren.length} aula${deleteTargetChildren.length !== 1 ? 's' : ''} ` +
                    `(${deleteTargetChildren.map((c) => c.name).join(', ')}); ` +
                    `al darla de baja quedarán desvinculadas pero seguirán activas. ` +
                    `El historial de reservaciones se conservará.`
                  : `Se desactivará "${deleteTarget.name}". El historial de reservaciones se conservará. ` +
                    `Puedes reactivarla desde la tabla en cualquier momento.`
                : 'Esta acción desactivará el aula seleccionada.'
            }
            confirmLabel="Dar de baja"
            cancelLabel="Cancelar"
          />
        </>
      )}
    </div>
  );
}

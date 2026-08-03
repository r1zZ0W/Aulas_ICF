import { useMemo, lazy, Suspense } from 'react';
import { Boxes, PackageCheck, Plus, Pencil, Trash2 } from 'lucide-react';

import { useResources } from './hooks/useResources';
import { useResourcesForm } from './hooks/useResourcesForm';
import { usePagination } from '../../../hooks/usePagination';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';

import Button from '../../../components/Button/Button';
import Card from '../../../components/Card/Card';
import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import EmptyState from '../../../components/EmptyState/EmptyState';
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner';
import Pagination from '../../../components/Pagination/Pagination';

// ── Lazy-loaded modal components ──────────────────────────────────────────────
const FormModal = lazy(() => import('../../../components/FormModal/FormModal'));
const ConfirmDeleteModal = lazy(() => import('../../../components/ConfirmDeleteModal/ConfirmDeleteModal'));
const ResourceFormFields = lazy(() => import('./ResourceFormFields'));

import './ResourcesPage.css';

/**
 * Admin screen for the global equipment resource catalog ("Gestión de Recursos").
 *
 * Deliberately scoped to catalog CRUD + a global `quantity` per resource — no
 * per-unit availability, status, or usage tracking (left for a future iteration).
 * See the plan for the full rationale.
 */
export default function ResourcesPage() {
  // ── Search / pagination state (URL-synced) ───────────────────────────────────
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300 });

  // ── Server state ─────────────────────────────────────────────────────────────
  const {
    resources,
    totalElements,
    totalPages,
    stats,
    resourcesLoading,
    resourcesError,
    refetchResources,
    createMutation,
    updateMutation,
    deleteMutation,
  } = useResources({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name',
    direction: 'asc',
  });

  // ── Form / modal state ───────────────────────────────────────────────────────
  const {
    createOpen,
    editResource,
    deleteTarget,
    setDeleteTarget,
    createForm,
    editForm,
    createErrors,
    editErrors,
    openCreate,
    closeCreate,
    openEdit,
    closeEdit,
    handleCreateField,
    handleEditField,
    handleCreateBlur,
    handleEditBlur,
    handleCreateSubmit,
    handleEditSubmit,
  } = useResourcesForm({ createMutation, updateMutation });

  // ── Table columns ────────────────────────────────────────────────────────────
  const columns = useMemo(
    () => [
      {
        key: 'name',
        header: 'Recurso',
        width: '28%',
        render: (row) => <span className="resources-page__resource-name">{row.name}</span>,
      },
      {
        key: 'description',
        header: 'Descripción',
        width: '42%',
        render: (row) => (
          <span className="resources-page__resource-description">
            {row.description || '—'}
          </span>
        ),
      },
      {
        key: 'quantity',
        header: 'Cant.',
        width: '12%',
        render: (row) => <span className="resources-page__resource-qty">{row.quantity}</span>,
      },
      {
        key: 'acciones',
        header: 'Acciones',
        width: '18%',
        align: 'right',
        render: (row) => (
          <div className="resources-page__actions">
            <button
              type="button"
              className="resources-page__action-btn"
              title="Editar recurso"
              onClick={() => openEdit(row)}
            >
              <Pencil size={16} />
            </button>
            <button
              type="button"
              className="resources-page__action-btn resources-page__action-btn--danger"
              title="Eliminar recurso"
              onClick={() => setDeleteTarget(row)}
            >
              <Trash2 size={16} />
            </button>
          </div>
        ),
      },
    ],
    [openEdit, setDeleteTarget]
  );

  // ── Render ───────────────────────────────────────────────────────────────────
  return (
    <div className="resources-page">

      {/* Header */}
      <div className="resources-page__header">
        <div>
          <h1 className="resources-page__title">Gestión de Recursos</h1>
          <p className="resources-page__subtitle">
            Administra el inventario global de recursos asignables a las aulas
          </p>
        </div>
        <Button
          variant="primary"
          size="small"
          iconLeft={<Plus size={18} />}
          iconSize={18}
          onClick={openCreate}
        >
          Nuevo Recurso
        </Button>
      </div>

      {/* Stats — sourced from GET /api/v1/resources/stats (full corpus counts) */}
      <div className="resources-page__stats">
        <Card className="resources-page__stat-card">
          <span className="resources-page__stat-icon resources-page__stat-icon--blue">
            <Boxes size={24} />
          </span>
          <div>
            <p className="resources-page__stat-label">Total Tipos</p>
            <p className="resources-page__stat-value">{stats.totalTypes}</p>
          </div>
        </Card>
        <Card className="resources-page__stat-card">
          <span className="resources-page__stat-icon resources-page__stat-icon--green">
            <PackageCheck size={24} />
          </span>
          <div>
            <p className="resources-page__stat-label">Unidades Totales</p>
            <p className="resources-page__stat-value">{stats.totalUnits}</p>
          </div>
        </Card>
      </div>

      {/* Table card */}
      <div className="resources-page__table-card">
        {resourcesError && (
          <ErrorBanner
            message="No se pudo cargar el catálogo de recursos."
            onDismiss={() => refetchResources()}
          />
        )}

        {/* Toolbar */}
        <div className="resources-page__table-toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar recurso por nombre o descripción..."
            style={{ maxWidth: 448 }}
          />
        </div>

        {/* Table */}
        <DataTable
          columns={columns}
          rows={resources}
          rowKey={(row) => row.uuid}
          loading={resourcesLoading}
          loadingMessage="Cargando recursos…"
          emptyState={
            <EmptyState
              hasSearch={!!searchInput}
              message="Aún no hay recursos registrados en el catálogo."
              searchMessage="No se encontraron recursos que coincidan con la búsqueda."
              actionLabel="Nuevo Recurso"
              onAction={openCreate}
            />
          }
        />

        {/* Pagination */}
        {!resourcesLoading && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            pageSize={resources.length}
            total={totalElements}
            noun="recurso"
            searchActive={!!searchInput}
          />
        )}
      </div>

      {/* ── Modales condicionales envueltos en Suspense ──────────────────── */}
      <Suspense fallback={null}>
        {/* ── Modal: Crear recurso ─────────────────────────────────────────── */}
        {createOpen && (
          <FormModal
            open={createOpen}
            onClose={closeCreate}
            title="Nuevo Recurso"
            subtitle="Agrega un nuevo tipo de equipo al catálogo global."
            submitLabel="Crear Recurso"
            submitIcon={<Plus size={18} />}
            submitIconSize={18}
            loading={createMutation.isPending}
            onSubmit={handleCreateSubmit}
          >
            <ResourceFormFields
              mode="create"
              form={createForm}
              onField={handleCreateField}
              onBlurField={handleCreateBlur}
              errors={createErrors}
            />
          </FormModal>
        )}

        {/* ── Modal: Editar recurso ─────────────────────────────────────────── */}
        {editResource && (
          <FormModal
            open={!!editResource}
            onClose={closeEdit}
            title="Editar Recurso"
            subtitle={editResource ? editResource.name : ''}
            submitLabel="Guardar cambios"
            loading={updateMutation.isPending}
            onSubmit={handleEditSubmit}
          >
            <ResourceFormFields
              mode="edit"
              form={editForm}
              onField={handleEditField}
              onBlurField={handleEditBlur}
              errors={editErrors}
            />
          </FormModal>
        )}

        {/* ── Modal: Eliminar ───────────────────────────────────────────────── */}
        {deleteTarget && (
          <ConfirmDeleteModal
            open={!!deleteTarget}
            onClose={() => setDeleteTarget(null)}
            onConfirm={() => deleteMutation.mutateAsync(deleteTarget?.uuid)}
            title="¿Eliminar recurso?"
            message={
              deleteTarget
                ? `Se eliminará permanentemente "${deleteTarget.name}" del catálogo, junto con sus asignaciones a cualquier aula. Esta acción no se puede deshacer.`
                : 'Esta acción eliminará permanentemente el recurso y sus asignaciones a las aulas.'
            }
            confirmLabel="Eliminar"
            cancelLabel="Cancelar"
          />
        )}
      </Suspense>
    </div>
  );
}

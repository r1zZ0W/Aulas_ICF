import { Users, ShieldCheck, Plus, Pencil, Trash2 } from 'lucide-react';

import { useUsers } from './hooks/useUsers';
import { useUsersForm } from './hooks/useUsersForm';
import { usePagination } from '../../../hooks/usePagination';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { DISPLAY_ROLE, roleBadgeVariant, roleLabel } from '../../../utils/roles';
import { getInitials } from '../../../utils/format';

import Button from '../../../components/Button/Button';
import Card from '../../../components/Card/Card';
import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import Pagination from '../../../components/Pagination/Pagination';
import FormModal from '../../../components/FormModal/FormModal';
import ConfirmDeleteModal from '../../../components/ConfirmDeleteModal/ConfirmDeleteModal';
import UserFormFields from './UserFormFields';

import './UsersPage.css';

// ── Component ─────────────────────────────────────────────────────────────────

export default function UsersPage() {
  // ── Search / pagination state (URL-synced) ───────────────────────────────────
  const { searchInput, setSearchInput, search, page, setPage } = usePagination({ debounce: 300 });

  // ── Server state ─────────────────────────────────────────────────────────────
  const {
    users,
    totalElements,
    totalPages,
    stats,
    roles,
    usersLoading,
    createMutation,
    updateMutation,
    deleteMutation,
  } = useUsers({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort: 'createdAt',
    direction: 'desc',
  });

  // ── Form / modal state ───────────────────────────────────────────────────────
  const {
    createOpen,
    editUser,
    deleteTarget,
    setDeleteTarget,
    createForm,
    editForm,
    formErrors,
    openCreate,
    closeCreate,
    openEdit,
    closeEdit,
    handleCreateField,
    handleEditField,
    handleCreateSubmit,
    handleEditSubmit,
  } = useUsersForm({ roles, createMutation, updateMutation });

  // ── Select options ───────────────────────────────────────────────────────────
  const roleOptions = roles.map((r) => ({
    value: String(r.id),
    label: DISPLAY_ROLE[r.name] ?? r.name,
  }));

  // ── Table columns ────────────────────────────────────────────────────────────
  const columns = [
    {
      key: 'usuario',
      header: 'Usuario',
      width: '28%',
      render: (row) => (
        <div className="users-page__user-cell">
          <span className="users-page__avatar">{getInitials(row)}</span>
          <div>
            <p className="users-page__user-name">{row.firstName} {row.lastNames}</p>
            <p className="users-page__user-email">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'roleName',
      header: 'Rol',
      width: '14%',
      render: (row) => (
        <Badge variant={roleBadgeVariant(row.roleName)}>{roleLabel(row.roleName)}</Badge>
      ),
    },
    {
      key: 'acciones',
      header: 'Acciones',
      width: '14%',
      align: 'right',
      render: (row) => (
        <div className="users-page__actions">
          <button
            type="button"
            className="users-page__action-btn"
            title="Editar usuario"
            onClick={() => openEdit(row)}
          >
            <Pencil size={16} />
          </button>
          <button
            type="button"
            className="users-page__action-btn users-page__action-btn--danger"
            title="Eliminar usuario"
            onClick={() => setDeleteTarget(row)}
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ];

  // ── Render ───────────────────────────────────────────────────────────────────
  return (
    <div className="users-page">

      {/* Header */}
      <div className="users-page__header">
        <div>
          <h1 className="users-page__title">Gestión de Usuarios</h1>
          <p className="users-page__subtitle">Administra los accesos y roles del sistema</p>
        </div>
        <Button
          variant="primary"
          size="small"
          iconLeft={<Plus size={18} />}
          iconSize={18}
          onClick={openCreate}
        >
          Nuevo Usuario
        </Button>
      </div>

      {/* Stats — sourced from GET /api/v1/users/stats (full corpus counts) */}
      <div className="users-page__stats">
        <Card className="users-page__stat-card">
          <span className="users-page__stat-icon users-page__stat-icon--blue">
            <Users size={24} />
          </span>
          <div>
            <p className="users-page__stat-label">Total de Usuarios</p>
            <p className="users-page__stat-value">{stats.total}</p>
          </div>
        </Card>
        <Card className="users-page__stat-card">
          <span className="users-page__stat-icon users-page__stat-icon--purple">
            <ShieldCheck size={24} />
          </span>
          <div>
            <p className="users-page__stat-label">Total de Administradores</p>
            <p className="users-page__stat-value">{stats.admins}</p>
          </div>
        </Card>
      </div>

      {/* Table card */}
      <div className="users-page__table-card">
        {/* Toolbar */}
        <div className="users-page__table-toolbar">
          <Buscador
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Buscar usuarios por nombre, correo o usuario..."
            style={{ maxWidth: 448 }}
          />
        </div>

        {/* Table */}
        <DataTable
          columns={columns}
          rows={users}
          rowKey={(row) => row.uuid}
          loading={usersLoading}
          loadingMessage="Cargando usuarios…"
          emptyState={
            <EmptyState
              hasSearch={!!searchInput}
              message="Aún no hay usuarios registrados en el sistema."
              searchMessage="No se encontraron usuarios que coincidan con la búsqueda."
              actionLabel="Nuevo Usuario"
              onAction={openCreate}
            />
          }
        />

        {/* Pagination */}
        {!usersLoading && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
            pageSize={users.length}
            total={totalElements}
            noun="usuario"
            searchActive={!!searchInput}
          />
        )}
      </div>

      {/* ── Modal: Crear usuario ─────────────────────────────────────────── */}
      <FormModal
        open={createOpen}
        onClose={closeCreate}
        title="Nuevo Usuario"
        subtitle="Completa la información para registrar un nuevo acceso al sistema."
        submitLabel="Crear Usuario"
        submitIcon={<Plus size={18} />}
        submitIconSize={18}
        loading={createMutation.isPending}
        onSubmit={handleCreateSubmit}
      >
        <UserFormFields
          mode="create"
          form={createForm}
          onField={handleCreateField}
          errors={formErrors}
          roleOptions={roleOptions}
        />
      </FormModal>

      {/* ── Modal: Editar usuario ─────────────────────────────────────────── */}
      <FormModal
        open={!!editUser}
        onClose={closeEdit}
        title="Editar Usuario"
        subtitle={editUser ? `${editUser.firstName} ${editUser.lastNames}` : ''}
        submitLabel="Guardar cambios"
        loading={updateMutation.isPending}
        onSubmit={handleEditSubmit}
      >
        <UserFormFields
          mode="edit"
          form={editForm}
          onField={handleEditField}
          errors={formErrors}
          roleOptions={roleOptions}
        />
      </FormModal>

      {/* ── Modal: Eliminar ───────────────────────────────────────────────── */}
      <ConfirmDeleteModal
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteMutation.mutateAsync(deleteTarget?.uuid)}
        title="¿Eliminar usuario?"
        message={
          deleteTarget
            ? `Se eliminará permanentemente a ${deleteTarget.firstName} ${deleteTarget.lastNames} junto con todas sus reservaciones. Esta acción no se puede deshacer.`
            : 'Esta acción eliminará permanentemente al usuario y todas sus reservaciones.'
        }
        confirmLabel="Eliminar"
        cancelLabel="Cancelar"
      />
    </div>
  );
}

import { useState, useEffect } from 'react';
import { Users, UserCheck, UserX, ShieldCheck, Plus, Pencil, Trash2 } from 'lucide-react';

import { useUsers }          from '../../../hooks/useUsers';
import { useUsersForm }      from '../../../hooks/useUsersForm';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { DISPLAY_ROLE }      from '../../../utils/roles';

import Button             from '../../../components/Button/Button';
import Card               from '../../../components/Card/Card';
import Buscador           from '../../../components/Buscador/Buscador';
import DataTable          from '../../../components/DataTable/DataTable';
import Badge              from '../../../components/Badge/Badge';
import EmptyState         from '../../../components/EmptyState/EmptyState';
import FormModal          from '../../../components/FormModal/FormModal';
import ConfirmDeleteModal from '../../../components/ConfirmDeleteModal/ConfirmDeleteModal';
import Input              from '../../../components/Input/Input';
import Select             from '../../../components/Select/Select';

import './UsersPage.css';

// ── Pure helpers ──────────────────────────────────────────────────────────────

/** Returns initials (up to 2 chars) from firstName + lastNames. */
function getInitials(user) {
  const parts = [user.firstName, user.lastNames].filter(Boolean);
  return parts.map((p) => p[0]).join('').toUpperCase().slice(0, 2);
}

/** Returns badge variant by roleName from backend (ADMIN / MAESTRO). */
function roleBadgeVariant(roleName) {
  return roleName?.toUpperCase() === 'ADMIN' ? 'primary' : 'neutral';
}

/** Returns the spanish display label for a backend role name. */
function roleLabel(roleName) {
  return DISPLAY_ROLE[roleName] ?? roleName ?? '—';
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function UsersPage() {
  // ── Search / pagination state ────────────────────────────────────────────────
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage]               = useState(0);

  // Debounced search sent to the server (avoids a fetch on every keystroke).
  const search = useDebouncedValue(searchInput, 300);

  // Reset to page 0 whenever the active search term changes.
  useEffect(() => { setPage(0); }, [search]);

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
    deactivateMutation,
  } = useUsers({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort:      'createdAt',
    direction: 'desc',
  });

  // ── Form / modal state ───────────────────────────────────────────────────────
  const {
    createOpen,
    editUser,
    deactivateTarget,
    setDeactivateTarget,
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

  // ── Derived ──────────────────────────────────────────────────────────────────
  const safeTotalPages = Math.max(1, totalPages);
  const isLastPage     = page >= safeTotalPages - 1;

  // ── Select options ───────────────────────────────────────────────────────────
  const roleOptions = roles.map((r) => ({
    value: String(r.id),
    label: DISPLAY_ROLE[r.name] ?? r.name,
  }));

  const statusOptions = [
    { value: 'true',  label: 'Activo' },
    { value: 'false', label: 'Inactivo' },
  ];

  // ── Table columns ────────────────────────────────────────────────────────────
  const columns = [
    {
      key: 'usuario',
      header: 'Usuario',
      width: '32%',
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
      key: 'departamento',
      header: 'Departamento',
      width: '20%',
      render: (row) => row.departamento || '—',
    },
    {
      key: 'roleName',
      header: 'Rol',
      width: '16%',
      render: (row) => (
        <Badge variant={roleBadgeVariant(row.roleName)}>{roleLabel(row.roleName)}</Badge>
      ),
    },
    {
      key: 'isActive',
      header: 'Estado',
      width: '14%',
      render: (row) => (
        <Badge variant={row.isActive ? 'success' : 'danger'}>
          {row.isActive ? 'Activo' : 'Inactivo'}
        </Badge>
      ),
    },
    {
      key: 'acciones',
      header: 'Acciones',
      width: '10%',
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
            title="Desactivar usuario"
            onClick={() => setDeactivateTarget(row)}
            disabled={!row.isActive}
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
            <p className="users-page__stat-label">Total Usuarios</p>
            <p className="users-page__stat-value">{stats.total}</p>
          </div>
        </Card>
        <Card className="users-page__stat-card">
          <span className="users-page__stat-icon users-page__stat-icon--green">
            <UserCheck size={24} />
          </span>
          <div>
            <p className="users-page__stat-label">Activos</p>
            <p className="users-page__stat-value">{stats.active}</p>
          </div>
        </Card>
        <Card className="users-page__stat-card">
          <span className="users-page__stat-icon users-page__stat-icon--red">
            <UserX size={24} />
          </span>
          <div>
            <p className="users-page__stat-label">Inactivos</p>
            <p className="users-page__stat-value">{stats.inactive}</p>
          </div>
        </Card>
        <Card className="users-page__stat-card">
          <span className="users-page__stat-icon users-page__stat-icon--purple">
            <ShieldCheck size={24} />
          </span>
          <div>
            <p className="users-page__stat-label">Administradores</p>
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
          <div className="users-page__pagination">
            <p className="users-page__pagination-info">
              Mostrando {users.length} de {totalElements} usuario{totalElements !== 1 ? 's' : ''}
              {searchInput && ' (búsqueda activa)'}
            </p>
            <div className="users-page__pagination-controls">
              <button
                type="button"
                className="users-page__page-btn"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Anterior
              </button>
              <button
                type="button"
                className="users-page__page-btn"
                onClick={() => setPage((p) => p + 1)}
                disabled={isLastPage}
              >
                Siguiente
              </button>
            </div>
          </div>
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
        <div className="users-page__form-grid">
          <Input
            label="Nombre(s)"
            value={createForm.firstName}
            onChange={(e) => handleCreateField('firstName', e.target.value)}
            placeholder="Ej. María"
            error={formErrors.firstName}
            required
          />
          <Input
            label="Apellidos"
            value={createForm.lastNames}
            onChange={(e) => handleCreateField('lastNames', e.target.value)}
            placeholder="Ej. García López"
            error={formErrors.lastNames}
            required
          />
          <Input
            label="Nombre de usuario"
            value={createForm.username}
            onChange={(e) => handleCreateField('username', e.target.value)}
            placeholder="Ej. mgarcia"
            error={formErrors.username}
            required
          />
          <Input
            label="Correo institucional"
            type="email"
            value={createForm.email}
            onChange={(e) => handleCreateField('email', e.target.value)}
            placeholder="usuario@icf.unam.mx"
            error={formErrors.email}
            required
          />
          <Input
            label="Contraseña"
            type="password"
            value={createForm.password}
            onChange={(e) => handleCreateField('password', e.target.value)}
            placeholder="Mín. 8 caracteres"
            error={formErrors.password}
            required
          />
          <Input
            label="Departamento"
            value={createForm.departamento}
            onChange={(e) => handleCreateField('departamento', e.target.value)}
            placeholder="Ej. Dirección"
            error={formErrors.departamento}
          />
          <div className="users-page__form-grid--full">
            <Select
              label="Rol"
              value={String(createForm.roleId)}
              onChange={(v) => handleCreateField('roleId', v)}
              options={roleOptions}
              placeholder="Seleccionar rol..."
              error={formErrors.roleId}
              required
            />
          </div>
        </div>
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
        <div className="users-page__form-grid">
          <Input
            label="Nombre(s)"
            value={editForm.firstName}
            onChange={(e) => handleEditField('firstName', e.target.value)}
            error={formErrors.firstName}
            required
          />
          <Input
            label="Apellidos"
            value={editForm.lastNames}
            onChange={(e) => handleEditField('lastNames', e.target.value)}
            error={formErrors.lastNames}
            required
          />
          <Input
            label="Nombre de usuario"
            value={editForm.username}
            onChange={(e) => handleEditField('username', e.target.value)}
            error={formErrors.username}
            required
          />
          <Input
            label="Correo electrónico"
            type="email"
            value={editForm.email}
            onChange={(e) => handleEditField('email', e.target.value)}
            error={formErrors.email}
            required
          />
          <Input
            label="Departamento"
            value={editForm.departamento}
            onChange={(e) => handleEditField('departamento', e.target.value)}
            error={formErrors.departamento}
          />
          <Select
            label="Estado"
            value={String(editForm.isActive)}
            onChange={(v) => handleEditField('isActive', v === 'true')}
            options={statusOptions}
            error={formErrors.isActive}
          />
          <div className="users-page__form-grid--full">
            <Select
              label="Rol"
              value={String(editForm.roleId)}
              onChange={(v) => handleEditField('roleId', v)}
              options={roleOptions}
              placeholder="Seleccionar rol..."
              error={formErrors.roleId}
              required
            />
          </div>
        </div>
      </FormModal>

      {/* ── Modal: Desactivar ──────────────────────────────────────────────── */}
      <ConfirmDeleteModal
        open={!!deactivateTarget}
        onClose={() => setDeactivateTarget(null)}
        onConfirm={() => deactivateMutation.mutateAsync(deactivateTarget?.uuid)}
        title="¿Desactivar usuario?"
        message={
          deactivateTarget
            ? `El acceso de ${deactivateTarget.firstName} ${deactivateTarget.lastNames} quedará deshabilitado. Podrás reactivarlo editando su perfil.`
            : 'Esta acción deshabilitará el acceso del usuario.'
        }
        confirmLabel="Desactivar"
        cancelLabel="Cancelar"
      />
    </div>
  );
}

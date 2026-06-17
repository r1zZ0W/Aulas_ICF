import { useState, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, UserCheck, UserX, ShieldCheck, Plus, Pencil, Trash2 } from 'lucide-react';

import { getUsers, createUser, updateUser, deactivateUser, getRoles } from '../../../api/users';
import { UserCreateSchema, UserUpdateSchema } from '../../../schemas/index.js';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';
import { toast } from '../../../utils/toast.jsx';
import { DISPLAY_ROLE } from '../../../utils/roles';

import Button from '../../../components/Button/Button';
import Card from '../../../components/Card/Card';
import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import FormModal from '../../../components/FormModal/FormModal';
import ConfirmDeleteModal from '../../../components/ConfirmDeleteModal/ConfirmDeleteModal';
import Input from '../../../components/Input/Input';
import Select from '../../../components/Select/Select';

import './UsersPage.css';

// ── Helpers ──────────────────────────────────────────────────────────────────

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

// ── Empty form state ──────────────────────────────────────────────────────────

const EMPTY_CREATE = {
  firstName: '', lastNames: '', username: '', email: '',
  password: '', departamento: '', roleId: '',
};
const EMPTY_UPDATE = {
  firstName: '', lastNames: '', username: '', email: '',
  departamento: '', roleId: '', isActive: true,
};

// ── Component ─────────────────────────────────────────────────────────────────

export default function UsersPage() {
  const queryClient = useQueryClient();

  // ── Remote data ────────────────────────────────────────────────────────────
  const { data: users = [], isLoading: usersLoading } = useQuery({
    queryKey: ['users'],
    queryFn: getUsers,
  });

  const { data: roles = [] } = useQuery({
    queryKey: ['roles'],
    queryFn: getRoles,
  });

  // ── Local state ────────────────────────────────────────────────────────────
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState(null);    // full user object or null
  const [deactivateTarget, setDeactivateTarget] = useState(null);

  const [createForm, setCreateForm] = useState(EMPTY_CREATE);
  const [editForm, setEditForm]   = useState(EMPTY_UPDATE);
  const [formErrors, setFormErrors] = useState({});

  // ── Derived stats ──────────────────────────────────────────────────────────
  const stats = useMemo(() => ({
    total:   users.length,
    active:  users.filter((u) => u.isActive).length,
    inactive: users.filter((u) => !u.isActive).length,
    admins:  users.filter((u) => u.roleName?.toUpperCase() === 'ADMIN').length,
  }), [users]);

  // ── Filtered + paginated rows ──────────────────────────────────────────────
  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return users;
    return users.filter((u) =>
      `${u.firstName} ${u.lastNames}`.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q) ||
      u.username?.toLowerCase().includes(q)
    );
  }, [users, search]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / DEFAULT_PAGE_SIZE));
  const safePage   = Math.min(page, totalPages - 1);
  const pageRows   = filtered.slice(safePage * DEFAULT_PAGE_SIZE, (safePage + 1) * DEFAULT_PAGE_SIZE);

  // ── Role options for Select ────────────────────────────────────────────────
  const roleOptions = roles.map((r) => ({
    value: String(r.id),
    label: DISPLAY_ROLE[r.name] ?? r.name,
  }));

  const statusOptions = [
    { value: 'true',  label: 'Activo' },
    { value: 'false', label: 'Inactivo' },
  ];

  // ── Mutations ─────────────────────────────────────────────────────────────
  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success('Usuario creado correctamente.');
      setCreateOpen(false);
      setCreateForm(EMPTY_CREATE);
      setFormErrors({});
    },
    onError: (err) => toast.error(err.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ uuid, payload }) => updateUser(uuid, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success('Usuario actualizado correctamente.');
      setEditUser(null);
      setFormErrors({});
    },
    onError: (err) => toast.error(err.message),
  });

  const deactivateMutation = useMutation({
    mutationFn: deactivateUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast.success('Usuario desactivado correctamente.');
    },
    onError: (err) => toast.error(err.message),
  });

  // ── Form helpers ───────────────────────────────────────────────────────────
  function parseZodErrors(zodError) {
    const out = {};
    for (const issue of zodError.issues ?? []) {
      const key = issue.path[0];
      if (key && !out[key]) out[key] = issue.message;
    }
    return out;
  }

  function handleCreateField(field, value) {
    setCreateForm((f) => ({ ...f, [field]: value }));
    setFormErrors((e) => ({ ...e, [field]: undefined }));
  }

  function handleEditField(field, value) {
    setEditForm((f) => ({ ...f, [field]: value }));
    setFormErrors((e) => ({ ...e, [field]: undefined }));
  }

  function handleOpenEdit(user) {
    setEditUser(user);
    setEditForm({
      firstName:    user.firstName,
      lastNames:    user.lastNames,
      username:     user.username ?? '',
      email:        user.email,
      departamento: user.departamento ?? '',
      roleId:       String(roles.find((r) => r.name.toUpperCase() === user.roleName?.toUpperCase())?.id ?? ''),
      isActive:     user.isActive,
    });
    setFormErrors({});
  }

  // ── Submit handlers ────────────────────────────────────────────────────────
  function handleCreateSubmit() {
    const payload = {
      ...createForm,
      roleId: createForm.roleId ? Number(createForm.roleId) : undefined,
      departamento: createForm.departamento || undefined,
    };
    const result = UserCreateSchema.safeParse(payload);
    if (!result.success) {
      setFormErrors(parseZodErrors(result.error));
      return;
    }
    createMutation.mutate(result.data);
  }

  function handleEditSubmit() {
    const payload = {
      ...editForm,
      roleId:       Number(editForm.roleId),
      isActive:     editForm.isActive === 'true' || editForm.isActive === true,
      departamento: editForm.departamento || undefined,
    };
    const result = UserUpdateSchema.safeParse(payload);
    if (!result.success) {
      setFormErrors(parseZodErrors(result.error));
      return;
    }
    updateMutation.mutate({ uuid: editUser.uuid, payload: result.data });
  }

  // ── Table columns ──────────────────────────────────────────────────────────
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
            onClick={() => handleOpenEdit(row)}
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

  // ── Render ─────────────────────────────────────────────────────────────────
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
          onClick={() => { setCreateOpen(true); setCreateForm(EMPTY_CREATE); setFormErrors({}); }}
        >
          Nuevo Usuario
        </Button>
      </div>

      {/* Stats */}
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
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            placeholder="Buscar usuarios por nombre, correo o usuario..."
            style={{ maxWidth: 448 }}
          />
        </div>

        {/* Table */}
        <DataTable
          columns={columns}
          rows={pageRows}
          rowKey={(row) => row.uuid}
          loading={usersLoading}
          emptyState={
            <EmptyState
              hasSearch={!!search}
              message="Aún no hay usuarios registrados en el sistema."
              searchMessage="No se encontraron usuarios que coincidan con la búsqueda."
              actionLabel="Nuevo Usuario"
              onAction={() => setCreateOpen(true)}
            />
          }
        />

        {/* Pagination */}
        {!usersLoading && (
          <div className="users-page__pagination">
            <p className="users-page__pagination-info">
              Mostrando {pageRows.length} de {filtered.length} usuario{filtered.length !== 1 ? 's' : ''}
              {search && ` (filtrado de ${users.length})`}
            </p>
            <div className="users-page__pagination-controls">
              <button
                type="button"
                className="users-page__page-btn"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={safePage === 0}
              >
                Anterior
              </button>
              <button
                type="button"
                className="users-page__page-btn"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={safePage >= totalPages - 1}
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
        onClose={() => { setCreateOpen(false); setFormErrors({}); }}
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
        onClose={() => { setEditUser(null); setFormErrors({}); }}
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

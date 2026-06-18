import { useState, useEffect } from 'react';
import { Plus, Pencil, Trash2 } from 'lucide-react';

import { useClassrooms } from '../../../hooks/useClassrooms';
import { useDebouncedValue } from '../../../hooks/useDebouncedValue';
import { DEFAULT_PAGE_SIZE } from '../../../utils/queryUtils';

import Button from '../../../components/Button/Button';
import Card from '../../../components/Card/Card';
import Buscador from '../../../components/Buscador/Buscador';
import DataTable from '../../../components/DataTable/DataTable';
import Badge from '../../../components/Badge/Badge';
import EmptyState from '../../../components/EmptyState/EmptyState';
import FormModal from '../../../components/FormModal/FormModal';
import Input from '../../../components/Input/Input';
import Select from '../../../components/Select/Select';

import './ClassroomsPage.css';

function typeLabel(type) {
  if (!type) return '—';
  switch (type) {
    case 'AULA': return 'Aula';
    case 'AUDITORIO': return 'Auditorio';
    case 'LABORATORIO': return 'Laboratorio';
    case 'SALA_SEMINARIOS': return 'Sala de seminarios';
    default: return type;
  }
}

export default function ClassroomsPage() {
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);
  const search = useDebouncedValue(searchInput, 300);

  useEffect(() => { setPage(0); }, [search]);

  const { classrooms, totalElements, totalPages, loading, createMutation, updateMutation } = useClassrooms({
    search,
    page,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name',
    direction: 'asc',
  });

  // Stats computed from the returned page (for full counts consider fetching all pages)
  const total = totalElements;
  const available = classrooms.filter(c => c.isActive).length;
  const notAvailable = classrooms.filter(c => !c.isActive).length;

  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null);
  const [form, setForm] = useState({ name: '', capacity: 20, type: 'AULA', description: '', isActive: true });

  function openCreate() { setForm({ name: '', capacity: 20, type: 'AULA', description: '', isActive: true }); setCreateOpen(true); }
  function closeCreate() { setCreateOpen(false); }

  function openEdit(target) { setEditTarget(target); setForm({
    name: target.name || '',
    capacity: target.capacity || 1,
    type: target.type || 'AULA',
    description: target.description || '',
    isActive: target.isActive ?? true,
  }); }
  function closeEdit() { setEditTarget(null); }

  function handleCreateSubmit() {
    createMutation.mutate({ name: form.name, capacity: Number(form.capacity), type: form.type, description: form.description, isActive: form.isActive });
    closeCreate();
  }

  function handleEditSubmit() {
    updateMutation.mutate({ uuid: editTarget.uuid, payload: { name: form.name, capacity: Number(form.capacity), type: form.type, description: form.description, isActive: form.isActive } });
    closeEdit();
  }

  const columns = [
    { key: 'name', header: 'Nombre', width: '30%', render: (r) => r.name },
    { key: 'type', header: 'Tipo', width: '18%', render: (r) => typeLabel(r.type) },
    { key: 'capacity', header: 'Capacidad', width: '12%', render: (r) => r.capacity ?? '—' },
    { key: 'description', header: 'Descripción', width: '28%', render: (r) => r.description || '—' },
    { key: 'isActive', header: 'Estado', width: '8%', render: (r) => <Badge variant={r.isActive ? 'success' : 'danger'}>{r.isActive ? 'Disponible' : 'No disponible'}</Badge> },
    { key: 'acciones', header: 'Acciones', width: '12%', align: 'right', render: (r) => (
      <div className="classrooms__actions">
        <button className="classrooms__action-btn" onClick={() => openEdit(r)} title="Editar"><Pencil size={16} /></button>
      </div>
    )},
  ];

  return (
    <div className="classrooms-page">
      <div className="classrooms-page__header">
        <div>
          <h1 className="classrooms-page__title">Gestión de Aulas</h1>
          <p className="classrooms-page__subtitle">Administra las aulas y su disponibilidad</p>
        </div>
        <Button variant="primary" size="small" iconLeft={<Plus size={18} />} onClick={openCreate}>Nueva Aula</Button>
      </div>

      <div className="classrooms-page__stats">
        <Card className="classrooms-page__stat-card">
          <div>
            <p className="classrooms-page__stat-label">Total de aulas</p>
            <p className="classrooms-page__stat-value">{total}</p>
          </div>
        </Card>
        <Card className="classrooms-page__stat-card">
          <div>
            <p className="classrooms-page__stat-label">Disponibles</p>
            <p className="classrooms-page__stat-value">{available}</p>
          </div>
        </Card>
        <Card className="classrooms-page__stat-card">
          <div>
            <p className="classrooms-page__stat-label">No disponibles</p>
            <p className="classrooms-page__stat-value">{notAvailable}</p>
          </div>
        </Card>
      </div>

      <div className="classrooms-page__table-card">
        <div className="classrooms-page__table-toolbar">
          <Buscador value={searchInput} onChange={(e) => setSearchInput(e.target.value)} placeholder="Buscar aulas por nombre o descripción..." style={{ maxWidth: 448 }} />
        </div>

        <DataTable columns={columns} rows={classrooms} rowKey={(r) => r.uuid} loading={loading} emptyState={<EmptyState hasSearch={!!searchInput} message="No hay aulas registradas" searchMessage="No se encontraron aulas que coincidan con la búsqueda." actionLabel="Nueva Aula" onAction={openCreate} />} />
      </div>

      <FormModal open={createOpen} onClose={closeCreate} title="Nueva Aula" submitLabel="Crear" onSubmit={handleCreateSubmit} loading={createMutation.isLoading}>
        <div className="classrooms-page__form-grid">
          <Input label="Nombre" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Capacidad" type="number" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} required />
          <div className="classrooms-page__form-grid--full">
            <Select label="Tipo" value={form.type} onChange={(v) => setForm({ ...form, type: v })} options={[{ value: 'AULA', label: 'Aula' }, { value: 'AUDITORIO', label: 'Auditorio' }, { value: 'LABORATORIO', label: 'Laboratorio' }, { value: 'SALA_SEMINARIOS', label: 'Sala de seminarios' }]} />
          </div>
          <div className="classrooms-page__form-grid--full">
            <Input label="Descripción" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </div>
      </FormModal>

      <FormModal open={!!editTarget} onClose={closeEdit} title="Editar Aula" submitLabel="Guardar" onSubmit={handleEditSubmit} loading={updateMutation.isLoading}>
        <div className="classrooms-page__form-grid">
          <Input label="Nombre" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Input label="Capacidad" type="number" value={form.capacity} onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })} required />
          <div className="classrooms-page__form-grid--full">
            <Select label="Tipo" value={form.type} onChange={(v) => setForm({ ...form, type: v })} options={[{ value: 'AULA', label: 'Aula' }, { value: 'AUDITORIO', label: 'Auditorio' }, { value: 'LABORATORIO', label: 'Laboratorio' }, { value: 'SALA_SEMINARIOS', label: 'Sala de seminarios' }]} />
          </div>
          <div className="classrooms-page__form-grid--full">
            <Input label="Descripción" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <Select label="Estado" value={String(form.isActive)} onChange={(v) => setForm({ ...form, isActive: v === 'true' })} options={[{ value: 'true', label: 'Disponible' }, { value: 'false', label: 'No disponible' }]} />
        </div>
      </FormModal>
    </div>
  );
}

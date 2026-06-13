export const SALAS = [
  { id: 1, label: 'Auditorio',            color: '#2563eb', capacidad: 100, computadoras: 0,  proyectores: 2 },
  { id: 2, label: 'Aula I - Multimodal',  color: '#16a34a', capacidad: 30,  computadoras: 30, proyectores: 1 },
  { id: 3, label: 'Aula II - Multimodal', color: '#9333ea', capacidad: 25,  computadoras: 25, proyectores: 1 },
  { id: 4, label: 'Aula III',             color: '#ea580c', capacidad: 40,  computadoras: 0,  proyectores: 1 },
  { id: 5, label: 'Aula IV',              color: '#f59e0b', capacidad: 35,  computadoras: 0,  proyectores: 1 },
];

export const SALA_BY_ID = Object.fromEntries(SALAS.map(s => [s.id, s]));

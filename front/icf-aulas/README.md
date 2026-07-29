# Aulas ICF — Frontend

SPA en React que consume la API REST de [`back/aulas`](../../back/aulas) para gestionar la
reserva de aulas del Instituto de Ciencias Físicas — UNAM. Para la visión general del sistema
(arquitectura, roles, flujo de negocio, despliegue completo), ver el
[README raíz del proyecto](../../README.md).

***

## Tabla de contenido

- [Tecnologías](#tecnologías)
- [Requisitos](#requisitos)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Puesta en marcha en desarrollo](#puesta-en-marcha-en-desarrollo)
- [Variables de entorno](#variables-de-entorno)
- [Scripts disponibles](#scripts-disponibles)
- [Build de producción](#build-de-producción)

***

## Tecnologías

| Categoría | Tecnología | Versión |
|---|---|---|
| Framework | React | 19.2 |
| Build tool | Vite | 8.1 |
| Ruteo | React Router | 7.18 |
| Estado de servidor / caché | TanStack Query | 5.101 |
| Validación de formularios | Zod | 4.4 |
| Calendario de disponibilidad | FullCalendar (`daygrid`, `timegrid`, `interaction`, `react`) | 6.1 |
| Gráficas de reportes | Recharts | 3.9 |
| Estilos | styled-components | 6.4 |
| Componentes UI | `@moondesignsystem/react`, Radix UI (`alert-dialog`, `tooltip`) | — |
| Notificaciones | sonner | 2.0 |
| Animaciones | motion | 12.42 |
| Exportación de reportes | jsPDF, html2canvas-pro | — |
| Decodificación de JWT (cliente) | jwt-decode | 4.0 |
| Linting | ESLint (`eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`) | 10.7 |

***

## Requisitos

| Componente | Versión requerida | Verificar con |
|---|---|---|
| Node.js | `^20.19.0` o `>=22.12.0` (rango exigido por Vite 8) | `node -v` |
| pnpm o npm | Cualquiera de los dos (ambos lockfiles están versionados) | `pnpm -v` / `npm -v` |

Usa siempre el mismo gestor de paquetes en tu máquina de trabajo: alternar entre
`pnpm install` y `npm install` sobre el mismo `node_modules` puede dejar dependencias
inconsistentes. pnpm se instala con `corepack enable` (incluido en Node.js).

***

## Estructura del proyecto

```text
src/
├── api/                 # Clientes HTTP por módulo (auth, classrooms, reservations,
│                         # resources, semesters, timeslots, users, reports) + base.js
├── components/           # Componentes reutilizables (Modal, DataTable, Select, Calendar,
│                         # Pagination, Sidebar, Toast, etc.)
├── context/              # AuthContext, ReservationContext
├── errors/                # Manejo y normalización de errores de API
├── hooks/                 # Hooks compartidos (useZodForm, useUrlFilters, usePagination,
│                         # useApiMutation, useDebouncedValue, useTableSort, useCalendar, ...)
├── layouts/               # Layouts de página (admin, maestro, público)
├── modules/
│   ├── admin/            # Pantallas exclusivas de ADMIN (usuarios, recursos, reportes)
│   ├── maestro/           # Pantallas exclusivas de TEACHER
│   ├── public/            # Login y páginas sin autenticación
│   └── shared/             # Pantallas usadas por ambos roles (aulas, reservas, semestres, perfil)
├── routes/                # PrivateRouter, PublicRouter, RoleGuard, routeConfig, routes.meta
├── schemas/                # Esquemas Zod por dominio (login, user, classroom, resource,
│                         # semester, reservation, report) — única fuente de verdad de
│                         # validación de formularios
├── styles/                 # Estilos globales
└── utils/                  # Utilidades varias (toasts, formateo, etc.)
```

***

## Puesta en marcha en desarrollo

El backend debe estar corriendo (ver [README raíz](../../README.md#2-configurar-y-correr-el-backend))
antes de levantar el frontend, ya que las peticiones apuntan a `VITE_API_URL`.

```bash
cd front/icf-aulas
cp .env.example .env.development
pnpm install    # o: npm install
pnpm dev        # o: npm run dev
```

El servidor de desarrollo queda disponible en `http://localhost:5173`.

***

## Variables de entorno

Vite solo expone al bundle las variables prefijadas con `VITE_`. Se cargan desde
`.env.development` en desarrollo (`pnpm dev`) y desde `.env.production` en build
(`pnpm build`) — ver [`.env.example`](.env.example) como plantilla.

| Variable | Descripción |
|---|---|
| `VITE_API_URL` | URL base del backend. Se hornea en el bundle en tiempo de build — no es configurable después de compilar. **Nunca debe incluir `/api`**: los módulos ya agregan `/api/v1` en su propio path (ver `src/api/base.js`). |

| Entorno | Valor típico | Notas |
|---|---|---|
| Desarrollo | `http://localhost:8080` | Backend corriendo en local con el perfil `dev`. |
| Producción, raíz del dominio | *(vacío)* | Ej. `https://aulas.fis.unam.mx` — declara `VITE_API_URL=` explícitamente; **omitir la variable** la deja en su default de `http://localhost:8080`. |
| Producción, subpath | Prefijo exacto del subpath, ej. `/~aulas_icf` | El proxy inverso debe mapear ese mismo prefijo hacia el backend (ver [Despliegue en producción](../../README.md#despliegue-en-producción) en el README raíz). |

En producción, usar `localhost` en `VITE_API_URL` es un error común: el navegador de cada
visitante resuelve `localhost` contra su propia máquina, no contra el servidor, aunque el
backend corra en el mismo host físico que sirve el frontend.

***

## Scripts disponibles

| Script | Comando | Descripción |
|---|---|---|
| `dev` | `pnpm dev` | Levanta el servidor de desarrollo de Vite en el puerto `5173` con HMR. |
| `build` | `pnpm build` | Compila el bundle de producción en `dist/`, horneando las variables `VITE_*` de `.env.production`. |
| `preview` | `pnpm preview` | Sirve localmente el contenido ya compilado de `dist/`, útil para verificar el build antes de desplegar. |
| `lint` | `pnpm lint` | Corre ESLint sobre todo el proyecto. |

***

## Build de producción

```bash
cd front/icf-aulas
# Confirma VITE_API_URL en .env.production antes de compilar — se hornea en el bundle.
pnpm install    # o: npm install
pnpm build      # o: npm run build
```

El resultado queda en `dist/`, listo para servirse como archivos estáticos. El detalle
completo del despliegue (proxy inverso, subpath, `VirtualHost` de Apache2, systemd del
backend) está documentado en la sección
[Despliegue en producción](../../README.md#despliegue-en-producción) del README raíz —
esta sección solo cubre el build del propio frontend.

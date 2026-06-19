# Peticiones del frontend al módulo de Aulas

Este documento registra los cambios solicitados por el frontend para que el módulo de aulas funcione completamente.

> **Estado:** ✅ Ambas peticiones implementadas (2026-06-18).

---

## 1. ✅ Búsqueda server-side en `GET /api/v1/classrooms`

### Problema

El frontend enviaba el parámetro `?search=<término>` en todas las peticiones de lista de aulas, pero el controlador lo ignoraba — `ClassroomController.findAll` recibía únicamente `PageCriteria`. El buscador nunca filtraba nada.

### Solución implementada

El endpoint de lista acepta ahora un parámetro opcional `search`:

```
GET /api/v1/classrooms?search=laboratorio&page=0&size=10&sort=name&direction=asc
```

Cuando `search` está presente y no es vacío, se aplica un filtro `LIKE` case-insensitive sobre `name` y `description`. El split ADMIN (todos) / MAESTRO (solo activos) se mantiene igual.

### Archivos modificados

#### `ClassroomController.java`

```java
@GetMapping
public ResponseEntity<ApiResponse<PagedResultDTO<ClassroomResponseDTO>>> findAll(
        @AuthenticationPrincipal UserDetailsImp principal,
        @RequestParam(value = "search", required = false) String search,
        @SortWhitelist(
                value = {"createdAt", "name", "capacity"},
                defaultSort = "name",
                defaultDirection = "asc")
        PageCriteria criteria) {
    if ("ADMIN".equals(principal.getRoleName()))
        return ok(classroomService.findAll(search, criteria.toPageable()));
    return ok(classroomService.findAllActive(search, criteria.toPageable()));
}
```

#### `ClassroomRepository.java`

```java
@Query("""
        SELECT c FROM Classroom c
        WHERE (LOWER(c.name)        LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
Page<Classroom> search(@Param("q") String q, Pageable pageable);

@Query("""
        SELECT c FROM Classroom c
        WHERE c.isActive = true
          AND (LOWER(c.name)        LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
Page<Classroom> searchActive(@Param("q") String q, Pageable pageable);
```

#### `ClassroomService.java`

```java
public PagedResultDTO<ClassroomResponseDTO> findAll(String search, Pageable pageable) {
    var page = (search != null && !search.isBlank())
            ? classroomRepository.search(search.trim(), pageable)
            : classroomRepository.findAll(pageable);
    return PageMapper.toDto(page, classroomMapper::toDtoList);
}

public PagedResultDTO<ClassroomResponseDTO> findAllActive(String search, Pageable pageable) {
    var page = (search != null && !search.isBlank())
            ? classroomRepository.searchActive(search.trim(), pageable)
            : classroomRepository.findByIsActiveTrue(pageable);
    return PageMapper.toDto(page, classroomMapper::toDtoList);
}
```

### Respuesta (contrato sin cambios)

```json
{
  "data": {
    "items": [ /* ClassroomResponseDTO[] */ ],
    "page": 0,
    "size": 10,
    "totalElements": 3,
    "totalPages": 1
  },
  "error": false
}
```

`totalElements` refleja el recuento filtrado (no el total global).

---

## 2. ✅ Endpoint de estadísticas `GET /api/v1/classrooms/stats`

### Problema

Las tarjetas de estadísticas de la página de aulas mostraban `—` porque el endpoint `/api/v1/classrooms/stats` no existía.

### Solución implementada

```
GET /api/v1/classrooms/stats
Authorization: Bearer <token>
Roles permitidos: ADMIN
```

#### Respuesta

```json
{
  "data": {
    "total":        12,
    "available":     9,
    "notAvailable":  3
  },
  "error": false
}
```

### Archivos modificados / creados

#### `ClassroomStatsDTO.java` (nuevo)

```java
package mx.unam.icf.aulas.modules.resources.classrooms.app.dtos;

public record ClassroomStatsDTO(long total, long available, long notAvailable) {}
```

> Los campos son `long` primitivo (consistente con `UserStatsDTO`). El `COALESCE` en la
> query garantiza que una tabla vacía devuelva `0,0,0` sin NPE.

#### `ClassroomRepository.java`

```java
@Query("""
        SELECT new mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomStatsDTO(
            COUNT(c),
            COALESCE(SUM(CASE WHEN c.isActive = true  THEN 1L ELSE 0L END), 0L),
            COALESCE(SUM(CASE WHEN c.isActive = false OR c.isActive IS NULL THEN 1L ELSE 0L END), 0L))
        FROM Classroom c
        """)
ClassroomStatsDTO fetchStats();
```

#### `ClassroomService.java`

```java
@Transactional(readOnly = true)
public ClassroomStatsDTO getStats() {
    return classroomRepository.fetchStats();
}
```

#### `ClassroomController.java`

```java
@GetMapping("/stats")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<ClassroomStatsDTO>> stats() {
    return ok(classroomService.getStats());
}
```

Spring MVC resuelve el path literal `/stats` antes que el template `/{uuid}`, sin conflicto.

---

---

## 3. ⏳ Campo `color` en el catálogo de aulas

### Estado actual

El frontend asigna un color a cada aula de forma **determinista a partir del
UUID** (hash → índice en una paleta de 8 colores). Esto garantiza que el mismo
aula siempre recibe el mismo color independientemente del orden de la lista,
del dispositivo o de cuántas aulas existan.

El cálculo vive en `src/utils/salas.js → salaColor(uuid)`. Se usa en el
Sidebar, el calendario y los modales de reserva.

### Problema

El administrador no puede controlar el color con el que aparece cada aula.
Colores chocantes o similares entre aulas cercanas no se pueden ajustar.

### Petición

Agregar un campo `color` nullable (hex `#RRGGBB`) al modelo de aula:

```
Classroom.color  VARCHAR(7) NULL
ClassroomRequestDTO.color   String (nullable, pattern #[0-9A-Fa-f]{6})
ClassroomResponseDTO.color  String (nullable)
```

El admin elegiría el color en el formulario de creación/edición mediante un
`<input type="color">` accesible (con etiqueta visible y valor hex editable por
teclado).

### Compatibilidad / migración sin disrupción

El frontend ya está preparado para adoptarlo de forma no disruptiva:

```js
// src/utils/salas.js
color: classroom.color ?? salaColor(classroom.uuid),
```

Las aulas sin color asignado seguirán usando el color determinista por UUID
hasta que el admin defina uno. No hay cambio de comportamiento para datos
existentes.

### Beneficio

Identidad visual estable y controlada por el admin, consistente en el
calendario, el sidebar y cualquier reporte futuro.

---

## Resumen de archivos

| Acción    | Archivo                                                   |
|-----------|-----------------------------------------------------------|
| Creado    | `…/classrooms/app/dtos/ClassroomStatsDTO.java`            |
| Modificado | `…/classrooms/infrastructure/ClassroomController.java`   |
| Modificado | `…/classrooms/infrastructure/ClassroomRepository.java`   |
| Modificado | `…/classrooms/app/ClassroomService.java`                 |

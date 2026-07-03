# Guía de Implementación de Endpoints — Aulas ICF

> Referencia interna para el equipo de backend. Documenta las convenciones, capas y
> patrones que **ya están en producción** en el proyecto. Seguir esta guía garantiza
> coherencia con el código existente y evita repetir errores ya resueltos.

---

## Índice

1. [Estructura de módulos](#1-estructura-de-módulos)
2. [Capas y responsabilidades](#2-capas-y-responsabilidades)
3. [Formato de respuesta — `ApiResponse<T>`](#3-formato-de-respuesta--apiresponset)
4. [Controladores](#4-controladores)
5. [DTOs](#5-dtos)
6. [Servicios de aplicación](#6-servicios-de-aplicación)
7. [Repositorios](#7-repositorios)
8. [Manejo de errores](#8-manejo-de-errores)
9. [Seguridad](#9-seguridad)
10. [Paginación](#10-paginación)
11. [Validación de entrada](#11-validación-de-entrada)
12. [Javadoc](#12-javadoc)
13. [Pruebas](#13-pruebas)
14. [Checklist antes de un PR](#14-checklist-antes-de-un-pr)

---

## 1. Estructura de módulos

Cada funcionalidad vive en su propio paquete bajo `modules/`. La estructura interna es
idéntica en todos los módulos:

```
modules/
└── <nombre-modulo>/
    ├── domain/          ← entidades JPA, enums, excepciones de dominio
    ├── app/             ← servicios, DTOs, mappers, lógica de negocio
    │   └── dtos/
    └── infrastructure/  ← controladores REST, repositorios Spring Data
```

El código de infraestructura compartida (manejo de errores, paginación, respuestas) vive
bajo `kernel/`.

**Regla:** un módulo no puede importar clases de `infrastructure/` de otro módulo.
La comunicación entre módulos se hace a través de las clases `app/` (servicios).

---

## 2. Capas y responsabilidades

| Capa | Paquete | Qué hace | Qué NO hace |
|---|---|---|---|
| **Domain** | `domain/` | Define entidades, enums, invariantes del negocio | Conoce la BD o HTTP |
| **Application** | `app/` | Orquesta casos de uso, valida reglas de negocio | Conoce HTTP, `HttpServletRequest`, etc. |
| **Infrastructure** | `infrastructure/` | Expone HTTP, habla con la BD | Contiene lógica de negocio |

---

## 3. Formato de respuesta — `ApiResponse<T>`

**Todas** las respuestas usan el wrapper `ApiResponse<T>`. El JSON siempre tiene esta forma:

```json
{
  "message": "Operation successfully completed without any errors.",
  "data":    { ... },
  "error":   false
}
```

### Factories disponibles

```java
// 200 — con payload
ApiResponse.success(data)

// 200 — mensaje personalizado sin payload
ApiResponse.successMessage("Recurso eliminado correctamente.")

// 200 — mensaje personalizado con payload
ApiResponse.success("Reserva creada.", data)

// Error — siempre construido por GlobalExceptionHandler, nunca en el controlador
ApiResponse.error("Mensaje de error.")
```

### Regla importante

Los controladores **nunca** construyen `ApiResponse.error(...)` manualmente.
Los errores se emiten lanzando la excepción apropiada; el `GlobalExceptionHandler` los convierte.

---

## 4. Controladores

### 4.1 Convenciones generales

```java
@RestController
@RequestMapping(value = "/api/v1/<recurso>", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class MiController implements ResponseHandler {   // ← siempre implementa ResponseHandler

    private final MiService service;

    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<MiResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(service.findByUuid(uuid));             // ← helper del ResponseHandler
    }
}
```

### 4.2 ResponseHandler — helpers disponibles

```java
ok(data)              // 200 con payload
ok(message)           // 200 sin payload, mensaje personalizado
ok(message, data)     // 200 con payload y mensaje personalizado
created(data)         // 201 con payload
created()             // 201 sin payload
```

### 4.3 Verbos HTTP y paths

| Operación | Verbo | Path |
|---|---|---|
| Listar (paginado) | `GET` | `/api/v1/<recurso>` |
| Obtener uno | `GET` | `/api/v1/<recurso>/{uuid}` |
| Crear | `POST` | `/api/v1/<recurso>` |
| Actualizar parcial | `PATCH` | `/api/v1/<recurso>/{uuid}` |
| Eliminar | `DELETE` | `/api/v1/<recurso>/{uuid}` |
| Acción específica | `PATCH` | `/api/v1/<recurso>/{uuid}/<accion>` |
| Consulta agregada (sin ID) | `GET` | `/api/v1/<recurso>/<sub-recurso>` |

Los identificadores públicos son siempre `UUID`, nunca IDs numéricos internos.

### 4.4 Fechas en query params

```java
@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from
```

El cliente envía `?from=2025-03-01`. Sin `@DateTimeFormat` Spring no sabe el formato.

### 4.5 Enums en query params

Preferir recibir el enum directamente cuando todos los valores son válidos:

```java
@RequestParam(required = false) ReservInstanceStatus status
```

Cuando se necesita un mensaje de error personalizado, recibir como `String` y parsear
en el servicio (ver [`GlobalExceptionHandler`](#8-manejo-de-errores) — `IllegalArgumentException` → 400).

---

## 5. DTOs

### 5.1 Usar `record`

```java
public record MiRequestDTO(
        @NotNull(message = "El campo X es requerido")
        UUID campoX,

        @Size(max = 150, message = "Máximo 150 caracteres")
        String campoY
) {}
```

Los `record` son inmutables por defecto. No usar clases con setters salvo que haya una
razón técnica concreta (e.g., deserialización especial).

### 5.2 Separar Request y Response

- **Request**: campos que el cliente envía; incluye anotaciones `@Valid`.
- **Response**: campos que el servidor devuelve; sin anotaciones de validación.

No reutilizar el mismo DTO en ambas direcciones.

### 5.3 Identificadores públicos

Los responses siempre exponen `UUID`, nunca `Long id`. El ID interno es un detalle
de implementación que no debe filtrarse al cliente.

### 5.4 Nullabilidad explícita

Si un campo puede ser `null` en el response, usar el tipo wrapper (`Double`, `Long`, `Boolean`)
y documentar cuándo es null en el Javadoc del record.

---

## 6. Servicios de aplicación

### 6.1 Anotaciones obligatorias

```java
@Service
@RequiredArgsConstructor
public class MiService {

    @Transactional(readOnly = true)
    public MiResponseDTO findByUuid(UUID uuid) { ... }

    @Transactional
    public MiResponseDTO save(MiRequestDTO dto) { ... }
}
```

- Operaciones de sólo lectura → `@Transactional(readOnly = true)`.
- Mutaciones → `@Transactional` (sin flag).
- **No** poner `@Transactional` en el controlador.

### 6.2 Validación de reglas de negocio

```java
// Regla de negocio violada → DomainException (400)
if (classroom.isUnavailable())
    throw new DomainException("El aula ya está ocupada en ese horario.");

// Recurso no encontrado → ResourceNotFoundException (404)
Classroom classroom = classroomRepo.findByUuid(uuid)
        .orElseThrow(() -> new ResourceNotFoundException("Aula no encontrada: " + uuid));

// Parámetro inválido del cliente → IllegalArgumentException (400)
// Útil cuando el servicio valida algo que no pasa por @Valid (e.g., un String parseado)
throw new IllegalArgumentException("scope debe ser MENSUAL o SEMESTRAL");
```

### 6.3 Inyección de dependencias

Solo a través del constructor. `@RequiredArgsConstructor` de Lombok genera el constructor
con todos los campos `final`. No usar `@Autowired` en campo.

---

## 7. Repositorios

### 7.1 Repositorio de dominio (CRUD estándar)

```java
public interface MiRepository extends JpaRepository<MiEntidad, Long> {

    Optional<MiEntidad> findByUuid(UUID uuid);
}
```

Extender `JpaRepository<Entidad, Long>` para los repositorios del módulo principal.

### 7.2 Repositorio de agregación (sólo lectura)

Para consultas que agregan datos de varias entidades (dashboards, reportes), crear un
repositorio dedicado que extienda la interfaz mínima:

```java
public interface MiAggRepository extends Repository<EntidadPrincipal, Long> {

    @Query("SELECT COUNT(e) FROM MiEntidad e WHERE e.estado = :estado")
    long contarPorEstado(@Param("estado") MiEstado estado);
}
```

Esto evita que el repositorio de dominio se contamine con queries de reporting.

### 7.3 Proyecciones en lugar de entidades

Para queries que devuelven columnas específicas, usar proyecciones de interfaz:

```java
// Proyección
public interface MiVista {
    String getNombre();
    long getTotal();
}

// Query
@Query("SELECT e.nombre AS nombre, COUNT(e) AS total FROM MiEntidad e GROUP BY e.id")
List<MiVista> topPorTotal(Limit limit);
```

Los alias en la query (`AS nombre`) deben coincidir exactamente con los nombres de los
métodos de la proyección (`getNombre` → alias `nombre`).

### 7.4 Límites con `Limit`

Para top-N sin paginación completa, usar `org.springframework.data.domain.Limit`:

```java
List<MiVista> topPorTotal(Limit limit);

// En el servicio:
repo.topPorTotal(Limit.of(5));
```

### 7.5 JPQL sobre SQL nativo

Preferir JPQL siempre que sea posible. Usar `@Query(nativeQuery = true)` sólo si la
consulta requiere funciones específicas del dialecto (window functions, full-text search)
que JPQL no puede expresar.

### 7.6 Evitar N+1

No cargar listas de entidades para luego iterar y agregar en la JVM. Si el resultado
es un escalar o un conteo, calcularlo en la base de datos:

```java
// ❌ Anti-patrón: carga todas las instancias a la JVM para contar
List<ReservInstance> instances = repo.findByGrupo(grupoId);
long count = instances.size(); // N+1 cuando hay asociaciones lazy

// ✅ Correcto: un COUNT en la BD
long count = repo.countByGrupoId(grupoId);
```

---

## 8. Manejo de errores

El `GlobalExceptionHandler` convierte automáticamente las excepciones en respuestas HTTP.
**No agregar try/catch en controladores ni servicios** salvo para encapsular una
causa raíz diferente.

### Mapa de excepciones → HTTP

| Excepción | HTTP | Cuándo lanzarla |
|---|---|---|
| `ResourceNotFoundException` | 404 | Entidad no encontrada por UUID/nombre |
| `DomainException` | 400 | Regla de negocio violada en la capa de dominio/aplicación |
| `IllegalArgumentException` | 400 | Parámetro de entrada inválido que no pasa por `@Valid` (ej. enum como String, UUID inválido) |
| `ReservationConflictException` | 409 | Conflicto de slot (incluye `ConflictDetailDTO`) |
| `AccessDeniedException` | 403 | El usuario no tiene permiso para esa operación específica |

### Mensajes de error en `dev` vs producción

Los handlers de `IllegalArgumentException`, `MethodArgumentNotValidException` y las
excepciones de mail incluyen el mensaje original en perfil `dev` y un mensaje genérico
en producción. No asumir que el cliente siempre verá el texto exacto del throw.

---

## 9. Seguridad

### 9.1 Acceso global por rol

```java
// En el controlador
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/ruta-privada")
public ResponseEntity<...> metodoPorRol() { ... }
```

El sistema de roles usa identificadores internos en mayúsculas (`ADMIN`, `MAESTRO`).
**No** usar las versiones en español (`ADMINISTRADOR`) — `@PreAuthorize` compara con el
identificador del enum, no con el nombre de display.

### 9.2 Acceso mixto (usuario autenticado + lógica de ownership)

Cuando un Maestro puede operar sobre sus propios recursos pero no los ajenos, el
control de ownership va en el servicio (no como un `@PreAuthorize` complejo):

```java
// Controlador
@GetMapping("/user/{userUuid}")
public ResponseEntity<...> findByUser(
        @PathVariable UUID userUuid,
        @AuthenticationPrincipal UserDetailsImp principal) {
    if (!"ADMIN".equals(principal.getRoleName()) && !userUuid.equals(principal.getUuid()))
        throw new AccessDeniedException("You can only view your own reservations");
    return ok(service.findByUser(userUuid, ...));
}
```

### 9.3 Usuario autenticado en el servicio

El controlador extrae el UUID del principal y lo pasa al servicio como parámetro:

```java
// Controlador
public ResponseEntity<...> save(
        @Valid @RequestBody MiRequestDTO dto,
        @AuthenticationPrincipal UserDetailsImp principal) {
    return created(service.save(dto, principal.getUuid()));
}

// Servicio — no conoce nada de Spring Security
public MiResponseDTO save(MiRequestDTO dto, UUID userUuid) { ... }
```

El servicio **nunca** inyecta `SecurityContextHolder` directamente.

---

## 10. Paginación

### 10.1 Patrón estándar para endpoints paginados

```java
@GetMapping
public ResponseEntity<ApiResponse<PagedResultDTO<MiResponseDTO>>> findAll(
        @SortWhitelist(
                value = {"createdAt", "nombre", "estado"},
                defaultSort = "createdAt",
                defaultDirection = "desc")
        PageCriteria criteria,
        @RequestParam(required = false) String search) {
    return ok(service.findAll(search, criteria.toPageable()));
}
```

Los parámetros de paginación (`page`, `size`, `sort`, `direction`) los resuelve
automáticamente `PageCriteriaArgumentResolver`. No agregarlos como `@RequestParam`.

### 10.2 `PagedResultDTO` en el servicio

```java
public PagedResultDTO<MiResponseDTO> findAll(String search, Pageable pageable) {
    Page<MiEntidad> page = repo.findAll(search, pageable);
    List<MiResponseDTO> items = page.getContent().stream()
            .map(mapper::toResponse)
            .toList();
    return PagedResultDTO.of(items, page.getTotalElements(), page.getTotalPages(),
            pageable.getPageNumber(), pageable.getPageSize());
}
```

### 10.3 `@SortWhitelist` — campos permitidos

Sólo incluir en el whitelist campos que existen como propiedades de la entidad JPA
(camelCase exacto). Un valor fuera de la lista devuelve 400 automáticamente.

### 10.4 Cuándo NO paginar

Endpoints de tipo lookup/disponibilidad (e.g., `GET /availability`, `GET /statistics`)
devuelven `List<T>` o un DTO plano directamente, no `PagedResultDTO`.

---

## 11. Validación de entrada

### 11.1 Bean Validation en RequestDTO

```java
public record CrearAulaDTO(

        @NotBlank(message = "El nombre es requerido")
        @Size(max = 100, message = "Máximo 100 caracteres")
        String nombre,

        @NotNull(message = "La capacidad es requerida")
        @Positive(message = "La capacidad debe ser positiva")
        Integer capacidad,

        @NotNull(message = "El UUID del edificio es requerido")
        UUID edificioUuid
) {}
```

En el controlador, anotar el parámetro con `@Valid`:

```java
public ResponseEntity<...> save(@Valid @RequestBody CrearAulaDTO dto) { ... }
```

Los errores de validación son capturados por `GlobalExceptionHandler` y devueltos como 400.

### 11.2 Validaciones que no caben en anotaciones

Reglas que dependen de la base de datos o de múltiples campos se validan en el servicio
lanzando `DomainException` o `IllegalArgumentException` (ver [sección 8](#8-manejo-de-errores)).

### 11.3 Normalización de strings opcionales

Cuando un campo de texto opcional puede venir vacío o con espacios:

```java
// En el servicio, antes de persistir
String titulo = (dto.titulo() == null || dto.titulo().isBlank()) ? null : dto.titulo().trim();
```

Nunca persistir strings vacíos; normalizar a `null`.

---

## 12. Javadoc

Todo método y clase pública lleva Javadoc en **inglés**. Los comentarios explican el
**por qué**, no el qué (el código ya dice qué hace).

### Clase

```java
/**
 * Application service that manages classroom reservations.
 *
 * <p>Enforces the business rule that a classroom cannot be double-booked
 * within the same time slot on the same date.</p>
 *
 * @see ReservInstanceRepository
 */
```

### Método

```java
/**
 * Cancels a reservation as its owner.
 *
 * <p>Only the user whose group owns the instance may cancel it.
 * An attempt by a different user throws {@link AccessDeniedException}.</p>
 *
 * @param uuid      public UUID of the reservation instance to cancel
 * @param userUuid  UUID of the authenticated user requesting the cancellation
 * @return the updated reservation with status {@code CANCELLED_BY_USER}
 * @throws ResourceNotFoundException if no instance matches {@code uuid}
 * @throws AccessDeniedException     if {@code userUuid} does not own the instance
 */
public ReservInstanceResponseDTO cancelByUser(UUID uuid, UUID userUuid) { ... }
```

### Record / DTO

Documentar directamente en los parámetros del record:

```java
/**
 * Response payload for a classroom reservation instance.
 *
 * @param uuid      public identifier of this instance; never null
 * @param title     optional label; {@code null} when not provided by the user
 * @param reassigned {@code true} when an admin reassigned this instance;
 *                   the status remains {@code ACTIVE} after reassignment
 */
public record MiResponseDTO(UUID uuid, String title, Boolean reassigned) {}
```

---

## 13. Pruebas

El proyecto sigue tres tipos de pruebas. No es necesario mezclarlos en el mismo archivo.

### 13.1 Pruebas de unidad — lógica de negocio (`@ExtendWith(MockitoExtension.class)`)

Para servicios y componentes con dependencias:

```java
@ExtendWith(MockitoExtension.class)
class MiServiceTest {

    @Mock private MiRepository repo;
    @InjectMocks private MiService service;

    @Test
    void save_throwsDomainException_whenAulaOcupada() {
        when(repo.existsConflict(any(), any())).thenReturn(true);

        assertThatExceptionOfType(DomainException.class)
                .isThrownBy(() -> service.save(buildRequest()))
                .withMessage("El aula ya está ocupada en ese horario.");
    }
}
```

**Trampas comunes con Mockito:**
- No crear mocks de proyecciones (`mockDateCount(...)`) dentro de `List.of(...)` en el
  argumento de `thenReturn(...)`: el `when()` interno interrumpe el `when()` externo.
  Crear las variables antes.
- Si un stub en `@BeforeEach` no se usa en todos los tests, marcarlo con
  `lenient().when(...)` para evitar `UnnecessaryStubbingException`.
- Usar `verify(repo, times(1)).metodo(...)` para confirmar que sólo se llama una vez
  cuando el conteo importa.

### 13.2 Prueba slice web — capa HTTP (`standaloneSetup`)

Para verificar routing, binding de parámetros y manejo de errores sin levantar el
contexto completo de Spring:

```java
@ExtendWith(MockitoExtension.class)
class MiControllerTest {

    @Mock private MiService service;
    @Mock private Environment env;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MiController controller = new MiController(service);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .build();
    }

    @Test
    void findByUuid_returns200_withCorrectShape() throws Exception {
        when(service.findByUuid(any())).thenReturn(buildDto());

        mockMvc.perform(get("/api/v1/mi-recurso/{uuid}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(false))
                .andExpect(jsonPath("$.data.uuid").exists());
    }

    @Test
    void findByUuid_returns400_whenServiceThrowsIllegalArgument() throws Exception {
        when(service.findByUuid(any()))
                .thenThrow(new IllegalArgumentException("UUID inválido"));
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        mockMvc.perform(get("/api/v1/mi-recurso/{uuid}", UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }
}
```

`@PreAuthorize` **no** se prueba en slice tests (no hay Spring Security en
`standaloneSetup`). Es aceptable; la seguridad se cubre con pruebas de integración.

### 13.3 Prueba de integración — `@SpringBootTest`

Para verificar comportamiento end-to-end (BD real, contexto completo). Solo para casos
que realmente requieren la base de datos. Ver `AulasApplicationTests` como referencia.

---

## 14. Checklist antes de un PR

Antes de abrir un pull request con un endpoint nuevo, verificar:

- [ ] El path sigue el patrón `/api/v1/<módulo>[/<sub-recurso>][/{uuid}]`
- [ ] El controlador implementa `ResponseHandler` y usa `ok(...)` / `created(...)`
- [ ] Nunca se construye `ApiResponse.error(...)` en el controlador
- [ ] Los identificadores públicos en URLs y DTOs son `UUID`, no `Long`
- [ ] Los campos opcionales en el request son `required = false` o `@Size` tolerante a null
- [ ] Las fechas en query params llevan `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)`
- [ ] El servicio tiene `@Transactional(readOnly = true)` en lecturas y `@Transactional` en escrituras
- [ ] Los errores se lanzan como `DomainException`, `ResourceNotFoundException` o `IllegalArgumentException`; nunca se construyen respuestas de error manualmente
- [ ] Los queries de agregación devuelven proyecciones o escalares; no listas de entidades para iterar en la JVM
- [ ] Si hay paginación, se usa `PageCriteria` + `@SortWhitelist` + `PagedResultDTO`
- [ ] Todos los métodos y clases públicas tienen Javadoc en inglés con `@param` y `@return`
- [ ] Hay al menos un test unitario por caso de uso (happy path + al menos un error)
- [ ] Hay al menos un test de capa web que verifique el binding de parámetros y el envelope `ApiResponse`
- [ ] `./mvnw test` pasa sin errores ni warnings de Mockito (`UnnecessaryStubbingException`, `UnfinishedStubbingException`)

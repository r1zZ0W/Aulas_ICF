# Auditoría de Seguridad — Sistema de Aulas ICF

**Fecha:** 2026-07-04
**Alcance:** Backend Spring Boot (`back/aulas`) + Frontend React (`front/icf-aulas`)
**Tipo:** Revisión manual dirigida por código + herramientas automatizadas disponibles
**Metodología:** Verificación de controles contra checklist OWASP (Backend §1–17, Frontend §1–15, Arquitectura). Cada fila cita evidencia concreta (`archivo:línea`). Sin métricas inventadas.

> **Leyenda de estado:** ✅ Cubierto · ⚠ Parcial / mejorable · ❌ No cubierto · ➖ No aplica

---

## 1. Resumen ejecutivo

El sistema tiene una **postura de seguridad madura y por encima del promedio** para un proyecto de estadía. Los controles fundamentales están bien implementados: BCrypt (cost 12), JWT firmado con HMAC (con `iss`/`aud`/`jti`/`exp`), revocación por blacklist, rotación de refresh tokens, rate limiting con bucket4j, bloqueo por intentos fallidos, respuestas de error genéricas en producción, consultas 100 % parametrizadas (sin SQL/JPQL injection), validación de DTOs, y secretos externalizados a variables de entorno.

Los hallazgos se concentran en **autorización a nivel de objeto/función (BOLA/BFLA)** en el módulo de reservas y en **endurecimiento defensivo** (headers, política de contraseñas, almacenamiento de token en el cliente). No se encontró ninguna vulnerabilidad crítica de ejecución remota, inyección o exposición de secretos versionados.

### Conteo por severidad

| Severidad | Cantidad | Hallazgos |
|-----------|----------|-----------|
| 🔴 Alto   | 1 | Listado global de reservas sin control de rol (BFLA) |
| 🟠 Medio  | 3 | IDOR de lectura por UUID (reservas y grupos); JWT en localStorage; política de contraseñas sin complejidad |
| 🟡 Bajo   | 7 | Headers de hardening; `show-sql=true` en prod; página "unpaged" de 1000; lockout dirigido; esbuild dev; traversal no defendido en capa de storage; enumeración por mensaje de lockout |
| ⚪ Informativo | 5 | Secreto JWT dev hardcodeado; devtools en pom; CORS con credenciales por env; PDF report sin token en nueva pestaña; logging de username |

### Top 5 acciones recomendadas (priorizadas)

1. **Restringir `GET /api/v1/reservations`** a ADMIN (o filtrar por el usuario autenticado). Hoy cualquier MAESTRO lista todas las reservas del sistema. → §2 Auth-BFLA
2. **Añadir verificación de propiedad** en `GET /reservations/{uuid}` y `GET /reservation-groups/{uuid}` (o restringir a ADMIN). → §2 IDOR
3. **Endurecer política de contraseñas** (exigir complejidad además de longitud). → §1
4. **Completar headers de seguridad** (`Referrer-Policy`, `Permissions-Policy`, CSP con `default-src`/`frame-ancestors`/`base-uri`). → §14
5. **Poner `spring.jpa.show-sql=false` en producción** (hoy se hereda `true`). → §7

---

## 2. Backend

### §1 Autenticación

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Login sin rate limiting (fuerza bruta) | ✅ | `RateLimitFilter.java:62` protege `/auth/login`, `/forgot-password`, `/refresh`, `/reset-password`, `/users/register`; 5 req/min por IP (`application.properties:64`) | Bajo | Sin acción |
| Bloqueo por intentos fallidos | ✅ | `LoginAttemptService.java`: 5 fallos → bloqueo 10 min por usuario; reset en éxito (`AuthUtils.java:57-73`) | Bajo | Sin acción |
| Contraseñas sin hash seguro / MD5-SHA1 | ✅ | `AuthConfig.java:30` `BCryptPasswordEncoder(12)` | Bajo | Sin acción |
| JWT sin expiración / expiración excesiva | ✅ | `jwt.expiration=3600000` (1 h) auth, refresh 7 d, reset 1 h (`JwtProvider.java:107`) | Bajo | Sin acción |
| JWT con secreto débil | ✅ | `Keys.hmacShaKeyFor` exige ≥256 bits o lanza `WeakKeyException` al arranque; prod requiere `${JWT_SECRET}` sin default (`application.properties:42`) | Bajo | Documentar longitud mínima (≥32 chars) en el runbook de despliegue |
| JWT sin validación issuer/audience | ✅ | `JwtProvider.java:44-45,187-188` emite `iss`/`aud`; `parseClaims` verifica firma (`:207-213`) | Bajo | *(Opcional)* validar explícitamente `iss`/`aud` en `parseClaims` con `.requireIssuer()/.requireAudience()` |
| Refresh tokens inseguros | ✅ | Rotación con blacklist del token usado (`AuthService.java:103-119`) | Bajo | Sin acción |
| Enumeración de usuarios | ✅ | Mensaje neutro único en todo fallo (`AuthUtils.java:26,72`); `forgotPassword` siempre 200 (`AuthController.java:78`) | Bajo | Sin acción |
| Password reset inseguro / token reutilizable | ✅ | Token `reset` de un solo uso; se "quema" en blacklist tras usarse (`AuthService.java:172-180`) | Bajo | Sin acción |
| Política de contraseñas | ⚠ | Solo longitud 8–128 (`RegisterRequestDTO.java:58`, `ResetPasswordRequestDTO.java:11`); sin exigir mayúscula/dígito/símbolo | Medio | Añadir `@Pattern` de complejidad o validador de fortaleza en registro y reset |
| Enumeración por mensaje de bloqueo | ⚠ | Cuenta bloqueada devuelve 429 (`GlobalExceptionHandler.java:113`) vs 401 en credenciales; el lockout es por username enviado (no revela existencia real) | Bajo | Aceptable; opcional unificar a un mismo código para no distinguir estado de bloqueo |

### §2 Autorización

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| **BFLA — listado global de reservas** | 🔴 ❌ | `ReservInstanceController.java:84` `findAll` **no** tiene `@PreAuthorize`; su gemelo `ReservationGroupController.java:49-50` sí exige ADMIN. Un MAESTRO puede `GET /api/v1/reservations` y ver reservas de todos (aula, fecha, nombre completo del docente) | **Alto** | Añadir `@PreAuthorize("hasRole('ADMIN')")` o forzar el filtro `userUuid` al principal cuando no es ADMIN |
| **IDOR lectura — reserva por UUID** | 🟠 ⚠ | `ReservInstanceController.java:107` + `ReservInstanceService.java:129` no verifican propiedad; cualquier autenticado lee cualquier instancia por UUID (mitigado: UUID aleatorio, no enumerable) | Medio | Verificar `instance.group.user.uuid == principal` salvo ADMIN |
| **IDOR lectura — grupo por UUID** | 🟠 ⚠ | `ReservationGroupController.java:66` `findByUuid` sin verificación de propiedad | Medio | Mismo patrón de verificación de propiedad |
| IDOR — reservas de otro usuario (listado) | ✅ | `ReservInstanceController.java:151` y `ReservationGroupController.java:89` verifican `userUuid == principal` salvo ADMIN | Bajo | Sin acción |
| Escalada vertical (rol vía self-edit) | ✅ | `UserSelfEditRequestDTO` no expone `roleId`; cambio de password en self-edit solo ADMIN (`UserService.java:315`) | Bajo | Sin acción |
| Escalada horizontal (crear/cancelar ajeno) | ✅ | `save`/`cancelByUser`/`upload` verifican propiedad del grupo (`ReservInstanceService.java:373,482`; `StudentListService.java:105`) | Bajo | Sin acción |
| Endpoints sin autorización | ⚠ | Método-security activo (`MainSecurity.java:43`), `anyRequest().authenticated()` (`:80`); pero varios endpoints de reservas quedan solo "authenticated" sin chequeo de rol/propiedad (ver filas BFLA/IDOR) | Medio | Revisar cada endpoint sin `@PreAuthorize` y decidir rol o scoping |
| Permisos solo en frontend | ✅ | Autorización real en backend (`@PreAuthorize` + checks de servicio); rol tomado del claim firmado, no del cliente (`JwtAuthenticationFilter.java:83-87`) | Bajo | Sin acción |
| Bypass por manipulación de request (rol) | ✅ | El rol proviene del JWT firmado con HMAC; alterarlo invalida la firma (`JwtProvider.java:126-138`) | Bajo | Sin acción |

### §3 Validación de entradas

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| SQL Injection | ✅ | Sin SQL nativo; todo Spring Data / JPQL parametrizado | Bajo | Sin acción |
| JPQL Injection | ✅ | Todas las `@Query` usan parámetros nombrados `:x` (p. ej. `ReservInstanceRepository.java`, `UserRepository.java:74`); ningún concat de input | Bajo | Sin acción |
| Native Query Injection | ✅ | No existen `nativeQuery = true` en el código de producción | Bajo | Sin acción |
| Filtro dinámico (búsqueda) seguro | ✅ | `ReservInstanceSpecification.java:75-89` usa Criteria API con parámetros ligados (`cb.like`), no concatenación | Bajo | Sin acción |
| No validar parámetros / longitudes / tipos | ✅ | DTOs con `@NotBlank`/`@Size`/`@Email`/`@Pattern` (`RegisterRequestDTO`, `UserUpdateRequestDTO`, etc.); tipos fuertes (`UUID`, `LocalDate`, enums) | Bajo | Sin acción |
| Strings enormes (DoS) | ✅ | `@Size(max=…)` en todos los campos de texto; password máx 128 evita abuso de BCrypt | Bajo | Sin acción |
| Enteros extremos / negativos | ✅ | Paginación acotada (`PageCriteriaArgumentResolver.java:107,118`); IDs de time-slot validados contra BD | Bajo | Sin acción |
| JSON mal formado | ✅ | `HttpMessageNotReadableException` → 400 genérico (`GlobalExceptionHandler.java:198`) | Bajo | Sin acción |
| Mass Assignment / binding peligroso | ✅ | Copia explícita campo a campo desde DTOs; nunca se bindea la entidad directamente (`UserService.java:92-111,209-227`) | Bajo | Sin acción |

### §4 Persistencia (JPA/Hibernate)

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Queries concatenadas | ✅ | No existen | Bajo | Sin acción |
| Uso inseguro de EntityManager | ✅ | No se usa `EntityManager` con input crudo; todo vía repos/Criteria | Bajo | Sin acción |
| Lazy loading exponiendo info | ✅ | Respuestas van por DTOs (MapStruct); no se serializan entidades JPA | Bajo | Sin acción |
| Cascade / borrados accidentales | ✅ | Borrado en orden FK-safe y transaccional (`UserService.java:253-274`) | Bajo | Sin acción |
| Relaciones sin validar permisos | ⚠ | Ver IDOR §2 (lectura por UUID) | Medio | Igual que §2 |

### §5 API REST

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Endpoints públicos innecesarios | ✅ | Solo `/api/v1/auth/**` y `/error` públicos (`MainSecurity.java:73`); Swagger solo en perfil dev (`:77-78`) | Bajo | Sin acción |
| Verbos HTTP incorrectos | ✅ | GET lectura, POST creación, PUT/PATCH mutación, DELETE borrado; consistente | Bajo | Sin acción |
| Info sensible en GET | ⚠ | El PDF report se abre en pestaña nueva por URL sin token (`reports.js:90-95`); el endpoint sí exige ADMIN (`ReportController.java:54`) → no hay exposición, pero la descarga probablemente falla por 401 | Bajo | Descargar vía fetch con `Authorization` y `blob`, no `window.open` |
| Versionado inexistente | ✅ | Prefijo `/api/v1` en todos los controladores | Bajo | Sin acción |
| Errores HTTP incorrectos | ✅ | Mapeo coherente 400/401/403/404/409/422/429/500 (`GlobalExceptionHandler.java`) | Bajo | Sin acción |
| StackTrace / respuestas demasiado detalladas | ✅ | `include-stacktrace=never` (`application.properties:56`); mensajes genéricos en prod, detalle solo en dev (`GlobalExceptionHandler.java:187-191`) | Bajo | Sin acción |

### §6 Manejo de errores

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Excepciones sin controlar | ✅ | Fallback `@ExceptionHandler(Exception.class)` (`GlobalExceptionHandler.java:280`) | Bajo | Sin acción |
| StackTrace al cliente | ✅ | Nunca; solo `log.error` server-side | Bajo | Sin acción |
| Logging de info sensible | ⚠ | Se registra `username` en fallos de login (`AuthUtils.java:69`); no se loguean passwords/tokens/headers | Bajo | Aceptable; opcional enmascarar username |

### §7 Configuración Spring

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Actuator expuesto | ✅ | Sin dependencia `spring-boot-starter-actuator` en `pom.xml` | Bajo | Sin acción |
| H2 Console habilitada | ✅ | No hay H2; MySQL en runtime (`pom.xml:64-68`) | Bajo | Sin acción |
| Swagger abierto en producción | ✅ | `springdoc.*.enabled=false` en prod (`application-prod.properties:5-6`) + gate a nivel security | Bajo | Sin acción |
| `ddl-auto` peligroso en prod | ✅ | prod usa `validate` (`application-prod.properties:10`); dev usa `update` | Bajo | Sin acción |
| `application.properties` con secretos | ✅ | Todos los secretos por env (`${JWT_SECRET}`, `${DB_PASSWORD}`, `${MAIL_*}`); `.env` **no** versionado (`.gitignore`) | Bajo | Sin acción |
| `show-sql=true` en producción | ⚠ | `application.properties:16` fija `show-sql=true` global; prod **no** lo sobreescribe → SQL impreso en prod (sin valores de bind salvo TRACE de dev) | Bajo | Poner `spring.jpa.show-sql=false` en `application-prod.properties` |
| Bean/devtools de desarrollo activo | ⚪ | `spring-boot-devtools` en `pom.xml:58-63` (`optional`, `runtime`); Spring lo desactiva en el jar empaquetado | Info | Confirmar que el despliegue usa jar empaquetado (devtools inerte) |
| Profiles mal configurados | ✅ | Default `dev`, prod explícito (`application.properties:2`) | Bajo | Asegurar `SPRING_PROFILES_ACTIVE=prod` en despliegue |

### §8 Spring Security

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| CSRF deshabilitado | ✅ | Correcto: API stateless con JWT en header (no cookies de sesión) (`MainSecurity.java:61`) | Bajo | Sin acción |
| CORS demasiado permisivo | ⚠ | Orígenes desde env, default `localhost:5173` (`MainSecurity.java:51,97`); `allowCredentials(true)` (`:100`). Sin comodín, pero depende de configurar bien el env en prod | Bajo | Fijar `APP_CORS_ALLOWED_ORIGINS` al dominio real en prod; nunca `*` |
| `permitAll()` excesivo | ✅ | Mínimo (`/auth/**`, `/error`, Swagger solo dev) | Bajo | Sin acción |
| Orden de filtros | ✅ | JWT antes de `UsernamePasswordAuthenticationFilter` (`MainSecurity.java:90`); rate-limit en `HIGHEST_PRECEDENCE+10` (`RateLimitFilter.java:40`) | Bajo | Sin acción |
| Session fixation | ✅ | `SessionCreationPolicy.STATELESS` (`MainSecurity.java:63`) | Bajo | Sin acción |
| Cookies inseguras (HttpOnly/Secure/SameSite) | ➖ | No se usan cookies para auth (token en header) | — | N/A |

### §9 Archivos

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Upload sin validar extensión/MIME | ✅ | Validación por *magic number* OOXML `PK\x03\x04` (`StudentListService.java:60,177-187`), más robusta que extensión/MIME | Bajo | Sin acción |
| Upload sin validar tamaño | ✅ | `max-file-size=1MB`, `max-request-size=10MB` (`application.properties:59-60`) | Bajo | Sin acción |
| Path/Directory Traversal | ⚠ | El nombre de archivo es siempre `{uuid}.xlsx` derivado en servidor (`StudentListService.java:259`) → seguro en uso actual; pero `LocalFileStorageService.store/load` no rechaza `..` en `filename` (defensa en profundidad ausente) (`LocalFileStorageService.java:44-73`) | Bajo | Validar que el path resuelto quede bajo `rootLocation` (`startsWith`) en la capa de storage |
| Archivos ejecutables / sobreescritura | ✅ | Solo `.xlsx` validado; sobreescritura acotada al propio grupo (`TRUNCATE_EXISTING`) | Bajo | Sin acción |
| Descarga arbitraria | ✅ | Descarga PDF por `groupUuid` (UUID), no por ruta; requiere ADMIN (`StudentListController.java:84`) | Bajo | Sin acción |
| Zip Slip / Zip Bomb (POI/OOXML) | ✅ | POI 5.3.0 aplica ratio de descompresión por defecto; entrada acotada a 1MB (`pom.xml:111-115`) | Bajo | Mantener POI actualizado |
| XXE en parseo XLSX | ✅ | POI/OOXML deshabilita entidades externas por defecto | Bajo | Sin acción |
| CSV / Formula injection en export | ✅ | La exportación es PDF (OpenPDF), no CSV; los nombres van como texto no ejecutable | Bajo | Sin acción |

### §10 Serialización

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Deserialización insegura / Jackson Default Typing | ✅ | Sin `enableDefaultTyping`/`@JsonTypeInfo` polimórfico; se deserializa a DTOs concretos (records) | Bajo | Sin acción |
| XXE | ✅ | Sin parseo XML de request; XLSX vía POI (entidades externas off) | Bajo | Sin acción |

### §11 Criptografía

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Algoritmos inseguros | ✅ | BCrypt + HMAC-SHA (jjwt 0.12.6) | Bajo | Sin acción |
| Claves hardcodeadas | ⚠ | Solo el secreto **dev** está en repo (`application-dev.properties:23`, `dev-only-...`); prod exige env sin default | Info | Aceptable en dev; nunca reutilizar ese valor fuera de local |
| IV fijo / AES ECB | ➖ | No se hace cifrado simétrico propio | — | N/A |
| Tokens predecibles / Random no cripto | ✅ | `SecureRandom` para matrícula (`UserService.java:350`); `UUID.randomUUID()` para `jti` | Bajo | Sin acción |

### §12 Base de datos

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Usuario root / permisos excesivos | ⚠ | Default de dev `DB_USERNAME:root` (`application.properties:7`); en prod se inyecta por env | Bajo | Usar cuenta MySQL de mínimo privilegio en prod (no root) |
| Conexión sin TLS | ⚠ | URL JDBC sin `useSSL/requireSSL` explícito (`application.properties:6`) | Bajo | Habilitar TLS a la BD en prod (`?useSSL=true&requireSSL=true`) |
| Información sensible sin cifrar | ✅ | Solo hashes BCrypt de password; sin PII sensible extra en claro | Bajo | Sin acción |
| Backups expuestos | ➖ | Fuera del repo | — | Gestionar cifrado/retención a nivel infra |

### §13 Logging

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Contraseñas / JWT / headers Authorization en logs | ✅ | No se registran; los handlers loguean solo mensajes/clases de excepción | Bajo | Sin acción |
| SQL completo con parámetros | ⚠ | Bind params solo a TRACE en dev (`application-dev.properties:9`); `show-sql` global (ver §7) | Bajo | `show-sql=false` en prod |
| Datos personales | ⚠ | `username` en warn de login fallido (`AuthUtils.java:69`) | Bajo | Opcional enmascarar |

### §14 Headers HTTP

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Content-Security-Policy | ⚠ | Presente pero mínima: `script-src 'self'; object-src 'none'` (`MainSecurity.java:66`); falta `default-src`, `frame-ancestors`, `base-uri` | Bajo | Ampliar CSP: `default-src 'self'; frame-ancestors 'none'; base-uri 'self'` |
| X-Frame-Options (clickjacking) | ✅ | Default de Spring Security `DENY` activo (no se deshabilitó `frameOptions`) | Bajo | Sin acción |
| X-Content-Type-Options | ✅ | `contentTypeOptions` habilitado (`MainSecurity.java:67`) | Bajo | Sin acción |
| Referrer-Policy | ❌ | No configurado | Bajo | Añadir `.referrerPolicy(...)` (`no-referrer` o `strict-origin-when-cross-origin`) |
| HSTS | ⚠ | Default de Spring activo solo sobre HTTPS | Bajo | Confirmar TLS en prod (terminación en proxy) para que aplique |
| Permissions-Policy | ❌ | No configurado | Bajo | Añadir `Permissions-Policy` restrictiva (geolocation=(), camera=(), etc.) |

### §15 Disponibilidad

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Rate limiting | ✅ | En endpoints sensibles (`RateLimitFilter.java`) | Bajo | *(Opcional)* extender a más endpoints de escritura |
| Límites de tamaño | ✅ | Multipart 1MB/10MB (`application.properties:59-60`) | Bajo | Sin acción |
| Límites de paginación | ⚠ | `MAX_SIZE=100` cuando se pagina, pero sin params se devuelve `DEFAULT_UNPAGED_SIZE=1000` (`PageCriteriaArgumentResolver.java:65,72`) | Bajo | Reducir el "unpaged" o forzar paginación en endpoints de alto volumen |
| Timeouts | ⚠ | SMTP con timeouts (`application.properties:28-30`); sin timeout explícito de datasource/HTTP | Bajo | Configurar timeouts de pool HikariCP y de conexión |
| DoS por lockout dirigido | ⚠ | 5 fallos bloquean un username conocido 10 min (`LoginAttemptService.java`) | Bajo | Aceptable; monitorear picos de 429 |
| Circuit breaker | ➖ | No hay dependencias externas críticas salvo SMTP (best-effort) | — | N/A |

### §16 Dependencias

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| Spring Boot desactualizado | ✅ | `spring-boot-starter-parent 4.0.6` (`pom.xml:8`), Java 21 — versión actual | Bajo | Mantener al día |
| Librerías con CVE conocido | ✅ | jjwt 0.12.6, poi-ooxml 5.3.0, bucket4j 8.10.1, springdoc 2.8.8, openpdf 1.3.43, mapstruct 1.6.3 — sin CVE crítico conocido a la fecha | Bajo | Ejecutar OWASP Dependency-Check en CI (comando en §Herramientas) |
| Librerías abandonadas | ✅ | Todas mantenidas activamente | Bajo | Sin acción |

### §17 OWASP API Security Top 10

| Riesgo | Estado | Nota |
|---|---|---|
| API1 Broken Object Level Authorization | ⚠ | IDOR de lectura por UUID (§2) |
| API2 Broken Authentication | ✅ | Robusto (§1) |
| API3 Broken Object Property Level Auth | ✅ | DTOs explícitos, sin over-posting (§3 Mass Assignment) |
| API4 Unrestricted Resource Consumption | ⚠ | Página unpaged de 1000; rate-limit parcial (§15) |
| API5 Broken Function Level Authorization | 🔴 | `GET /reservations` sin rol (§2) |
| API6 Unrestricted Access to Sensitive Flows | ✅ | Flujos sensibles con rate-limit/lockout |
| API7 SSRF | ✅ | Sin fetch de URLs provistas por el usuario |
| API8 Security Misconfiguration | ⚠ | Headers + `show-sql` (§7, §14) |
| API9 Improper Inventory Management | ✅ | `/api/v1`, Swagger gated |
| API10 Unsafe Consumption of APIs | ➖ | Sin consumo de APIs de terceros |

---

## 3. Frontend (React)

| Vulnerabilidad | Estado | Evidencia | Riesgo | Recomendación |
|---|---|---|---|---|
| XSS (Reflected/Stored/DOM) | ✅ | Sin `dangerouslySetInnerHTML`, `innerHTML` ni `eval` en `src/`; React escapa por defecto | Bajo | Sin acción |
| **JWT en localStorage** | ⚠ | `base.js:63`, `AuthContext.jsx:15,46`, `auth.js` guardan `token`/`refreshToken` en localStorage → robo vía XSS | Medio | Valorar cookie `HttpOnly`+`Secure`+`SameSite` para el refresh, o asumir el riesgo con CSP estricta + revisión anti-XSS |
| Rol solo en React (manipulable) | ✅ | El rol se deriva del JWT firmado, nunca de un string en localStorage (`AuthContext.jsx:9-17`, `utils/jwt.js:31`) | Bajo | Sin acción (backend es la autoridad real) |
| Rutas protegidas solo visualmente | ✅ | Guard de cliente + autorización real en backend; ocultar UI no es el control primario | Bajo | Sin acción |
| Logout incompleto | ✅ | `useLogout.js` + `base.js:117` `localStorage.clear()` y revocación server-side (blacklist) | Bajo | Sin acción |
| Validar solo en frontend | ✅ | Backend revalida todo (DTOs + reglas de dominio) | Bajo | Sin acción |
| CSRF en formularios | ✅ | Auth por header Bearer, no cookies → CSRF no aplica | Bajo | Sin acción |
| Datos sensibles en estado/Context | ✅ | Context solo guarda perfil + token; sin secretos de servidor | Bajo | Sin acción |
| HTTP en vez de HTTPS | ⚠ | Base URL default `http://localhost:8080` (`api/*.js`); en prod vía `VITE_API_URL` | Bajo | Fijar `VITE_API_URL` a `https://` en build de prod |
| Timeouts / reintentos infinitos | ⚠ | `base.js` usa `fetch` sin `AbortController`/timeout | Bajo | Añadir timeout con `AbortController` |
| Errores expuestos | ✅ | `HttpError` encapsula status/data; sin volcado de internals | Bajo | Sin acción |
| Preview/upload sin validar | ✅ | Backend valida magic number/tamaño; UI solo acompaña | Bajo | *(Opcional)* validar extensión/tamaño en cliente para UX |
| Dependencias npm vulnerables | ⚠ | `npm audit`: 1 baja (esbuild dev-server, GHSA-g7r4-m6w7-qqqr, solo dev en Windows); `pnpm audit`: 0 | Bajo | `npm audit fix` cuando convenga; no afecta prod |
| Build: source maps / secretos en bundle | ✅ | Solo `VITE_API_URL` (config pública) en el bundle; sin secretos | Bajo | Deshabilitar source maps en prod si no se requieren |
| CSP / Clickjacking / Mixed content | ⚠ | Depende de headers backend/proxy (§14) | Bajo | Ver §14 |

---

## 4. Arquitectura (Backend + Frontend)

| Vulnerabilidad | Estado | Evidencia / Nota |
|---|---|---|
| Secrets hardcodeados | ✅ | Solo secreto dev en repo; prod por env; `.env` ignorado |
| Comunicación sin HTTPS | ⚠ | Configurable por env; asegurar TLS extremo a extremo en prod |
| CORS demasiado permisivo | ⚠ | Ver §8 |
| Ausencia de rate limiting | ✅ | Presente en auth |
| Auditoría / logs de seguridad | ⚠ | Historial de reservas sí auditado (`ReservationHistory`); faltan logs de eventos de seguridad (login éxito/fallo centralizado, cambios de rol) |
| Monitoreo / alertas | ❌ | No hay stack de observabilidad (fuera de alcance del código) |
| Backup / cifrado de backup | ➖ | Nivel infraestructura |
| JWT sin revocación | ✅ | Blacklist por `jti` (`InMemoryTokenBlacklistService`) — nota: en memoria, no compartido entre instancias |
| Exposición de IDs secuenciales | ✅ | API expone `uuid` público, no IDs `Long` internos |
| Race conditions (doble booking) | ✅ | Constraints UNIQUE + chequeos de conflicto (`ReservInstanceService.java:299-311`) |
| Replay / timing / session fixation | ✅ | Stateless + tokens de un uso donde aplica |
| Open Redirect / Host Header Injection | ✅ | `app.frontend-url` desde config, no del header `Host` |

> **Nota sobre la blacklist en memoria:** `InMemoryTokenBlacklistService` y `LoginAttemptService` viven en el heap. En un despliegue **multi-instancia** la revocación y el lockout no se comparten entre nodos. Para escalar horizontalmente, migrar a Redis (o similar). Riesgo Bajo en despliegue de instancia única.

---

## 5. Resultados de herramientas automatizadas

### Ejecutadas en este entorno

**`pnpm audit` (front/icf-aulas):**
```
vulnerabilities: info 0, low 0, moderate 0, high 0, critical 0
totalDependencies: 333
```

**`npm audit` (front/icf-aulas):**
```
esbuild  0.27.3 - 0.28.0
  esbuild allows arbitrary file read when running the dev server on Windows
  GHSA-g7r4-m6w7-qqqr  (severidad: low)
  fix available via `npm audit fix`
1 low severity vulnerability
```
> Solo afecta al servidor de desarrollo de Vite/esbuild; no impacta producción.

**Revisión de dependencias backend (`pom.xml`):** Spring Boot 4.0.6 / Java 21; jjwt 0.12.6, poi-ooxml 5.3.0, bucket4j 8.10.1, springdoc 2.8.8, openpdf 1.3.43, mapstruct 1.6.3 — todas vigentes, sin CVE crítico conocido a la fecha de esta auditoría.

**Escaneo de secretos (grep manual):** No se encontraron secretos versionados. `.env` está en `.gitignore` y no rastreado; el único secreto en repo es el JWT **de desarrollo** en `application-dev.properties` (esperado).

### Pendientes de ejecutar (herramientas no instaladas en este entorno)

Ejecutar en un entorno/CI con las herramientas instaladas:

```bash
# Dependencias Java (CVE)
cd back/aulas && ./mvnw org.owasp:dependency-check-maven:check

# Análisis estático de seguridad Java
# (SpotBugs + FindSecBugs vía plugin Maven, o Semgrep)
semgrep --config auto back/ front/

# Secretos (histórico de git incluido)
gitleaks detect --source . --report-path gitleaks-report.json
trufflehog filesystem .

# Contenedores / filesystem
trivy fs .

# DAST (con la app corriendo)
# OWASP ZAP baseline scan contra http://localhost:8080
```

---

## 6. Plan de remediación priorizado

### 🔴 Alto — abordar primero
1. **`GET /api/v1/reservations` (findAll):** añadir `@PreAuthorize("hasRole('ADMIN')")` en `ReservInstanceController.java:84`, **o** inyectar el filtro por `principal.getUuid()` cuando el rol no sea ADMIN (reutilizar el patrón de `findByUser`).

### 🟠 Medio
2. **IDOR lectura por UUID:** en `ReservInstanceService.findByUuid` y `ReservationGroupService.findByUuid`, recibir el principal y verificar propiedad (`group.user.uuid == principal`) salvo ADMIN.
3. **Política de contraseñas:** exigir complejidad (mayúscula + minúscula + dígito + símbolo) en `RegisterRequestDTO`, `UserUpdateRequestDTO`, `ResetPasswordRequestDTO`.
4. **JWT en cliente:** decidir estrategia — migrar el refresh token a cookie `HttpOnly`+`Secure`+`SameSite=Strict`, o documentar la aceptación del riesgo respaldada por CSP estricta.

### 🟡 Bajo — endurecimiento
5. `spring.jpa.show-sql=false` en `application-prod.properties`.
6. Completar headers: `Referrer-Policy`, `Permissions-Policy`, CSP con `default-src`/`frame-ancestors`/`base-uri`.
7. Defensa en profundidad en `LocalFileStorageService` (verificar que el path resuelto quede bajo `rootLocation`).
8. Reducir `DEFAULT_UNPAGED_SIZE` o forzar paginación; añadir timeouts (datasource, fetch del frontend con `AbortController`).
9. TLS a la BD y cuenta MySQL de mínimo privilegio en prod; `VITE_API_URL` con `https://`.
10. `npm audit fix` para esbuild.

### ⚪ Informativo / operacional
11. Integrar OWASP Dependency-Check, Semgrep y Gitleaks en CI.
12. Para escalado multi-instancia: mover blacklist de tokens y contadores de lockout a Redis.
13. Confirmar `SPRING_PROFILES_ACTIVE=prod` y despliegue como jar empaquetado (devtools inerte) en producción.

---

*Auditoría basada en revisión estática del código en la rama `main`. No incluye pruebas dinámicas (DAST) ni de penetración activas. Las severidades reflejan el contexto de un despliegue institucional de instancia única; reevaluar si cambia el modelo de amenaza (p. ej. exposición a Internet abierta o multi-tenant).*

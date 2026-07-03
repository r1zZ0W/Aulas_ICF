# SAD — Módulo de Carga Masiva de Alumnos (Excel)
## Sistema de Aulas ICF · UNAM

> **Documento de Arquitectura de Software (SAD)** generado por un Architecture Review Board (ARB) simulado.
> **Comité:** Principal Software Architect (PSA) · Senior Spring Boot Engineer (SBE) · Database Architect (DBA) · Site Reliability Engineer (SRE) · Security Architect (SEC).
> **Stack verificado:** Spring Boot 4.0.6 · Java 21 · MySQL 8 + HikariCP · Spring MVC (bloqueante) · MapStruct · JWT · Bucket4j + Caffeine. Sin Actuator/Micrometer aún. Migraciones SQL manuales. Despliegue mono-instancia.
> **Última actualización:** 2026-06-30

---

## Convenciones del Documento (aplican a todo el SAD)

### Niveles de Confianza
| Nivel | Significado |
|---|---|
| **[C-Alta]** | Evidencia directa: código del repo, documentación oficial, teoría formal/matemática. |
| **[C-Media]** | Inferencia razonada a partir de características documentadas de la tecnología o benchmarks públicos de terceros (no medidos en *este* entorno). |
| **[C-Baja]** | Hipótesis de diseño. No existe medición. DEBE acompañarse del experimento que la valida. |

### Lenguaje Normativo (RFC 2119)
**MUST / MUST NOT / SHOULD / SHOULD NOT / MAY.** "MUST" = bloqueante de GA; "SHOULD" = recomendado, desviación justificable con argumentos; "MAY" = opcional.

### Formato de Decisión
Ninguna recomendación se cierra en el análisis. Las matrices producen **candidatos**; la decisión se formaliza en un **ADR numerado** que termina con: **Evidencia** · **Trade-off** · **Fitness Function** (¿cómo sé en 1 año que sigue siendo válida?).

### Regla de Métricas
Si no existen mediciones reales, no se inventa ninguna cifra de rendimiento. Las métricas no medidas se clasifican como **hipótesis de diseño** (HYP-xx) y se acompañan del benchmark que DEBE ejecutarse para validarlas.

---

## Registro de Supuestos (Assumptions Register)

| ID | Supuesto | Confianza | Fuente / Acción si es falso |
|---|---|---|---|
| **A-01** | Contenedor 2 vCPU / 4 GB RAM, `-Xmx2g` | **[C-Baja]** | NO confirmado. MUST validarse con Infra antes de GA. Si difiere, re-derivar Capacity Planning. |
| **A-02** | HikariCP `maximumPoolSize` = 10 (default Boot) | **[C-Media]** | Default del framework; MAY ser 30/50/100 según entorno. MUST confirmarse el valor real. |
| **A-03** | NFSv4 como único storage de evidencia | **[C-Alta]** | Restricción institucional declarada explícitamente. |
| **A-04** | Despliegue mono-instancia hoy, clúster futuro | **[C-Media]** | Inferido (sin coordinación distribuida en `pom.xml`). MUST confirmarse hoja de ruta. |
| **A-05** | Volúmenes de dominio (5k–100k real; 3M = abuso) | **[C-Media]** | 5k/50k/100k provistos por stakeholder; tamaño/fila (~300–500 B, 12–18 cols) es estimado. |
| **A-06** | Proyecto = Aulas ICF/UNAM, MySQL 8, Java 21, SB 4.0.6 | **[C-Alta · repo]** | `pom.xml`, `application*.properties`, `groupId=mx.unam.icf`. |
| **A-07** | SLA background: 5k ≤ 15 s / 50k ≤ 90 s / 100k ≤ 240 s | **[C-Alta · stakeholder]** | Provistos como objetivos. Que el sistema los *cumpla* es HYP-01. |

## Registro de Hipótesis (a validar con benchmark/PoC)

| ID | Hipótesis | Confianza | Experimento que la valida |
|---|---|---|---|
| **HYP-01** | El sistema puede cumplir los SLA de A-07 | **[C-Baja]** | **B-00**: carga end-to-end con datasets sintéticos de 5k/50k/100k sobre hardware A-01 real, ≥30 corridas, reportar p50/p95/p99. |
| **HYP-02** | Throughput sostenible ≥ ~800 filas/s | **[C-Baja]** | Derivada de B-00; es objetivo, no hecho. |
| **HYP-03** | Footprint de heap del parser streaming es ~O(1) y < umbral operable | **[C-Baja]** | **B-01**: parsear xlsx de 100k/1M/3M filas midiendo heap con JFR / `-XX:+HeapDumpOnOutOfMemoryError`. |
| **HYP-04** | Persistencia batched es ≥ 5× row-by-row | **[C-Media]** | **B-02**: comparar `batch_size` + `rewriteBatchedStatements=true` vs inserts unitarios, mismas filas. |
| **HYP-05** | Un pool aislado de tamaño P evita degradar la API | **[C-Baja]** | **B-03**: carga concurrente import + tráfico sintético de API, medir Δp99. |

---

## BLOQUE 1 — Cimientos, NFRs y Desafío de Restricciones

### 1. Supuestos Técnicos del Entorno

| Dominio | Supuesto base | Evidencia |
|---|---|---|
| Runtime | JDK 21 LTS, Project Loom GA (virtual threads sin flags) | `pom.xml` `<java.version>21</java.version>` |
| Framework | Spring Boot 4.0.6, Spring MVC bloqueante (no WebFlux) | `spring-boot-starter-webmvc` |
| RDBMS | MySQL 8 InnoDB, `REPEATABLE READ`, `utf8mb4` | `mysql-connector-j`, `MySQLDialect` |
| Pool | HikariCP (ver A-02) | transitivo de `spring-boot-starter-data-jpa` |
| Topología | Mono-instancia hoy; diseño cluster-ready (ver A-04) | sin coordinación distribuida en `pom.xml` |
| Storage | NFSv4 único almacén de evidencia (ver A-03) | restricción institucional |
| Memoria | Ver A-01 | no confirmado |
| Límite archivo | 1 MB multipart hoy — módulo requiere techo dedicado mayor | `spring.servlet.multipart.max-file-size=1MB` |
| Observabilidad | **Inexistente** — sin Actuator/Micrometer/OTel | ausente en `pom.xml` — **gap crítico bloqueante** |
| Migraciones | Manuales SQL en `docs/`, prod `ddl-auto=validate` | `application-prod.properties` |

> **Recomendación Consensuada §1:** Baseline congelado. Dos ítems son **deuda bloqueante de GA**: (a) cero observabilidad y (b) techo multipart de 1 MB insuficiente para el módulo. Ningún diseño de carga masiva es operable sin instrumentación ni sin un límite de subida dedicado.

### 2. Cuestionamiento Crítico de la Restricción NFS

La restricción "NFS como único storage para HA" se evalúa como **parcialmente contraproducente**: NFS da lectura compartida entre nodos pero no es HA — mueve el SPOF en lugar de eliminarlo.

**DBA:** NFS con `fsync` frecuente degrada. `soft` mounts corrompen datos, `hard` mounts cuelgan el hilo. *Mitigación:* evidencia Excel write-once en NFS con `O_CREAT|O_EXCL` (UUID); procesamiento en staging InnoDB, nunca en NFS.

**SRE:** Si cae el servidor NFS, todos los nodos pierden evidencia simultáneamente. `ENOSPC` al 100% produce error en plena escritura. *Mitigación:* (1) montar `hard,intr` + timeout de aplicación envolviendo toda I/O NFS, (2) health-check de capacidad que rechaza nuevas subidas al 90% con `503`, (3) degradación elegante: fallo NFS ⇒ se procesa igual y se encola la evidencia para reintento.

**SEC:** `AUTH_SYS` es trivialmente suplantable en LAN. *Mitigación:* export dedicado, `root_squash`, `0700`, `no_exec`, idealmente `sec=krb5p` (Kerberos + cifrado en tránsito) + cifrado en reposo del volumen. Validar magic numbers/ZIP-bomb antes de tocar NFS.

> **Recomendación Consensuada §2:** NFS = **almacén de evidencia inmutable write-once**, nunca sustrato de procesamiento ni HA real. La HA genuina la dan el clúster de app + MySQL. Obligatorio pre-GA: `hard`+timeout app · `root_squash`/`0700`/`krb5p` · health-check 90% · degradación elegante.
> **Disenso formal registrado (SRE):** NFS-como-HA es un anti-patrón. Se acepta solo bajo esas mitigaciones. Recomienda evaluar MinIO (S3 on-prem) post-MVP.
> **Trade-off (SRE vs restricción institucional):** auditoría inmutable vs fragilidad de FS-de-red.

### 3. Fase 0 — ATAM

**Atributos de calidad priorizados:**
1. Integrity/Correctness (padrón corrupto contamina reservas, accesos y reporting aguas abajo)
2. Availability síncrona (la API no debe degradarse durante una carga masiva)
3. Performance/Scalability asíncrona (cumplir los SLA de A-07)
4. Security (CURP/email = datos personales LFPDPPP; entrada binaria no confiable)
5. Observability (hoy = 0)
6. Modifiability (fuentes futuras CSV/API/ERP)
7. Maintainability / Cost

**Puntos de Sensibilidad:**
- **SP-1 Chunk size** — dial maestro: RAM ↔ duración tx ↔ filas/s
- **SP-2 `batch_size`+`rewriteBatchedStatements`** — O(n) vs O(n/batch) round-trips
- **SP-3 Pool Hikari reservado al import** — cuántas cargas caben sin matar la API
- **SP-4 Parsing DOM vs streaming** — única defensa real contra OOM a 3M filas

**Puntos de Trade-off:**
- **TP-1** Lote grande ↑throughput pero ↑RAM y ↑duración de lock
- **TP-2** Validación Set-Based (rápida) vs presión sobre el pool
- **TP-3** Idempotencia SHA-256 estricta vs reprocesar legítimamente tras fallo (→ ADR-004)
- **TP-4** Virtual threads vs *pinning* y saturación de Hikari
- **TP-5** Evidencia NFS (auditoría) vs Availability

**Riesgos:**
- **R-1** OOM por parsing DOM (3M filas) → caída del nodo
- **R-2** Agotamiento del pool → cae toda la API
- **R-3** Pérdida de estado ante kill abrupto → import a medias
- **R-4** Sin coordinación distribuida → doble procesamiento al clusterizar
- **R-5** Cero observabilidad → imposible probar SLA
- **R-6** NFS SPOF de evidencia

**No-Riesgos:**
- **NR-1** Escritura concurrente sobre el mismo alumno: el árbitro es `UNIQUE(matricula)` en InnoDB
- **NR-2** Back-pressure de red: procesamiento async desacoplado, WebFlux innecesario
- **NR-3** Latencia DB < 1 ms (LAN): no es el cuello de botella

> **Recomendación Consensuada §3:** El módulo es **heap-bound en parsing e I/O-bound en persistencia**. Optimizar alrededor de SP-1 y SP-4, con SP-3 como salvaguarda. R-1/R-2/R-3/R-5 = bloqueantes de GA. R-4 = bloqueante al clusterizar.

### 4. NFRs y SLAs

**SLAs de procesamiento [C-Alta · stakeholder]:**

| Volumen | Tiempo máx. background |
|---|---|
| 5.000 alumnos | ≤ 15 s |
| 50.000 alumnos | ≤ 90 s |
| 100.000 alumnos | ≤ 240 s |

*Throughputs derivados son HYP-01/HYP-02 (ver Registro de Hipótesis). No son hechos.*

**NFRs adicionales [C-Media]:**

| NFR | Objetivo |
|---|---|
| Latencia `POST /imports` | p99 ≤ 800 ms → solo valida cabeceras, persiste evidencia+registro, devuelve `202 + jobId`. MUST NOT procesar en el hilo del request. |
| Latencia `GET /imports/{id}` (polling) | p99 ≤ 150 ms |
| Subidas concurrentes | ≥ 20 aceptadas sin degradar API (encoladas vía admission control) |
| Aislamiento | carga de 100k no sube p99 del resto de la API > +20% |
| **RTO** | ≤ 5 min (reconciliación de jobs huérfanos) |
| **RPO** | ≈ 0 datos de producción (commit MySQL); ≤ 1 chunk para progreso en vuelo; evidencia NFS write-once ⇒ RPO = 0 una vez confirmada |

> **Recomendación Consensuada §4:** Contrato **asíncrono obligatorio** (`202` < 800 ms). RTO ≤ 5 min y RPO ≤ 1 chunk exigen **tabla de jobs con máquina de estados persistente** — requisito transversal que condiciona todos los ADR.

### 5. Performance Budget (base: SLA 100k = 240 s)

*Los porcentajes son una distribución de diseño [C-Baja], no mediciones. Cada fila es un SLO/alerta de Prometheus instrumentado en Micrometer (`import_phase_duration_seconds{phase}`). Los tiempos absolutos se validan con B-00.*

| Fase | % | Tiempo objetivo @100k | Umbral alerta (×1.3) | Naturaleza |
|---|---|---|---|---|
| 1. Carga/Recepción (NFS write-once + SHA-256) | 8% | ~19 s | 25 s | I/O NFS + hash |
| 2. Parsing (Excel streaming → filas) | 22% | ~53 s | 69 s | CPU + heap |
| 3. Validación Estructural (tipos/regex CURP-email) | 15% | ~36 s | 47 s | CPU por fila |
| 4. Validación Negocio (dedup + Set-Based vs DB) | 20% | ~48 s | 62 s | I/O DB set-based |
| **5. Persistencia (staging→prod, batched)** | **27%** | ~65 s | 84 s | I/O DB — fase más cara |
| 6. Auditoría/Telemetría/Cierre | 8% | ~19 s | 25 s | I/O DB + métricas |

Parsing (22%) + Persistencia (27%) ≈ 50% → focos de optimización (streaming + batch). Validación de Negocio (20%) MUST ser Set-Based (anti-join), MUST NOT row-by-row.

> **Recomendación Consensuada §5:** Adoptar este reparto como SLO por fase instrumentado en Micrometer. **Precondición:** introducir `spring-boot-starter-actuator` + `micrometer-registry-prometheus` (hoy ausentes). Sin ello el budget es inverificable.

---

## BLOQUE 2 — Capacity Planning, Matrices y Big O

### 1. Capacity Planning — peor escenario (100 usuarios × 20 archivos)

**Hallazgo central [C-Alta]:** la "concurrencia" no es un dato de entrada, es una decisión. Procesar N parsings en paralelo está acotado por la arquitectura, no por la demanda. El sistema MUST imponer **admission control** (ejecutor de tamaño `P` + cola acotada `Q`).

**Modelo analítico [C-Baja — pendiente B-01/B-03]:**
```
RAM_pico ≈ P × footprint_parser_stream + P × (chunk_size × bytes_fila) + overhead_JVM
```

Con parser **streaming** (HYP-03): footprint ~constante → el techo de RAM lo fija `P`, no el nº de archivos en cola.
Con parser **DOM**: `footprint ∝ nº filas` → un solo archivo de 3M puede agotar la JVM (R-1).

| Recurso | Modelo | Riesgo si no se acota |
|---|---|---|
| Heap | `P × (footprint + chunk × ~400B)` | OOM (R-1) |
| CPU | saturación a `P > nº vCPU` | thrash de context-switch |
| Pool | `P_import ≤ pool_import < pool_total` | R-2: hambre de la API |
| Staging | `Σ filas_en_vuelo × 400B` — puede ser enorme sin acotar | llenado de tablespace |

> **§2.1 MUST:** ejecutor acotado `P` + cola `Q` acotada con rechazo `429` al saturar. Staging MUST ser efímero, particionado por `jobId`, purgado por TTL. Valores `P`/`Q`/`chunk_size` son **[C-Baja]** — se fijan con B-01/B-03, no a priori.

### 2. Matrices de Decisión Ponderadas

*Criterios (dados): RAM .30 · Rendimiento .25 · Madurez .20 · Mantenibilidad .15 · Curva .10. Escala 1–5.*
*Puntajes [C-Media]: inferidos de características documentadas + benchmarks públicos; MUST validarse con PoC antes del ADR. Estas matrices NO cierran la decisión — eso es el ADR.*

**Componente A — Parser de Excel** (valida: B-01)

| Alternativa | RAM .30 | Perf .25 | Madurez .20 | Manten .15 | Curva .10 | Total |
|---|---|---|---|---|---|---|
| POI XSSF (DOM) | 1 | 2 | 5 | 4 | 5 | **2.90** |
| POI Event API (SAX) | 4 | 4 | 5 | 2 | 2 | **3.70** |
| **fastexcel** | 5 | 5 | 3 | 5 | 4 | **4.50** |
| excel-streaming-reader | 5 | 4 | 3 | 4 | 4 | **4.10** |
| Spring Batch FlatFile + pre-conv. | 4 | 4 | 4 | 3 | 2 | **3.65** |
| Apache Arrow | 3 | 5 | 3 | 2 | 1 | **3.15** |

→ Candidato: **fastexcel (4.50)**, fallback excel-streaming-reader. Caveat [C-Alta]: fastexcel solo lee `.xlsx` (OOXML), no `.xls` legado — irrelevante porque el sistema MUST rechazar `.xls` por superficie de ataque.

**Componente B — Coordinación distribuida** (relevante al clusterizar, R-4)

| Alternativa | RAM .30 | Perf .25 | Madurez .20 | Manten .15 | Curva .10 | Total |
|---|---|---|---|---|---|---|
| **ShedLock** | 5 | 4 | 4 | 5 | 5 | **4.55** |
| Native DB (`SKIP LOCKED`) | 4 | 4 | 4 | 3 | 3 | **3.75** |
| External cron orquestador | 3 | 3 | 3 | 3 | 4 | **3.10** |
| Quartz Cluster | 2 | 3 | 5 | 2 | 2 | **2.85** |

→ Candidatos **complementarios**: **ShedLock** (cron de reconciliación de jobs huérfanos/RTO) + `SELECT … FOR UPDATE SKIP LOCKED` (MySQL 8 [C-Alta]) para reclamar jobs sin doble-procesamiento. No compiten — cubren responsabilidades distintas.

**Componente C — Concurrencia asíncrona** (valida: B-03)

| Alternativa | RAM .30 | Perf .25 | Madurez .20 | Manten .15 | Curva .10 | Total |
|---|---|---|---|---|---|---|
| **ThreadPoolTaskExecutor acotado** | 4 | 3 | 5 | 4 | 5 | **4.05** |
| Spring Batch chunk-oriented | 4 | 4 | 5 | 3 | 2 | **3.85** |
| Virtual threads (Loom) | 4 | 4 | 3 | 4 | 4 | **3.80** |
| Cola de mensajería persistente | 2 | 4 | 4 | 2 | 2 | **2.90** |

> **Observación crítica [C-Alta]:** la rúbrica dada omite **Resiliencia/Restartability**, crítica para RTO/RPO (R-3). Spring Batch pierde por puntaje pero aporta restart/skip/retry nativos. **Desacuerdo formal:** SBE → executor simple (4.05); PSA/SRE → Spring Batch (resiliencia). Virtual threads MUST NOT usarse como motor (pool de 10 → cuello en Hikari + *pinning*, TP-4). **Resuelto en ADR-001.**

**Componente D — Pool de conexiones**

| Alternativa | RAM .30 | Perf .25 | Madurez .20 | Manten .15 | Curva .10 | Total |
|---|---|---|---|---|---|---|
| **HikariCP** | 5 | 5 | 5 | 5 | 5 | **5.00** |
| Tomcat JDBC | 4 | 3 | 4 | 3 | 4 | **3.60** |
| Commons DBCP2 | 3 | 2 | 4 | 3 | 4 | **3.05** |
| R2DBC | 1 | 3 | 3 | 1 | 1 | **1.90** |

→ Candidato: **HikariCP** (ya es el default [C-Alta]). R2DBC descalificado (MVC bloqueante, A-06). La decisión real no es la librería sino un **`DataSource` secundario aislado para imports** + sizing (HYP-05).

### 3. Big O y Costos Computacionales

*Complejidad [C-Alta] (análisis formal); footprint/throughput en filas/seg NO se reportan — ninguna cifra existe hasta ejecutar B-00…B-03.*

| Etapa | Tiempo | Memoria |
|---|---|---|
| Parsing streaming | O(n) | **O(1)** |
| Parsing DOM | O(n) | **O(n)** — causa de R-1, descartado |
| Validación estructural | O(n·c) ≈ O(n) | O(1) |
| Dedup intra-archivo | O(n) | **O(k)** claves → SHOULD delegar a `GROUP BY` en staging si k grande |
| Dedup vs DB (set-based) | ~O(n+m) | O(1) app — MUST NOT row-by-row |
| Persistencia batched | O(n) inserts, **O(n/b)** round-trips | O(b) + índice `UNIQUE` O(n·log m) |

> **Candidatos de Bloque 2 (no decisiones):** A: fastexcel · B: ShedLock + SKIP LOCKED · C: *abierto* → ADR-001 · D: HikariCP + pool aislado. Todo cierre depende de ejecutar B-00…B-03.

---

## BLOQUE 3 — Catálogo Formal de ADRs

### ADR-001 — Modelo de Concurrencia y Motor de Procesamiento

**Contexto y Problema.** El análisis de Bloque 2 dejó un desacuerdo formal: `ThreadPoolTaskExecutor` (SBE, ganó la rúbrica 4.05 por simplicidad) vs Spring Batch chunk-oriented (PSA/SRE, 3.85). La rúbrica omitía Resiliencia/Restartability, crítica para R-3 y RTO ≤ 5 min. Virtual threads quedan vetados como motor (TP-4: pool JDBC acotado → cuello + *pinning*).

**Alternativas Consideradas:**
- **ThreadPoolTaskExecutor + chunking manual** — *Pro:* mínima curva, cero tablas extra. *Con:* re-implementa restart/skip/retry a mano; recuperación ante kill (R-3) es código propio frágil; viola "framework-native sobre custom" ([C-Media · architectural-rigor]).
- **Spring Batch chunk-oriented** — *Pro:* `commit-interval` es SP-1; restart/skip/retry e idempotencia de paso nativos; `JobRepository` persiste el estado (R-3/RTO); framework-native. *Con:* tablas `BATCH_*`, mayor curva, más configuración.
- **Virtual threads (Loom)** — *Pro:* concurrencia I/O barata. *Con:* sin restartability; satura Hikari; *pinning* con JDBC.
- **Cola de mensajería persistente** — *Pro:* desacople duro. *Con:* infra nueva, viola A-03.

**Decisión [C-Media].** El sistema MUST usar **Spring Batch chunk-oriented** como motor, lanzado por un `TaskExecutor` acotado de tamaño `P` (admission control del §2.1). `commit-interval` MUST ser parametrizable. Virtual threads MUST NOT ser el motor; MAY usarse para I/O auxiliar no-JDBC (p.ej. escritura NFS).

**Consecuencias.** *Gana:* restart/recuperación casi gratis, `chunk_size` como dial de primera clase, `JobRepository` como estado durable. *Pierde:* tablas `BATCH_*` en el esquema, mayor curva, configuración más verbosa.

**Riesgos.** Acoplamiento a Spring Batch (mitigar: core detrás de interfaces, OCP — ver Bloque 4). Carga tx de `JobRepository` sobre la misma DB (mitigar: pool aislado, HYP-05).

**Evidencia:** B-03 (pool aislado protege API), B-00 (SLA end-to-end con Spring Batch). **Trade-off:** simplicidad ↓ por resiliencia ↑. **Fitness Function:** si la reanudación de un job tras kill supera RTO (5 min) O si `BATCH_*` añade > X% al tiempo de Fase 6 → revisar el motor.

---

### ADR-002 — Modelo de Validación de Negocio

**Contexto y Problema.** Validar duplicados (matrícula/CURP intra-archivo y contra producción) y FKs de carrera para hasta 100k–3M filas, sin reventar el budget (Fase 4 = 20%) ni la RAM.

**Alternativas Consideradas:**
- **Row-by-row** (1 query por fila) — *Con:* O(n) round-trips; a 100k = 100k SELECTs; convertiría Fase 4 en > 70% del tiempo. **Rechazado.**
- **In-memory `HashSet`** (cargar todo el padrón existente) — *Pro:* O(1)/fila. *Con:* O(m) RAM; a millones de alumnos agota heap; *stale* bajo escrituras concurrentes. **Rechazado para volumen.**
- **Set-Based vía Staging** — bulk-insert de filas crudas a tabla de staging por `jobId`, luego `GROUP BY` (dedup intra-archivo) + anti-join indexado contra producción. O(n+m) con índices, una sola pasada, correcto bajo concurrencia, auditable.

**Decisión [C-Alta].** El sistema MUST validar negocio **Set-Based sobre tabla de staging** particionada por `jobId`. MUST NOT row-by-row. La unicidad final MUST descansar además en `UNIQUE(matricula)` InnoDB como árbitro definitivo (NR-1). Staging MUST ser efímero (TTL/purge).

**Consecuencias.** *Gana:* rendimiento predecible, RAM acotada, auditoría de filas rechazadas. *Pierde:* I/O de staging y complejidad SQL.

**Riesgos.** Índices de staging mal dimensionados → joins lentos (mitigar: índice en columnas clave, `ANALYZE`). Crecimiento de staging (mitigar: purge por TTL).

**Evidencia:** B-02 + `EXPLAIN` del anti-join (MUST NOT mostrar full scan). **Trade-off:** I/O y SQL extra por O() y RAM acotados. **Fitness Function:** si Fase 4 supera 62 s @100k O si `EXPLAIN` muestra full scan → revisar índices/estrategia.

---

### ADR-003 — Almacenamiento y Evidencia Física (NFS)

**Contexto y Problema.** A-03 impone NFS para evidencia. El Bloque 1 §2 estableció que NFS no es HA real. La pregunta concreta: ¿dónde vive el blob Excel y dónde el dato de trabajo?

**Alternativas Consideradas:**
- **NFS como sustrato de procesamiento** — `fsync`/locks NLM/stale handles/SPOF (R-6). **Rechazado.**
- **BLOB en DB (`LONGBLOB`)** — infla InnoDB, contamina buffer pool, hincha backups. **Rechazado para el blob.**
- **Object storage (MinIO S3 on-prem)** — ideal (versionado, durabilidad, desacople). Viola A-03 hoy. **Diferido post-MVP.**
- **Disco local por nodo** — no compartido; rompe clúster (A-04). **Rechazado.**
- **NFS write-once de evidencia + metadatos en DB** — el `.xlsx` original se escribe una vez con UUID (`O_CREAT|O_EXCL`), inmutable; DB guarda metadatos (path, SHA-256, tamaño, `jobId`). El procesamiento ocurre en staging InnoDB, nunca sobre NFS.

**Decisión [C-Media].** El blob original MUST persistirse write-once en NFS bajo nombre **UUID** (anti path-traversal). Toda I/O NFS MUST envolverse en un timeout de aplicación. NFS MUST montarse `hard,intr` + `root_squash` + `0700` + `no_exec`, idealmente `sec=krb5p`. El procesamiento MUST NOT tocar NFS. Fallo de NFS MUST producir degradación elegante (procesar igual, evidencia encolada para reintento), nunca abortar el import. Migrar a MinIO SHOULD evaluarse post-MVP.

**Consecuencias.** *Gana:* evidencia inmutable auditable, desacople procesamiento/evidencia. *Pierde:* dependencia operativa de NFS, complejidad de timeouts/degradación.

**Riesgos.** R-6 SPOF de NFS (mitigar: degradación + health-check 90%). *Stale handle* (mitigar: write-once sin locks de app).

> **Observación de auditoría (§Bloque5-autocrítica):** si la evidencia es requisito de cumplimiento duro (no best-effort), "degradación elegante" es insuficiente. Un import SUCCESS sin evidencia sería un incumplimiento. **Esto MUST escalarse al stakeholder antes de GA.**

**Evidencia:** prueba de inyección de fallo NFS (Bloque 5) + verificación de export `krb5p`. **Trade-off:** auditoría vs fragilidad de FS-de-red. **Fitness Function:** si p99 de Fase 1 > 25 s @100k O si hay > N stale-handles/mes → acelerar migración a MinIO.

---

### ADR-004 — Idempotencia (SHA-256) coexistiendo con Redrive

**Contexto y Problema.** TP-3: idempotencia estricta por SHA-256 bloquea el ataque de duplicación (15 subidas del mismo archivo en 3 s), pero también bloquea el reprocesamiento legítimo de un archivo idéntico tras un fallo. Hay que conciliar ambos.

**Alternativas Consideradas:**
- **SHA-256 con `UNIQUE` global** — *Pro:* dedup perfecto. *Con:* impide reintentar un archivo que falló; rígido. **Insuficiente solo.**
- **Sin idempotencia, solo redrive manual** — el ataque crea N jobs. **Rechazado.**
- **Idempotencia con scope de estado + redrive sobre identidad de job** — el hash es único solo contra imports en estado SUCCESS o activos; un antecedente FAILED/CANCELLED libera una nueva subida. El redrive no es re-subida: es `POST /imports/{id}/retry` que reutiliza la evidencia existente y reanuda Spring Batch (ADR-001) sin re-hashear.

**Decisión [C-Media].** MUST calcular SHA-256 **en streaming** durante la recepción del multipart. Hash en estado SUCCESS/activo ⇒ MUST responder `409 Conflict` con enlace al job previo (corta el ataque de duplicación). Antecedente FAILED/CANCELLED ⇒ MAY aceptar nueva subida. Reproceso legítimo MUST exponerse como **redrive sobre `jobId`** (reutiliza evidencia + restart de Spring Batch). Dedup (contenido) y redrive (identidad) son **ejes ortogonales**.

**Consecuencias.** *Gana:* anti-duplicación sin sacrificar recuperabilidad; redrive barato (no re-sube ni re-hashea). *Pierde:* unicidad condicional por estado (más compleja que un `UNIQUE` plano).

**Riesgos.** Carrera entre dos subidas idénticas simultáneas (mitigar: `UNIQUE(sha256, estado_activo)` + manejo de `DuplicateKey`). Colisión SHA-256: probabilidad despreciable.

**Evidencia:** prueba del ataque de duplicación (15× en 3 s) + prueba de redrive tras kill. **Trade-off:** complejidad de estado vs recuperabilidad. **Fitness Function:** si aparecen jobs SUCCESS con mismo hash → la guarda de unicidad falló; si un redrive genera filas duplicadas en producción → revisar idempotencia de paso de Spring Batch.

---

### ADR-005 — Interfaz Asíncrona con el Cliente (Polling vs SSE vs WebSockets)

**Contexto y Problema.** El endpoint devuelve `202 + jobId`. El frontend necesita conocer progreso/resultado. La solución debe ser cluster-ready (A-04): el job puede correr en el nodo A mientras la conexión del cliente cae en el nodo B.

**Alternativas Consideradas:**
- **WebSockets** — *Con:* bidireccional (innecesario), stateful, enrutamiento en clúster requiere sticky/pub-sub. Sobre-ingeniería.
- **SSE (`SseEmitter`)** — *Pro:* unidireccional, encaja con progreso, simple en Spring MVC. *Con:* conexión viva; en clúster, el nodo con la conexión ≠ nodo que procesa → requiere pub/sub o sticky.
- **Polling (`GET /imports/{id}`)** — *Pro:* stateless, cluster-trivial (estado en DB, cualquier nodo responde), resiliente a desconexiones, cacheable con `ETag`. *Con:* latencia de sondeo (mitigable con backoff exponencial).

**Decisión [C-Media].** El contrato MUST ser **Polling** sobre `GET /imports/{id}` (estado + contadores + enlace a reporte de errores), con backoff exponencial e `ETag`/`304`. SSE MAY añadirse como *progressive enhancement* una vez resuelto el enrutamiento en clúster. WebSockets MUST NOT. El estado del job MUST vivir en la tabla `import_job` (no en memoria), habilitando que cualquier nodo responda.

**Consecuencias.** *Gana:* simplicidad, cluster-readiness inmediata, resiliencia. *Pierde:* no es tiempo real (latencia de sondeo).

**Riesgos.** Sondeo agresivo del cliente (mitigar: backoff exponencial + rate-limit vía Bucket4j, ya en el stack).

**Evidencia:** verificación de respuesta correcta desde un nodo distinto al de proceso (prueba de clúster). **Trade-off:** tiempo real sacrificado por stateless/simplicidad. **Fitness Function:** si QPS de polling satura la DB O el negocio exige < 1 s de latencia de progreso → introducir SSE con pub/sub sobre `import_job`.

---

### Resumen de ADRs

| ADR | Decisión | Conf. |
|---|---|---|
| 001 | Spring Batch chunk-oriented + executor acotado; VT MUST NOT ser motor | [C-Media] |
| 002 | Validación Set-Based sobre staging por `jobId`; MUST NOT row-by-row | [C-Alta] |
| 003 | NFS write-once UUID + metadatos DB; MinIO SHOULD post-MVP | [C-Media] |
| 004 | SHA-256 idempotente por estado + redrive sobre `jobId` (ejes ortogonales) | [C-Media] |
| 005 | Polling MUST; SSE MAY (post-clúster); WebSockets MUST NOT | [C-Media] |

*Las `[C-Media]` MUST confirmarse con benchmarks B-00…B-03 antes de declararse `[C-Alta]`.*

---

## BLOQUE 4 — Pipeline, Patrones, Evolución (OCP) y Testing

### 1. Pipeline de Procesamiento

```
[1 Recepción]  →  [2 Val. Estructural]  →  [3 Staging]  →  [4 Val. Negocio]  →  [5 Producción]  →  [6 Cierre]
 202+jobId         tipos/regex/req.        bulk-insert      set-based anti-      UPSERT batched    estado+auditoría
 SHA-256+NFS       por fila, streaming     por jobId        join vs prod.        UNIQUE árbitro    +reporte+notif.
```

Mapeo framework-native **[C-Alta]:** cada paso = Step de Spring Batch. `ItemReader → ItemProcessor → ItemWriter`. `commit-interval = chunk_size` (SP-1).

### 2. Comparación de Patrones

| Patrón | Veredicto | Justificación |
|---|---|---|
| **Pipeline** | **ACEPTADO (macro) [C-Alta]** | El problema es un pipeline de etapas con E/S claras; 1:1 con Steps de Spring Batch. Es la columna vertebral. |
| **Chain of Responsibility** | **ACEPTADO (intra-etapa) [C-Media]** | `List<Validator>` componible para validadores; abierto a extensión (OCP). MUST limitarse *dentro* de una etapa, no como macro-arquitectura. |
| **Strategy** | **ACEPTADO (seam OCP) [C-Alta]** | El parser/lector varía por fuente (xlsx/CSV/JSON/API). Es la bisagra de extensibilidad (§3). |
| **Mediator** | **DESCARTADO [C-Media]** | Spring Batch ya media el flujo entre Steps. Un Mediator propio es ceremonial y añade acoplamiento central. |
| **Saga** | **DESCARTADO [C-Alta]** | Saga resuelve transacciones distribuidas entre servicios con compensaciones. Aquí hay un solo RDBMS + un FS. ACID por chunk + restart de Spring Batch + evidencia write-once con degradación cubren la consistencia. Sin commit distribuido que compensar. |
| **CQRS** | **Ligero ACEPTADO / Pesado DESCARTADO [C-Media]** | La separación `POST /imports` (comando) vs `GET /imports/{id}` (consulta/proyección) es CQRS ligero, ya implícita en ADR-005. CQRS pesado (modelos y almacenes separados) MUST NOT: no hay asimetría de carga que lo justifique. |
| **Event Sourcing** | **DESCARTADO [C-Alta]** | La máquina de estados `import_job` + log de transiciones + metadatos `BATCH_*` ya dan trazabilidad y recuperación. Event Sourcing añade complejidad de replay sin beneficio para este dominio. |

> **Recomendación §1 [C-Media]:** Pipeline + Strategy + CoR + CQRS-ligero. Rechazados: Saga, Event Sourcing, Mediator, CQRS-pesado — sobre-ingeniería para un dominio mono-RDBMS.

### 3. Evolución OCP — Fuentes Heterogéneas

**Objetivo:** aceptar CSV, XML, JSON, APIs de control escolar, ERP/SAP **sin modificar** el core de validación/staging/persistencia.

**Bisagra OCP — el `ItemReader` de Spring Batch es el *seam* natural:**

```
           ┌──────────────── núcleo CERRADO a modificación ────────────────┐
 Fuente → RecordSource (port) → RawStudentRecord (DTO canónico) → ItemProcessor (CoR) → ItemWriter (staging→prod)
    ▲             ▲
    │   ABIERTO a extensión
    └── XlsxRecordSource · CsvRecordSource · XmlRecordSource · JsonApiRecordSource · SapRecordSource …
```

- `interface RecordSource { Stream<RawStudentRecord> open(SourceDescriptor d); }` — produce el DTO canónico, independiente del origen.
- `RawStudentRecord` desacopla parsing de validación/persistencia. MapStruct (ya en el stack) mapea origen → canónico → entidad.
- `RecordSourceFactory` selecciona la implementación por `SourceType` (Strategy). Spring inyecta `Map<SourceType, RecordSource>`.
- Validadores extensibles: `List<Validator>` (CoR) — añadir reglas no modifica las existentes.

**Prueba de OCP — añadir SAP:** implementar `SapRecordSource` + registrar el bean. Cero cambios en `ItemProcessor`, staging, persistencia, máquina de estados o API.

> **Recomendación §3 [C-Media]:** MUST definir `RecordSource` + `RawStudentRecord` como contrato del seam. Nuevas fuentes MUST entrar como Strategy; nuevas reglas como CoR. El core MUST NOT modificarse. **Fitness Function:** si añadir una fuente exige tocar `ItemProcessor`/`ItemWriter`/esquema → el seam OCP se rompió, revisar la abstracción.

### 4. Estrategia Multidimensional de Testing

| Nivel | Objetivo | Herramientas |
|---|---|---|
| **Unit** | Validadores, SHA-256, mappers MapStruct, transiciones de estado, selección de Strategy | JUnit 5 + Mockito + AssertJ; sin contexto Spring (rápidos) |
| **Integration** | Step read→process→write, anti-join de staging, restart de Spring Batch, `UNIQUE`/`SKIP LOCKED` | **Testcontainers MySQL 8 (MUST NOT usar H2 [C-Alta])**, `@SpringBatchTest`, `@DataJpaTest` |
| **Contract** | Estabilidad del contrato API para el frontend | Validación OpenAPI + REST-assured / Spring Cloud Contract |
| **Performance/Load** | Validar SLA (HYP-01 = **B-00**); p50/p95/p99; budget por fase | k6 / Gatling / JMeter + datasets sintéticos + JFR |
| **Stress** | Punto de quiebre: 3M filas, saturar `Q` → `429`, sin OOM | k6 rampa agresiva + monitor de heap |
| **Soak** | Fatiga de memoria/pool/staging a largo plazo | JFR + heap dumps periódicos + Micrometer (SHOULD ≥ 8 h nocturno) |
| **Chaos** | R-3 (kill mid-batch/RTO), R-6 (NFS lleno), timeout DB, flood duplicados (ADR-004) | Toxiproxy, Chaos Monkey for Spring Boot, kill manual de contenedor |

> **Notas:**
> - Integration MUST correr sobre MySQL real (Testcontainers). H2 oculta `SKIP LOCKED`, collation e InnoDB → falsa confianza.
> - Soak Testing es el único que valida creep de heap (invisible en pruebas cortas).
> - Contract Tests MUST ser bloqueantes de merge.
> - Cobertura SHOULD ≥ 80% en validadores y máquina de estados. Sin número global fabricado para el resto.
> - **B-00 (Performance/Load) es la prueba que convierte HYP-01 de `[C-Baja]` en evidencia.**
> - Chaos y Soak MUST ejecutarse antes de GA (cubren R-3 y creep de memoria).

---

## BLOQUE 5 — Resiliencia, Seguridad, Observabilidad y Autocrítica

### 1. Inyección de Fallos y Resiliencia Extrema

**Escenario 1: NFS al 100% en plena escritura**

Health-check rechaza nuevas subidas al 90% (preventivo, ADR-003). Si el volumen se llena a mitad de la escritura de evidencia → `ENOSPC`. Como la evidencia (Fase 1) está desacoplada del dato (staging = InnoDB local), el import MUST NOT abortar: procesa normalmente, marca `evidence_status=PENDING` y encola el blob (del temp local del multipart) para reintento posterior. Sin rollback de datos. Dispara alerta `nfs_free < 10%`.

**Escenario 2: DB se desconecta / timeout a mitad de la migración staging→producción**

Fase 5 corre en transacciones por chunk (`commit-interval`). Timeout intra-chunk → rollback ACID de ese chunk; los chunks ya confirmados permanecen (commit parcial por diseño). Spring Batch marca el Step como fallido. Al restart, reanuda desde el último checkpoint (ADR-001) — los chunks confirmados no se reprocesan. HikariCP valida conexión + Spring Retry con backoff exponencial ante timeouts transitorios. RPO ≤ 1 chunk se cumple.

**Requisito duro:** el UPSERT de Fase 5 MUST ser idempotente (`INSERT … ON DUPLICATE KEY UPDATE`) para que el replay del chunk no duplique filas.

**Escenario 3: DoS por duplicación intencional (mismo archivo 15× en 3 s)**

Defensa en capas: (1) Bucket4j (ya en el stack) limita `POST /imports` por IP/usuario → mayoría reciben `429`. (2) Idempotencia SHA-256 (ADR-004): el primero crea job activo; los idénticos → `409`. Carrera simultánea resuelta por `UNIQUE(sha256, activo)` + manejo de `DuplicateKey` → solo uno gana. (3) Cola `Q` acotada → `429` al saturar. Resultado: 1 job procesa, 14 rechazados sin parsing. Sin amplificación.

**Escenario 4: Instancia killed (OOM-killer/restart) con lote a medias**

`JobRepository` persiste el `StepExecution`; la ejecución muerta queda `STARTED`. Reconciliación: cron ShedLock detecta jobs `STARTED` con heartbeat/lease vencido (timestamp actualizado por chunk) → los marca `FAILED` y dispara restart (reanuda desde último chunk confirmado). En clúster: `SELECT … FOR UPDATE SKIP LOCKED` evita que dos nodos reclamen el mismo job. RTO ≤ 5 min vía cadencia del cron. Chunks confirmados sobreviven (ACID); chunk en vuelo se repite (RPO ≤ 1, seguro por UPSERT idempotente). El OOM en sí se previene con parser streaming (HYP-03) + `P` acotado.

**Escenario 5: Archivo con 3M filas que pasó los controles del cliente**

El servidor MUST NOT confiar en el cliente. (1) Límite multipart del endpoint de import elevado pero con techo. (2) Parser streaming → heap O(1) (HYP-03). (3) Guard de conteo en streaming: si filas > `MAX_ROWS` (política a definir con stakeholder) → abort temprano `422` + job `REJECTED` antes de procesar todo. (4) Guard de ZIP-bomb (§2). Resultado: rechazo elegante dentro de política o proceso lento-pero-seguro. Sin OOM ni caída.

> **Recomendación §1 [C-Media]:** El invariante crítico MUST ser: ningún fallo deja datos de producción en estado inconsistente (peor caso = 1 chunk replayado, seguro por UPSERT idempotente). La evidencia NFS es el único componente best-effort, aislado a propósito.

### 2. Seguridad y Vectores de Ataque

| Vector | Control |
|---|---|
| **Magic numbers / MIME** | MUST validar firma binaria, no extensión ni `Content-Type`. `.xlsx` = ZIP (`PK\x03\x04`) que MUST contener `[Content_Types].xml` con content-type OOXML de hoja de cálculo. MUST rechazar `.xls` (OLE2/CFB). [C-Alta] |
| **ZIP-bomb (OOXML)** | OOXML es ZIP → MUST imponer límites de descompresión: tamaño inflado total máx., ratio de compresión máx., nº de entradas máx. (con fastexcel: envolver `ZipInputStream` en guard acotado). Cortar antes de parsear. [C-Media] |
| **Path Traversal** | Nombre de archivo del cliente MUST NOT usarse en ninguna ruta. Almacenar bajo UUID generado en servidor: `{base}/{yyyy}/{mm}/{uuid}.xlsx`. Mount `no_exec`. [C-Alta] |
| **Excel Injection** | Celdas que inician con `= + - @` MUST neutralizarse en reportes/exportaciones. MUST NOT evaluarse en el servidor. [C-Media] |
| **Cifrado en tránsito** | TLS/HTTPS en la API + `sec=krb5p` en NFS. [C-Media] |
| **Cifrado en reposo** | Cifrado de volumen (LUKS/dm-crypt) para NFS + cifrado en MySQL. CURP/email = datos personales (LFPDPPP) → SHOULD cifrado en reposo + control de acceso estricto. [C-Media] |
| **Permisos FS mínimos** | Usuario de servicio dedicado; `0700` directorio, `0600` archivos; `root_squash`; `no_exec`. Least privilege. [C-Alta] |
| **AuthZ** | Solo rol ADMIN MUST poder `POST /imports`. IDs de rol internos MUST coincidir back/front (lección ADMIN/ADMINISTRADOR). JWT existente. [C-Alta] |

### 3. Observabilidad Empresarial

**Estado actual [C-Alta]: observabilidad = 0.** Sin Actuator/Micrometer/OTel en `pom.xml`. Es **deuda bloqueante de GA** (R-5). MUST introducir:
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
- `micrometer-tracing-bridge-otel` (trazas distribuidas)

**Reto crítico de propagación async [C-Alta]:** el MDC (Correlation ID / `traceparent` W3C) MUST propagarse a los hilos worker de Spring Batch vía `TaskDecorator` que copie el MDC al cruzar de hilo. Sin esto, el `jobId`/traceId se pierde y los logs son inútiles para diagnosticar.

**Métricas MUST exponer (Prometheus/Grafana):**
- `import_phase_duration_seconds{phase}` → SLOs del budget (Bloque 1 §5)
- `import_rows_processed_total`, `import_rows_rejected_total{reason}`, `import_throughput_rows_per_second`
- `import_jobs_active`, `import_queue_depth`
- `hikaricp_connections_active{pool=import}`, `jvm_memory_used_bytes`, `nfs_write_duration_seconds`, `nfs_free_bytes`

**Umbrales de alerta:**
- Fase > budget ×1.3 (tabla §5)
- `import_queue_depth > Q × 0.8`
- Heap > umbral (Fitness Function de RAM)
- `hikaricp_connections_active = max` (saturación del pool)
- `nfs_free_bytes < 10%`

> **Recomendación §3 [C-Media]:** Sin esta instrumentación el Performance Budget y todas las Fitness Functions son **inverificables**. MUST introducir antes de GA. Cada métrica mapea 1:1 a un riesgo (R-1…R-6) o a un SLO del budget.

### 4. Revisión Crítica Independiente (Autocrítica del ARB)

*El auditor externo critica el diseño de los Bloques 1–4:*

**Crítica 1 — SPOF real ignorado: MySQL único [C-Alta]**
Todo el "escalado" cabalga sobre una sola instancia MySQL: staging, anti-joins, `JobRepository` y persistencia batched golpean la misma DB. Bajo N imports concurrentes, **la pared es la DB, no la app**. El documento optimizó parser y concurrencia (R-1/R-2) pero subestimó que la DB es el verdadero cuello de throughput y el SPOF no mitigado.
*Optimización:* read replica para los anti-joins de validación; evaluar esquema/DB de import separado de la OLTP; particionar staging.

**Crítica 2 — Sobre-ingeniería frente a escala no confirmada [C-Media] (la más importante)**
El diseño se calibró para 100k–3M filas / alta concurrencia, pero A-05/A-07 son hipótesis `[C-Media]`/`[C-Baja]`, no mediciones. Para la escala real de estadías ICF (posiblemente miles de alumnos), Spring Batch + ShedLock + staging + pool aislado puede ser un cañón para matar un mosquito. Un `ThreadPoolTaskExecutor` acotado + JDBC batch simple podría ser suficiente.
*Acción:* **right-sizing con el stakeholder y B-00 ANTES de construir**. Si los volúmenes reales son miles y no cientos de miles, evaluar descartar Spring Batch (posible reversión de ADR-001).

**Crítica 3 — Deuda técnica de migraciones [C-Alta]**
Migraciones manuales por SQL. Añadir `BATCH_*` + `staging` + `import_job` a mano es frágil y propenso a drift dev/prod. SHOULD adoptar Flyway o Liquibase antes de introducir estas tablas.

**Crítica 4 — Evidencia best-effort contradice la auditoría [C-Media]**
ADR-003 hace la evidencia NFS best-effort con reintento. Si la evidencia es un requisito de cumplimiento duro, "best-effort" es inaceptable: un fallo de NFS dejaría un import SUCCESS sin evidencia. El trade-off TP-5 no está resuelto — está diferido. MUST escalarse al stakeholder antes de GA. Si la auditoría es dura → la evidencia MUST ser durable-transaccional (DB o MinIO desde ahora, no post-MVP), contradiciendo A-03.

**Crítica 5 — Sobrecarga de Polling sobre la misma MySQL [C-Media]**
N clientes sondeando añaden QPS a la misma DB del punto 1. Mitigado con backoff/`ETag`/rate-limit, pero es deuda latente si crece la concurrencia. Vigilar con la métrica `import_queue_depth` y el QPS del endpoint de polling.

**Crítica 6 — `MAX_ROWS` y techo multipart sin valor [C-Baja]**
El diseño los exige pero no los fija (correcto: no inventar cifras). MUST definirse con el stakeholder antes de GA; sin ese valor los guards son teóricos.

> **Veredicto del auditor [C-Media]:** El diseño es sólido en resiliencia y seguridad, pero **arquitectónicamente sobredimensionado para una escala no confirmada** y con un **SPOF de DB no mitigado**. Acción #1 antes de escribir código: validar A-05/A-07 con B-00 y, según el resultado, **simplificar agresivamente**. "La mejor optimización es no construir lo que no se necesita."

---

## Cierre del SAD

**Riesgos bloqueantes de GA** (R-1/R-2/R-3/R-5): OOM (parsing DOM), agotamiento de pool, pérdida de estado ante kill, cero observabilidad. MUST resolverse antes del lanzamiento.

**Benchmarks obligatorios:**
- **B-00** — Carga end-to-end (SLA). Convierte HYP-01 de hipótesis a evidencia.
- **B-01** — Heap del parser (HYP-03). Confirma elección de fastexcel.
- **B-02** — Batch vs row-by-row (HYP-04). Justifica `rewriteBatchedStatements`.
- **B-03** — Pool aislado vs API (HYP-05). Justifica el `DataSource` secundario.

**Deuda de infraestructura pre-GA (MUST):**
1. `spring-boot-starter-actuator` + `micrometer-registry-prometheus` + OTel.
2. Techo multipart dedicado para el endpoint de import.
3. Flyway/Liquibase antes de introducir `BATCH_*`/`staging`/`import_job`.
4. Confirmación de A-01 (hardware real) y A-02 (pool size real) con Infra.
5. Escalación al stakeholder de: (a) evidencia como requisito duro vs best-effort (TP-5), (b) `MAX_ROWS` y techo multipart del módulo.

**ADR pendientes de upgrade a `[C-Alta]`:** ADR-001, ADR-003, ADR-004, ADR-005 — al completar sus benchmarks B-00…B-03.

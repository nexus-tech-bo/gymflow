# Reporte de QA exploratorio — GymFlow

**Fecha:** 2026-10-12  
**Entorno:** frontend Angular 22 en `http://localhost:4200` (proxy de `/api` a `http://localhost:8080`), backend Spring Boot en `http://localhost:8080` (levantado, PostgreSQL con los datos semilla de `DataSeeder`: gimnasio "Iron Gym", zona `America/Lima`).  
**Cobertura:** roles OWNER (`owner@irongym.com`), ADMIN (`admin@irongym.com`) y RECEPTIONIST (`recepcion@irongym.com`) del gimnasio 1, más un OWNER de un gimnasio nuevo registrado durante la prueba (gimnasio 2) para verificar el aislamiento entre tenants. Viewports de 1440×900 y 375×812. Navegador headless (Edge vía `playwright-core`) y `curl` contra la API.  
**Flujos probados:** login (validaciones, credenciales incorrectas, usuario desactivado, `?redirect=`), registro de gimnasio, guards por rol en las 10 rutas del shell, dashboard, check-in (DNI, payload QR `GF1:`, duplicados, todos los motivos de rechazo), socios (búsqueda, paginación por URL, alta, edición, activar/desactivar), detalle de socio (vender, renovar, congelar, descongelar, cancelar, carnet QR, regenerar QR), tarjeta pública `/card/:token`, caja (abrir, cierre ciego del recepcionista, cierre con diferencia del OWNER, anular pago), historial y detalle de cajas, planes, staff y reporte de ingresos.

Severidad: **crítica** (caída del servicio, fuga de datos entre tenants o corrupción), **alta** (falla de seguridad o de regla de negocio central), **media** (resultado incorrecto, validación faltante o error mal manejado), **baja** (pulido, textos, consistencia).

---

## Backend

### B1. Un usuario de staff desactivado sigue operando con su token durante hasta 8 horas
- **Severidad:** alta
- **Endpoint:** todos los protegidos (verificado con `GET /api/me` y `POST /api/members`)
- **Pasos:**
  1. Como OWNER: `POST /api/staff` crea a "QA Recep Uno" (`qa.recep1@test.com`, id 4). QA Recep Uno inicia sesión y guarda su token.
  2. Como OWNER: `PATCH /api/staff/4` con `{"active":false}` responde 200.
  3. Con el token del paso 1: `GET /api/me` responde `200` y `POST /api/members` con `{"fullName":"QA Socio Fantasma","dni":"QA000001"}` responde `201` (socio id 6 creado).
- **Esperado:** 401 en cuanto el usuario se desactiva. La propia UI lo promete: "¿Desactivar a X? Ya no podrá iniciar sesión."
- **Obtenido:** solo se bloquea el *nuevo* login (`POST /api/auth/login` responde 401). Las sesiones abiertas siguen vivas hasta que el JWT expira (`JWT_EXPIRATION_MS=28800000`, 8 h), así que un recepcionista despedido puede seguir vendiendo, registrando ingresos y cerrando caja durante todo el turno.
- **Evidencia:** `JwtAuthFilter.java:37-45` autentica solo con los claims del token y nunca consulta `staff.active`. `StaffServiceImpl.findMe` (`:36-40`) tampoco filtra por `active`.

### B2. Un cambio de rol no se aplica hasta que el token expira: un ADMIN degradado conserva sus permisos
- **Severidad:** alta
- **Endpoint:** todos los que usan `@PreAuthorize` (verificado con `GET /api/staff` y `GET /api/cash/sessions`)
- **Pasos:**
  1. Como OWNER: crear a "QA Admin Uno" (`qa.admin1@test.com`, id 5) con rol ADMIN. Él inicia sesión.
  2. Como OWNER: `PATCH /api/staff/5` con `{"role":"RECEPTIONIST"}` responde 200.
  3. Con el token de QA Admin Uno: `GET /api/me` devuelve `"role":"RECEPTIONIST"`, pero `GET /api/staff` y `GET /api/cash/sessions` (solo OWNER/ADMIN) responden `200` con los datos.
- **Esperado:** que los permisos sigan al rol vigente: 403 en los endpoints de gestión desde el momento de la degradación.
- **Obtenido:** el rol sale del claim `role` del JWT, así que el usuario degradado sigue gestionando staff, anulando pagos, viendo reportes, etc. durante 8 h. El frontend hace `hydrate()` y oculta el menú de gestión (muestra RECEPTIONIST), pero la API sigue aceptando las llamadas.
- **Evidencia:** `JwtAuthFilter.java:39-44` (`ROLE_` + claim del token), `JwtUtil.generate` (`:41-51`).

### B3. El "cierre ciego" de caja no es ciego: el recepcionista recibe y ve cada pago con su monto y método
- **Severidad:** alta
- **Endpoint:** `GET /api/cash/current` (y la respuesta de `POST /api/cash/close`)
- **Pasos:**
  1. Como RECEPTIONIST: `GET /api/cash/current`.
  2. En la UI, entrar a `/caja` como recepcionista.
- **Esperado:** en un cierre ciego, el recepcionista no puede deducir el efectivo esperado. Por eso el backend anula `expectedCash`, `totals` y `difference`.
- **Obtenido:** la respuesta trae `expectedCash:null` y `totals:null`, pero también `payments[]` completo con `amount` y `method` de cada pago. La pantalla "Caja" muestra el monto inicial (Bs 100,00) y la tabla "Pagos de esta caja" con Efectivo/QR y montos (Bs 10,00 en efectivo). El recepcionista calcula al instante el efectivo esperado (100 + 10 = 110), y el control antifraude queda anulado.
- **Evidencia:** `CashServiceImpl.java:104-110` (`toDetailResponse` devuelve `paymentMapper.toResponseList(payments)` aunque `blind` sea true). En el frontend, `cash-page.component.html:27-63` renderiza la tabla de pagos para todos los roles. Captura `shots/RECEPTIONIST_d__caja.png`.

### B4. Cancelar (o anular el pago de) la membresía activa deja la renovación ya pagada programada en el futuro
- **Severidad:** media
- **Endpoints:** `POST /api/memberships/{id}/cancel`, `POST /api/payments/{id}/void`
- **Pasos:**
  1. Socio 7 con Mensual activa del 12/10 al 10/11. Se vende una renovación Trimestral, que queda programada del 11/11/2026 al 08/02/2027 (pago id 8).
  2. Cancelar la Mensual: `POST /api/memberships/5/cancel` responde 200.
  3. Check-in con DNI `QA000002`.
- **Esperado:** que la renovación pagada se reprograme y empiece hoy (como ya hace `unfreeze` con las renovaciones en cola), o al menos advertirlo.
- **Obtenido:** la Trimestral sigue empezando el 11/11. El check-in responde `DENIED / NOT_STARTED "Su membresía aún no inicia"`: el socio pagó Bs 270 y no puede entrar durante 30 días. Pasa lo mismo al anular el pago de la membresía activa desde Caja.
- **Evidencia:** `MembershipServiceImpl.java:132-141` y `PaymentServiceImpl.java:47-49` no tocan las membresías posteriores. En cambio, `MembershipServiceImpl.java:117-122` (unfreeze) sí las desplaza. Captura `shots/member7_scheduled_gap.png`.

### B5. La búsqueda de socios no encuentra DNI alfanuméricos en minúsculas ni nombres sin tildes, y `_`/`%` actúan como comodines
- **Severidad:** media
- **Endpoint:** `GET /api/members?q=`
- **Pasos y resultados (UI `/socios`):**
  - `q=QA000002` devuelve 1 resultado, pero `q=qa000002` devuelve 0. El DNI se guarda en mayúsculas (`normalizeDni`) y la búsqueda no normaliza `q`. El check-in sí acepta `qa000002`, lo cual es inconsistente.
  - `q=pérez` devuelve 1 resultado, pero `q=perez` devuelve 0. Un recepcionista que escribe sin tildes no encuentra a "Juan Pérez".
  - `q=_` devuelve los 7 socios, porque `_` y `%` no se escapan en el `LIKE`.
- **Esperado:** normalizar `q` igual que el DNI (`toUpperCase`), comparar sin tildes (`unaccent` o una columna normalizada) y escapar los comodines.
- **Evidencia:** `MemberRepository.java:36-37`, `MemberServiceImpl.java:49`.

### B6. El log registra SQL y los parámetros enlazados (hashes de contraseña y datos personales) en todos los perfiles, incluido producción
- **Severidad:** media
- **Archivo:** `src/main/resources/application.yaml:15` (`show-sql: true`) y `:31` (`org.hibernate.orm.jdbc.bind: TRACE`)
- **Detalle:** `application-dev.yaml` y `.env` dicen que esto es "Local development only (SPRING_PROFILES_ACTIVE=dev)", pero el `application.yaml` base trae lo mismo. Así, el despliegue de `render.yaml` (sin perfil dev) escribe en el log los valores de cada parámetro SQL: emails, DNI, teléfonos y hashes BCrypt. **Confirmado solo por lectura de la configuración** (no tengo acceso a los logs del servidor).

### B7. No existe ninguna forma de cambiar una contraseña, aunque la UI la llama "temporal"
- **Severidad:** media
- **Endpoints:** no hay ninguno. `PATCH /api/staff/{id}` (`StaffUpdateRequest`) solo acepta `fullName`, `role` y `active`, y no hay endpoint `/api/me/password` ni de reseteo.
- **Pasos:** como ADMIN, en `/staff` pulsar "Nuevo usuario": el campo se llama "Contraseña temporal *", con la ayuda "Compártela con la persona".
- **Esperado:** que el usuario pueda cambiarla al primer ingreso, o que un OWNER/ADMIN pueda resetearla.
- **Obtenido:** la contraseña compartida queda para siempre. Además, como en `/staff` nadie puede editar su propia fila (`canManage` devuelve false para `row.id === me.id`), nadie puede corregir su propio nombre.
- **Evidencia:** `dto/staff/StaffUpdateRequest.java`, `StaffController.java`, `MeController.java` (solo `GET`). En el frontend, `staff-form-dialog.component.html` (etiqueta "Contraseña temporal").

### B8. `daysLeft` de una membresía programada cuenta desde hoy: "119 días restantes" en un plan de 90 días
- **Severidad:** baja
- **Endpoint:** cualquier respuesta con `MembershipResponse` (`GET /api/members/{id}`, check-in, etc.)
- **Pasos:** en el caso de B4, la Trimestral (90 días) programada del 11/11 al 08/02 devuelve `daysLeft: 119`, y el detalle del socio muestra "119 días restantes".
- **Esperado:** para `SCHEDULED`, la duración del plan (o mostrar "Inicia el 11/11").
- **Evidencia:** `MembershipRules.java:45` (`case ACTIVE, SCHEDULED -> DAYS.between(today, endDate)`).

### B9. El teléfono del socio acepta cualquier texto
- **Severidad:** baja
- **Endpoint:** `POST/PUT /api/members`
- **Pasos:** crear un socio con teléfono `abc!!` desde el diálogo "Nuevo socio". Responde `201` y se muestra "Teléfono: abc!!".
- **Esperado:** 400 con un patrón de dígitos, espacios y `+`. El frontend tampoco lo valida (`member-form-dialog.component.ts:34`, solo `maxLength(20)`).
- **Evidencia:** `dto/member/MemberRequest.java:22` (solo `@Size(max = 20)`). Captura `shots/member_created_detail.png`.

### B10. Un usuario desactivado que ingresa la contraseña correcta recibe "Correo o contraseña incorrectos"
- **Severidad:** baja
- **Endpoint:** `POST /api/auth/login`
- **Pasos:** iniciar sesión con `qa.recep1@test.com` / `qapass123` (desactivado en B1).
- **Esperado:** después de validar la contraseña, un mensaje del tipo "Tu usuario está desactivado, habla con el administrador". La contraseña ya se verificó, así que esto no filtra si el email existe.
- **Obtenido:** `401 "Credenciales inválidas"`. La UI muestra "Correo o contraseña incorrectos." y el usuario cree que olvidó su clave.
- **Evidencia:** `AuthServiceImpl.java:95-97`.

### B11. El registro público de gimnasios no tiene límite y cada llamada crea un schema completo
- **Severidad:** baja
- **Endpoint:** `POST /api/auth/register-gym` (público)
- **Detalle:** cada registro crea `gym_N` y ejecuta todas las migraciones de Flyway (`AuthServiceImpl.java:55-58`). No hay rate limiting, captcha ni verificación de email, así que un script puede crear miles de schemas y degradar PostgreSQL y el arranque, porque `SchemaProvisioningServiceImpl.run` migra todos los gimnasios al iniciar. **Confirmado por lectura de código.** Solo hice un registro real ("QA Gym Dos") para no ensuciar la base de datos.

### B12. `sort` acepta cualquier propiedad anidada, incluido el hash de contraseña
- **Severidad:** baja
- **Endpoint:** `GET /api/cash/sessions?sort=openedBy.account.password` (OWNER/ADMIN)
- **Obtenido:** `200`, con las cajas ordenadas por el hash BCrypt de quien las abrió. Los nombres inválidos sí dan 400 (`sort=foo`), pero no hay lista blanca de campos ordenables.
- **Esperado:** limitar `sort` a las columnas que expone la UI.

---

## Frontend

### F1. En mobile (375 px) las tablas recortan columnas clave y las acciones quedan fuera de la vista
- **Severidad:** media
- **Pantallas:** `/socios`, `/caja`, `/staff`, `/cajas` · 375×812 · todos los roles
- **Pasos:** abrir cualquiera de esas pantallas en 375 px.
- **Esperado:** en mobile, tarjetas o columnas priorizadas, de modo que el estado, el monto y las acciones se vean sin desplazar.
- **Obtenido:** la tabla se desplaza en horizontal dentro del panel sin ninguna pista visual. En Socios, el chip "Vence en 3 d…" queda cortado y Estado/Acciones no se ven. En Caja solo se ven Hora, Socio y Método: el **Monto** y el botón "Anular pago" quedan fuera. En Staff, el rol aparece cortado ("Propietari…") y los botones Editar/Desactivar no se ven. En Historial de cajas, "Estado" queda cortado. La página no tiene scroll horizontal global.
- **Evidencia:** capturas `shots/OWNER_m__socios.png`, `OWNER_m__caja.png`, `OWNER_m__staff.png`, `OWNER_m__cajas.png`. Código: `members-page.component.html:25`, `payments-table.component.html:4`, `staff-page.component.html:8`, `cash-sessions-page.component.html:4` (`overflow-x-auto` sin layout móvil).

### F2. El diálogo "Cerrar caja" lanza NG0100 (ExpressionChangedAfterItHasBeenChecked) cada vez que se abre y al mostrar el resultado
- **Severidad:** media
- **Pantalla:** `/caja` → "Cerrar caja" · RECEPTIONIST y OWNER · 1440
- **Pasos:** abrir el diálogo "Cerrar caja". Como OWNER, además, confirmar el cierre (el diálogo cambia a "Caja cerrada").
- **Obtenido:** `ERROR RuntimeError: NG0100: ExpressionChangedAfterItHasBeenCheckedError … Previous value for 'attr.aria-labelledby': 'undefined'. Current value: 'dialog-title-1'. Expression location: CdkDialogContainer`. Al pasar al resultado aparece otra vez (`'dialog-title-2'` → `'dialog-title-3'`). No ocurre en los demás diálogos.
- **Causa:** es el único diálogo cuyo `<app-dialog-frame>` está dentro de `@if/@else`, así que el frame se crea después de que el contenedor CDK ya se verificó, y su constructor llama a `container._addAriaLabelledBy(...)` en ese momento.
- **Evidencia:** consola. Código: `close-cash-dialog.component.html:1,46-47` y `shared/ui/dialog-frame.component.ts:44-48`. Corregir: mover el `@if` dentro de un único `app-dialog-frame` (con el título vinculado) o registrar el `aria-labelledby` fuera del ciclo de verificación.

### F3. Una página fuera de rango en Socios dice "Aún no tienes socios" junto a "7 socios"
- **Severidad:** baja
- **Pantalla:** `/socios?page=99` · cualquier rol
- **Obtenido:** el encabezado dice "7 socios" y el cuerpo muestra el estado vacío inicial "Aún no tienes socios. Registra al primero…", sin paginación para volver.
- **Esperado:** "Esta página no existe" con un enlace a la primera página, o corregir el `page` al último válido.
- **Evidencia:** captura `shots/socios_page99.png`. Código: `members-page.component.html:38-52` (solo distingue entre búsqueda y lista vacía, no mira `totalItems`).

### F4. Textos de días y duplicados confusos en Check-in y Dashboard
- **Severidad:** baja
- **Pantallas:** `/check-in`, `/dashboard`
- **Detalle:**
  - Check-in de Sofía Ramos (Diario que vence hoy): "Diario · vence 12/10/2026 · **quedan 0 días**". Por código, con 1 día dice "quedan 1 días" (`check-in-page.component.html:76-77`, sin singular ni el caso "vence hoy" que sí existe en `expiringChip`).
  - Ingreso duplicado: "Ya había ingresado hoy. Ya registró su ingreso" repite la idea, porque concatena el texto fijo con `r.message` (`check-in-page.component.html:87`).
  - Dashboard "Vencen pronto": la columna Días muestra el chip "0" en lugar de "Hoy" (`dashboard-page.component.ts:74-76`).
- **Evidencia:** captura `shots/checkin_sofia.png`, `shots/O_dashboard_cash_closed.png`.

### F5. "Renovar" sigue habilitado con la membresía congelada y falla después de llenar el formulario
- **Severidad:** baja
- **Pantalla:** `/socios/:id` · OWNER/ADMIN
- **Pasos:** congelar la membresía actual, pulsar "Renovar", elegir un plan y confirmar.
- **Obtenido:** recién al enviar aparece "Descongele la membresía antes de renovar" (409).
- **Esperado:** deshabilitar "Renovar" con esa explicación (como ya se hace con el socio inactivo y `aria-describedby`).
- **Evidencia:** `member-detail-page.component.html:83-90` (solo `[disabled]="!m.active"`).

### F6. Los nombres largos sin espacios desbordan el título de la página
- **Severidad:** baja
- **Pantalla:** `/socios/:id` · 1440 y 375
- **Pasos:** crear un socio con un nombre de 117 caracteres con palabras largas ("QA Socio Largo NombreNombre…").
- **Obtenido:** el `h1` sale del área visible y se corta en el borde derecho. La tarjeta pública sí lo parte bien.
- **Evidencia:** captura `shots/member_created_detail.png`, `shots/m_member_detail.png`. Código: `shared/ui/page-header.component.ts:10` (sin `break-words`/`min-w-0` en el `h1`).

### F7. Un rango de fechas inexistente en la URL del reporte muestra el error crudo del backend con un "Reintentar" inútil
- **Severidad:** baja
- **Pantalla:** `/reportes/ingresos?from=2026-02-31&to=2026-10-12` · OWNER/ADMIN
- **Obtenido:** el selector muestra "31/02/2026 – 12/10/2026", el backend responde 400 y la pantalla dice "Valor inválido para el parámetro 'from'" con el botón "Reintentar", que vuelve a fallar.
- **Esperado:** validar que la fecha exista en `rangeError` y volver al rango por defecto.
- **Evidencia:** captura `shots/report_invalid_date.png`. Código: `core/utils/format.util.ts:113-118`.

### F8. Contraseña: el frontend permite 72 caracteres, pero el backend limita a 72 *bytes*, con un mensaje técnico
- **Severidad:** baja
- **Pantallas:** `/registro`, diálogo "Nuevo usuario"
- **Pasos:** registrar un gimnasio con una contraseña de 40 "ñ" (80 bytes).
- **Obtenido:** pasa la validación del cliente (`maxLength(72)`) y el servidor responde "no debe superar 72 bytes". El usuario no sabe qué es un byte.
- **Evidencia:** `register-page.component.ts:31`, `staff-form-dialog.component.ts:39`. Sugerencia: validar bytes con `TextEncoder` y explicar el límite ("máx. 72 caracteres sin tildes").

### F9. Las pantallas "no encontrado" no tienen encabezado `h1`
- **Severidad:** baja
- **Pantallas:** `/socios/9999`, `/socios/abc`, `/cajas/9999`
- **Obtenido:** el título "Socio no encontrado" / "Caja no encontrada" no es un encabezado, así que un lector de pantalla no tiene `h1` en la página (el resto de las pantallas sí lo tiene).
- **Evidencia:** captura `shots/ADMIN_d__socios_9999.png`. Código: `member-detail-page.component.html:264`, `cash-session-detail-page.component.html:83`.

### F10. Configuración regional mezclada: moneda boliviana con zona horaria y términos de Perú
- **Severidad:** baja
- **Archivo:** `core/utils/format.util.ts:5-8`
- **Detalle:** el dinero se formatea con `es-BO` / `BOB` ("Bs"), pero la hora se fija en `America/Lima` (UTC-5), las fechas usan `es-PE` y el documento se llama "DNI". Si el gimnasio está en Bolivia (UTC-4), entre las 23:00 y las 24:00 locales los ingresos y pagos se asignan al día anterior. Además, el frontend ignora `APP_TIMEZONE` del backend porque la zona está fija en el cliente. **Inconsistencia confirmada en el código.** Hay que confirmar el país objetivo antes de corregir.

---

## Lo que funcionó bien (cubierto y sin defectos)
- Guards por rol: el RECEPTIONIST es redirigido a `/check-in` con el toast "No tienes permiso…" desde `/cajas`, `/cajas/:id`, `/reportes/ingresos` y `/staff` (2 viewports). `/login?redirect=` solo acepta rutas internas. Las rutas desconocidas van al inicio de cada rol.
- Aislamiento entre tenants: el OWNER del gimnasio 2 recibe 404 en socios, cajas, membresías, pagos y staff del gimnasio 1. El check-in con el QR o el DNI de un socio del gimnasio 1 responde "Socio no encontrado".
- Login: validación de vacíos, mensaje correcto ante una contraseña incorrecta, email con espacios finales tolerado. Un token alterado provoca el toast "Tu sesión expiró" y la redirección con `redirect`.
- Socios: validaciones del cliente (nombre en blanco, DNI de 6 a 12 caracteres, email), DNI duplicado mostrado en el campo, Esc cierra el diálogo y devuelve el foco a "Nuevo socio", desactivar/activar con confirmación, botón de venta deshabilitado con el socio inactivo.
- Venta: idempotencia (doble clic = 1 POST), cambio de precio solo para OWNER/ADMIN (el recepcionista no ve el campo), referencia solo para QR, renovación encolada al día siguiente del vencimiento, bloqueo de venta sin caja abierta con el atajo "Ir a Caja".
- Congelar, descongelar y cancelar con diálogos `alertdialog` y toasts. Regenerar QR invalida el enlace anterior (404). La tarjeta pública muestra el estado y un error amable para tokens inválidos o de socios inactivos.
- Check-in: autofocus que se mantiene después de cada lectura, botón deshabilitado con el campo vacío, todos los motivos (vencida, sin membresía, aún no inicia, código inválido, no encontrado, duplicado en 2 h) y log del día actualizado.
- Caja: validaciones (negativo, 3 decimales), cierre ciego con toast para el recepcionista, cierre del OWNER con diferencia en vivo y resumen, "Ver detalle", anular pago con motivo obligatorio y cancelación de la membresía asociada, totales correctos en el historial y el detalle.
- Planes (nombre duplicado sin distinguir mayúsculas, decimales, rango, mostrar inactivos), Staff (el ADMIN solo crea recepcionistas, email duplicado mostrado en el campo, sin acciones sobre el OWNER ni sobre la propia fila), reporte (rango invertido, año vacío, totales por método y por día).
- Sin scroll horizontal de página en 1440 ni en 375. Menú móvil con focus trap, Esc y devolución del foco. Ningún control visible sin nombre accesible.

## No probado o limitaciones
- El escáner de cámara (`getUserMedia`/BarcodeDetector) no se probó porque el navegador headless no tiene cámara. Solo se probaron la entrada manual y el payload `GF1:` tipeado.
- No se probó la paginación real (más de 20 socios o cajas) para no crear decenas de registros. Solo se probaron los parámetros `page` por URL.
- B6 y B11 se confirmaron solo leyendo código y configuración. No revisé los logs del servidor ni ejecuté registros masivos para no degradar el entorno compartido.
- No probé la expiración real del token (8 h) ni la vuelta de medianoche. El contraste WCAG no se midió de forma sistemática.

## Nota sobre los datos de prueba creados
No hay endpoints para borrar, así que estos registros siguen en la base de datos (desactivados o anulados donde la UI lo permite):
- **Gimnasio 1 (Iron Gym):**
  - Staff: id 4 "QA Recep Uno" (`qa.recep1@test.com`), id 5 "QA Admin Uno" (`qa.admin1@test.com`, degradado a RECEPTIONIST) e id 6 "QA Recep Dos" (`qa.recep2@test.com`). Los tres están **inactivos** y la contraseña de todos es `qapass123`.
  - Socios: id 6 "QA Socio Fantasma" (DNI `QA000001`) e id 7 "QA Socio Largo Nombre…" (DNI `QA000002`, teléfono `abc!!`). Ambos inactivos. El socio 7 tiene la Mensual 5 y la Diario 6 canceladas, y la Trimestral 8 cancelada por la anulación de su pago.
  - Plan: "QA Plan Semanal" (inactivo).
  - Pagos: id 7 (Pedro Castillo, Diario, anulado), id 8 (socio 7, anulado). Los pagos 5 (Bs 80, QR) y 6 (Bs 10, efectivo) del socio 7 **no están anulados**, porque su caja ya está cerrada, y siguen sumando en el reporte de ingresos del 12/10.
  - Cajas: la caja semilla id 2 quedó **cerrada** por Rosa (contado Bs 105, diferencia −15). La id 3 (abierta por Rosa con Bs 50) quedó cerrada por el OWNER con contado Bs 45 (−5). Queda **abierta la caja id 4** (Bs 100, abierta por Carlos Dueño).
  - Además: varios check-ins del 12/10 (permitidos y denegados) de Juan Pérez, Luis Torres, Pedro Castillo y los socios QA, y el QR del socio 6, que fue regenerado.
- **Gimnasio 2 "QA Gym Dos"** (schema `gym_2`): OWNER `qa.owner2@test.com` / `qapass123`. No tiene otros datos.
- Las capturas citadas están en el scratchpad de la sesión: `C:\Users\luisd\AppData\Local\Temp\claude\C--Users-luisd-Desktop-Projects-gym-flow-frontend\9f298152-40e0-4bcc-ab6e-df2105e4155c\scratchpad\shots\`.

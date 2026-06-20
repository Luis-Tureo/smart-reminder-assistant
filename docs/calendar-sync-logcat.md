# Calendar sync Logcat

Usa estos tags para revisar solo sincronizacion de calendarios:

- `CalendarSync`
- `CalendarAutoSync`
- `GoogleCalendarSync`
- `MicrosoftCalendarSync`
- `CalendarAuth`
- `CalendarUi`
- `CalendarError`
- `CalendarQuota`

Filtro sugerido en Logcat:

`tag:CalendarAutoSync|tag:GoogleCalendarSync|tag:MicrosoftCalendarSync|tag:CalendarError|tag:CalendarAuth|tag:CalendarUi`

`CalendarQuota` muestra contadores locales, cooldown, reintentos, backoff y limites seguros. Los cursores incrementales, tokens y datos privados no se escriben en Logcat.

Microsoft Calendar usa solo permisos delegados estandar y endpoints de calendario no medidos. No se deben agregar recursos `Microsoft.GraphServices/accounts`, APIs con medicion, secretos de cliente ni recursos Azure de pago.

## Sincronizacion automatica

- WorkManager programa un trabajo unico cada 15 minutos, que es el minimo admitido para trabajo periodico y evita sondeo constante.
- El trabajo requiere red disponible. Android puede retrasarlo por bateria, Doze o politicas del sistema; no es tiempo real.
- Tambien se encola una ejecucion al abrir el proceso de la app. La pantalla mensual sincroniza al volver y las operaciones locales conservan su sincronizacion inmediata existente.
- Google y Microsoft se evalúan por separado. Un proveedor desconectado o pausado se omite antes de solicitar tokens o llamar APIs.
- Un cooldown persistente de 10 minutos evita duplicar la ejecucion al abrir varias pantallas. Los errores usan backoff exponencial desde 15 minutos hasta 6 horas.
- Microsoft respeta `Retry-After`; valores largos se difieren en vez de mantener un worker bloqueado.
- Google reutiliza `syncToken` y Microsoft reutiliza `deltaLink`. Solo se hace carga completa si falta el cursor, su ventana expiro o el servidor lo invalido.

## Flujo de Google Calendar

- `DISCONNECTED`: el boton muestra `Conectar con Google`; no se solicitan tokens al iniciar.
- `CONNECTING` y `SYNCING`: el boton conserva la accion estable; el estado interno no cambia la etiqueta.
- `CONNECTED`: la sincronizacion esta habilitada y el boton muestra `Desactivar Google`.
- `PAUSED`: la sesion se conserva, no se sincroniza y el boton muestra `Activar Google`.
- `ERROR`: el error aparece dentro de la tarjeta; la accion principal permanece fuera del recuadro y sigue siendo conectar, activar o desactivar segun la sesion conservada.

La conexion solo se guarda despues de validar la cuenta, el scope de Calendar y la obtencion del token. Desactivar cambia unicamente el estado local de sincronizacion: no cierra la sesion, no borra recordatorios y no elimina eventos de Google.

Codigos visibles: `GOOGLE_AUTH_CANCELLED`, `GOOGLE_AUTH_MISSING_GAIA_ID`, `GOOGLE_AUTH_BAD_AUTHENTICATION`, `GOOGLE_AUTH_INTERNAL_ERROR`, `GOOGLE_AUTH_NETWORK_IO`, `GOOGLE_AUTH_PLAY_SERVICES_UNAVAILABLE`, `GOOGLE_AUTH_SCOPE_DENIED`, `GOOGLE_CALENDAR_API_401`, `GOOGLE_CALENDAR_API_403`, `GOOGLE_CALENDAR_API_429`, `GOOGLE_CALENDAR_API_5XX` y `GOOGLE_SYNC_IO_EXCEPTION`.

Prueba manual minima:

1. Sin cuenta conectada, pulsar `Conectar Google`, autorizar Calendar y confirmar el cambio inmediato a conectado y la carga del mes visible.
2. Pulsar `Desactivar Google` y confirmar que los datos permanecen y el boton cambia a `Activar Google`.
3. Pulsar `Activar Google`: con sesion valida no debe abrir login; con sesion invalida debe abrirlo.
4. Cancelar el login y comprobar `GOOGLE_AUTH_CANCELLED`; repetir sin red y comprobar un codigo de red, no cancelacion.
5. Revisar Logcat con el filtro anterior y confirmar que no aparecen tokens, correos completos ni detalles de eventos.

## Flujo de Microsoft Calendar

- `DISCONNECTED`: muestra `Conectar con Microsoft`.
- `CONNECTED`: muestra `Desactivar Microsoft`.
- `PAUSED`: conserva la cuenta MSAL y muestra `Activar Microsoft`.
- `ERROR`: muestra un codigo `MICROSOFT_AUTH_*`, `MICROSOFT_GRAPH_*` o `MICROSOFT_SYNC_IO_EXCEPTION` dentro de la tarjeta.

Prueba manual minima:

1. Conectar Microsoft, aceptar `Calendars.ReadWrite` y confirmar que se importan eventos y reuniones de Teams del mes visible.
2. Abrir una reunion importada y comprobar que aparece `Abrir reuni&#243;n`.
3. Desactivar y activar Microsoft; con sesion valida no debe abrirse el login ni desaparecer ningun evento local o externo.
4. Usar `Desconectar de Microsoft`, confirmar el dialogo y comprobar que solo se elimina la sesion y metadata local del proveedor.
5. Probar sin red y con una sesion revocada; el recuadro debe mostrar el codigo exacto y no debe incluir `Reintentar`.

Errores de instalacion del APK, launcher o despliegue desde Android Studio no pertenecen al flujo de sincronizacion. Si aparecen junto a estos logs, revisa sus tags por separado antes de atribuirlos a Google Calendar o Microsoft Calendar.

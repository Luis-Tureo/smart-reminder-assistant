# Checklist de release para Firebase App Distribution

- [ ] Rama actual verificada como `release/firebase-app-distribution`.
- [ ] `versionCode` incrementado y `versionName` beta revisado.
- [ ] APK release firmada; no es debug ni unsigned.
- [ ] Misma clave release que la versión Firebase anterior.
- [ ] Firma verificada con `apksigner`.
- [ ] SHA-256 del archivo registrado.
- [ ] SHA-1 release registrado en la credencial Android de Google.
- [ ] Hash de firma release registrado en la plataforma Android de Microsoft Entra.
- [ ] Redirect MSAL release coincide con paquete y certificado.
- [ ] Inicio de sesión Google probado.
- [ ] Inicio de sesión Microsoft probado.
- [ ] Crear, editar, completar y eliminar recordatorios probado.
- [ ] Alarmas exactas y notificaciones probadas con permisos concedidos y denegados.
- [ ] Sincronización automática probada.
- [ ] Google Calendar probado en lectura, creación, actualización y eliminación.
- [ ] Microsoft Calendar probado en lectura, creación, actualización y eliminación.
- [ ] Botones de Teams y Google Meet probados con aplicación instalada y fallback al navegador.
- [ ] OCR/cámara probado.
- [ ] Creación desde texto pegado probada.
- [ ] Asistente y TTS local/remoto probado, incluido fallback.
- [ ] Operación sin conexión probada.
- [ ] Migración de Room probada conservando datos.
- [ ] Actualización instalada sobre una APK Firebase anterior.
- [ ] Logcat revisado: sin tokens, correos, contenido privado, URLs completas ni identificadores de cuenta.
- [ ] Inicio en frío y reinicio sin crash.
- [ ] `testDebugUnitTest` y `lintRelease` aprobados.
- [ ] Evaluador final instaló y abrió la APK distribuida.


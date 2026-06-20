# Distribución privada con Firebase App Distribution

## 1. Preparar la firma local

1. Crear una clave de release en un equipo seguro y conservarla respaldada fuera del repositorio. No reutilizar la clave de depuración.
2. Copiar `keystore.properties.example` como `keystore.properties` en la raíz.
3. Completar localmente `storeFile`, `storePassword`, `keyAlias` y `keyPassword`. Como alternativa, definir `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD` en el entorno.
4. No versionar el keystore ni sus credenciales. Verificar con `git status` antes de cada commit.
5. Ejecutar `gradlew.bat signingReport` y guardar los valores SHA-1 y SHA-256 de la variante release. También se pueden obtener con `keytool -list -v -keystore <ruta-del-keystore> -alias <alias>`.

La APK entregada directamente por Firebase se firma con esta clave local de release/upload. Google Play App Signing no interviene. Todas las actualizaciones deben conservar `com.luistureo.voicereminderapp` y exactamente la misma clave; cambiar la clave impide actualizar una instalación existente.

## 2. Configurar Google OAuth para release

1. Abrir Google Cloud Console y localizar la credencial OAuth de tipo Android usada por Google Calendar.
2. Registrar el paquete `com.luistureo.voicereminderapp` con el SHA-1 real del certificado release mostrado por `signingReport` o `keytool`.
3. Mantener la credencial de depuración existente; no reemplazar su SHA-1.
4. Si se usa una credencial Android separada para release, habilitar en el mismo proyecto las APIs y la pantalla de consentimiento que ya requiere la aplicación.
5. Instalar la APK firmada y validar inicio de sesión, consentimiento y lectura/escritura de calendario.

No inventar ni copiar el SHA-1 de depuración: Google valida la combinación exacta de paquete y certificado.

## 3. Configurar Microsoft Entra para release

1. Abrir Microsoft Entra admin center, ir a Registros de aplicaciones y seleccionar la aplicación pública existente.
2. En Autenticación, agregar una plataforma Android para `com.luistureo.voicereminderapp` con el hash de firma release real.
3. Obtener el hash desde el certificado release siguiendo la herramienta/documentación de MSAL para Android. El valor usado por Entra y por el redirect `msauth` debe provenir del mismo certificado; no inventarlo.
4. Mantener registrada la plataforma y redirect de depuración actuales.
5. Antes de compilar la entrega, reemplazar `RELEASE_SIGNATURE_HASH_NOT_CONFIGURED` en `app/src/release/res/raw/auth_config_single_account.json` por el hash release codificado para URL y en `app/src/release/AndroidManifest.xml` por el hash equivalente esperado en `android:path`. Conservar el `client_id` público y el redirect `msauth://com.luistureo.voicereminderapp/<hash-release-codificado>`.
6. No agregar un `client_secret`: Android usa un flujo de cliente público con permisos delegados `User.Read` y `Calendars.ReadWrite`.
7. Probar inicio de sesión interactivo, adquisición silenciosa de token y sincronización de Microsoft Calendar en la APK release.

La configuración principal de MSAL conserva el redirect de depuración y la variante release tiene archivos separados. La build firmada se bloquea mientras conserve el marcador, por lo que la preparación final de Microsoft es obligatoria antes de distribuir.

## 4. Crear o seleccionar la aplicación Firebase

1. Abrir Firebase Console y crear o seleccionar el proyecto correcto.
2. Agregar una aplicación Android con el paquete exacto `com.luistureo.voicereminderapp`.
3. Esta aplicación no usa actualmente el plugin Google Services ni SDK de Firebase en tiempo de ejecución. Para una carga manual en App Distribution, `google-services.json` no es necesario y no debe agregarse solo para distribuir la APK.
4. Si en el futuro se incorpora un producto Firebase que sí lo requiera, descargar el archivo desde Firebase, colocarlo en `app/google-services.json` y configurar el plugin oficial una sola vez. Revisarlo antes de cualquier commit; nunca subir claves de cuenta de servicio, tokens ni secretos. El archivo está ignorado preventivamente en este repositorio.

## 5. Generar y validar la APK

1. Ejecutar `gradlew.bat clean testDebugUnitTest lintRelease signingReport assembleRelease`.
2. Confirmar que la variante release no es depurable y que `signingReport` indica la clave release prevista, nunca `debug.keystore`.
3. Verificar la firma con `apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk`.
4. Calcular el SHA-256 del archivo y conservarlo junto con las notas de entrega.
5. No distribuir un archivo `app-release-unsigned.apk` ni renombrarlo para aparentar que está firmado.

## 6. Cargar manualmente en App Distribution

1. En Firebase Console, abrir App Distribution para la aplicación Android.
2. Seleccionar una nueva versión y cargar la APK release firmada.
3. Agregar los correos de los evaluadores o grupos autorizados sin guardarlos en el repositorio.
4. Copiar las notas verificadas desde `docs/firebase-release-notes-es.txt` y ajustarlas si cambió el alcance probado.
5. Revisar versión, artefacto y destinatarios; luego enviar las invitaciones.
6. El evaluador debe abrir el correo o enlace de Firebase, aceptar la invitación e instalar la aplicación para testers si Firebase lo solicita.
7. Android puede pedir autorización para “Instalar apps desconocidas” al navegador o instalador. Concederla solo a la fuente usada y revocarla después. Atender las advertencias y comprobaciones de Play Protect; no intentar eludirlas.

## 7. Publicar una actualización posterior

1. Incrementar `versionCode` sin reducirlo y actualizar `versionName`.
2. Compilar con la misma aplicación, application ID y keystore release.
3. Repetir pruebas, verificación de firma y checksum.
4. Cargar la nueva APK en App Distribution y seleccionar los evaluadores correspondientes.
5. Instalar sobre la versión anterior para validar la actualización y la migración de Room.

## Solución de problemas

- Instalación bloqueada: confirmar que la APK está firmada, que el dispositivo permite temporalmente la fuente y que Play Protect no reporta una firma o archivo alterado.
- “App no instalada” al actualizar: comparar certificado, application ID y `versionCode`. Desinstalar borra datos y no corrige una estrategia de firma inconsistente.
- Google no inicia sesión: comprobar paquete, SHA-1 release, API habilitada, cuenta evaluadora y pantalla de consentimiento. Los cambios de consola pueden tardar en propagarse.
- Microsoft no inicia sesión: comprobar plataforma Android, hash release, redirect de MSAL y filtro `msauth`; los cuatro deben coincidir. No sustituirlos por los valores debug.
- Invitación ausente: revisar carpeta de spam, correo exacto, grupo asignado y que la versión haya sido enviada, no solo cargada.
- APK no actualiza la anterior: verificar que se usó la misma clave release y un `versionCode` mayor.

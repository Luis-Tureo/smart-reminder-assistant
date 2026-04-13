# iosApp

Host iOS minimo para validar en Mac el flujo manual-local de recordatorios sobre `:shared`.

## Requisitos

- macOS con Xcode instalado
- Xcode Command Line Tools habilitadas
- XcodeGen instalado

## Generar el proyecto Xcode

1. Abre Terminal en macOS.
2. Ve a `iosApp`.
3. Ejecuta `sh ./generate_xcode_project.sh`.
4. Abre `iosApp.xcodeproj` en Xcode.

## Validar build en Mac

1. Ejecuta `sh ./validate_on_mac.sh` para generar el proyecto y compilar contra iOS Simulator.
2. En Xcode, selecciona el esquema `iosApp`.
3. Ejecuta la app en iPhone Simulator.
4. Si se valida en iPhone fisico, selecciona un team de firma antes de correr.

## Flujo funcional esperado

- Ver estado vacio al abrir la app por primera vez.
- Crear un recordatorio manual.
- Ver el recordatorio en la lista.
- Marcarlo como completado.
- Eliminarlo desde swipe action.

## Notas

- El framework `sharedKit` se prepara desde Xcode mediante `:shared:embedAndSignAppleFrameworkForXcode`.
- El flujo iOS actual es deliberadamente minimo y local; no incluye notificaciones, OCR, voz ni calendario.

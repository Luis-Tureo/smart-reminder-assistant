# TTS backend

Backend seguro para sintetizar la voz del asistente con Google Cloud Text-to-Speech sin incluir credenciales en Android.

## Endpoint

`POST /tts`

Entrada:

```json
{
  "text": "Perfecto, tu recordatorio quedo guardado.",
  "languageCode": "es-ES",
  "voice": "opcional"
}
```

`text` es obligatorio. `languageCode` y `voice` son opcionales; si no vienen, se usan las variables de entorno.

Respuesta por defecto:

- `200 OK`
- `Content-Type: audio/mpeg`
- cuerpo binario MP3 reproducible por el cliente Android.

Respuesta alternativa si `TTS_RESPONSE_MODE=json`:

```json
{
  "audioBase64": "...",
  "mimeType": "audio/mpeg"
}
```

Errores:

- `400 empty_text`: texto vacio.
- `400 invalid_json`: JSON invalido.
- `413 text_too_long`: texto sobre el limite configurado.
- `502 tts_failure`: fallo al llamar a Google Cloud Text-to-Speech.

## Variables de entorno

- `PORT`: puerto HTTP. Cloud Run lo configura automaticamente.
- `TTS_MAX_TEXT_LENGTH`: largo maximo del texto. Default: `500`.
- `TTS_DEFAULT_LANGUAGE_CODE`: idioma por defecto. Default: `es-ES`.
- `TTS_DEFAULT_VOICE_NAME`: voz opcional. Default: vacio para que Google seleccione una compatible.
- `TTS_SPEAKING_RATE`: velocidad. Default: `1.0`.
- `TTS_PITCH`: tono. Default: `0.0`.
- `TTS_AUDIO_ENCODING`: formato de Google TTS. Default: `MP3`.
- `TTS_RESPONSE_MODE`: `audio` o `json`. Default: `audio`.
- `TTS_ALLOWED_ORIGINS`: origenes CORS separados por coma. Dejar vacio para apps Android nativas.

Para una voz espanola mas natural, revisa las voces disponibles del proyecto y define `TTS_DEFAULT_VOICE_NAME`, por ejemplo una voz neural disponible para `es-ES` o `es-US`. No se fija una voz premium por defecto para evitar asumir disponibilidad o costos.

## Seguridad

- No subas JSON de cuenta de servicio ni API keys.
- En Cloud Run usa Application Default Credentials mediante la cuenta de servicio del servicio.
- El backend solo recibe el texto del asistente y no lo escribe en logs.
- Los logs incluyen metadatos minimos: cantidad de caracteres e idioma.
- Configura autenticacion o restriccion de acceso si el endpoint sera publico.

## Despliegue en Cloud Run

1. Habilita la API de Cloud Text-to-Speech:
   `gcloud services enable texttospeech.googleapis.com run.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com`

2. Crea o elige una cuenta de servicio para Cloud Run:
   `gcloud iam service-accounts create reminder-tts-runner`

3. Asigna permisos al servicio. Para Text-to-Speech normalmente basta con ejecutar el cliente con credenciales del proyecto; si tu organizacion exige IAM especifico, concede el rol minimo disponible para usar Cloud Text-to-Speech.

4. Crea un repositorio Docker en Artifact Registry:
   `gcloud artifacts repositories create reminder-backends --repository-format=docker --location=REGION`

5. Construye y publica la imagen desde la raiz del repositorio:
   `gcloud builds submit --config tts-backend/cloudbuild.yaml --substitutions _IMAGE=REGION-docker.pkg.dev/PROJECT_ID/reminder-backends/tts-backend:latest .`

6. Despliega en Cloud Run:
   `gcloud run deploy reminder-tts-backend --image REGION-docker.pkg.dev/PROJECT_ID/reminder-backends/tts-backend:latest --service-account reminder-tts-runner@PROJECT_ID.iam.gserviceaccount.com --set-env-vars TTS_DEFAULT_LANGUAGE_CODE=es-ES,TTS_AUDIO_ENCODING=MP3,TTS_RESPONSE_MODE=audio --region REGION`

7. Copia la URL de Cloud Run y configurala en Android como URL del backend remoto TTS.

8. Prueba el endpoint con un `POST /tts` y confirma que responde `audio/mpeg`.

## Desarrollo local

Ejecutar pruebas:

`./gradlew :tts-backend:test`

Ejecutar local con credenciales ADC configuradas:

`./gradlew :tts-backend:run`

# Mundus — Entrega 1

Demo para extraer guías del SEA y consultar su contenido con Qwen local.
El asistente se usa desde **Jupyter, por bloques**, en
[`Asistente/notebooks/01_qwen_local.ipynb`](Asistente/notebooks/01_qwen_local.ipynb).
El frontend conserva tres pantallas: Consulta de proyecto, Biblioteca y Procesamiento.

El recorrido es: Excel del SEA → PDF → texto por página → fragmentos JSON
→ búsqueda con FAISS → Qwen → preinforme con citas y páginas.

| Carpeta | Contenido |
| --- | --- |
| `Asistente` | Notebook, Qwen con LangChain y puente para el frontend |
| `Frontend` | React, TypeScript y Vite |
| `Backend` | Java 17 y Spring Boot: extracción, almacenamiento y API |
| `Preparador` | Embeddings y búsqueda de fragmentos |

## Abrir el asistente en Jupyter

Necesitas Ollama iniciado con `qwen2.5:7b` instalado. Puedes comprobarlo con
`ollama list`; si falta el modelo, se prepara una vez con `ollama pull qwen2.5:7b`.

1. Abre `Asistente/notebooks/01_qwen_local.ipynb` en VS Code.
2. En **Seleccionar kernel**, elige el Python del entorno del asistente.
   En este equipo puedes reutilizar el de
   `../Git Tavi/GPT/.venv-asistente/Scripts/python.exe`.
3. Ejecuta las celdas de arriba hacia abajo con ▶: configuración, comprobación
   de Ollama, modelo, prompt, cadena y pregunta. Cambia `pregunta` para probar
   otra consulta; la respuesta aparece debajo de la celda.
4. Ejecuta también la celda **Conectar con el frontend**. Inicia el puente en
   el puerto **8002**. La celda no bloquea el resto del cuaderno.

Si prefieres crear un entorno propio, desde esta carpeta y con Python 3.12:

```powershell
py -3.12 -m venv .venv-asistente
.\.venv-asistente\Scripts\python.exe -m pip install -r Asistente/notebooks/requirements.txt
```

Después selecciona `.venv-asistente/Scripts/python.exe` como kernel. Para cerrar
el puente usa la última celda del cuaderno. La pregunta libre del notebook es
una prueba del modelo; el formulario recupera las guías y devuelve sus fuentes.

## Levantar el frontend y la extracción

Necesitas Docker Desktop con contenedores Linux. Ejecuta estos pasos desde
esta carpeta:

1. Copia la configuración y cambia `DB_PASSWORD`:

   ```powershell
   Copy-Item .env.example .env
   notepad .env
   ```

2. En `VECTOR_CACHE_HOST`, indica la caché del modelo de embeddings
   `intfloat/multilingual-e5-base`. Usa `/` en la ruta. En este equipo puedes
   reutilizar `../Git Tavi/GPT/output/modelos/huggingface`.
   Si todavía no tienes esa caché, con Python 3.12:

   ```powershell
   py -3.12 -m venv .venv-modelo
   .\.venv-modelo\Scripts\python.exe -m pip install huggingface-hub
   .\.venv-modelo\Scripts\python.exe Preparador/descargar_modelo.py --confirmar-descarga
   ```

3. Inicia los tres servicios: PostgreSQL, backend y frontend.

   ```powershell
   docker compose up --build -d --wait
   ```

4. Abre **http://localhost:8082/app/**. La primera vez, pulsa **Procesar guías
   del SEA** y espera a que termine. Después completa **Consulta de proyecto**.
   Para recibir una respuesta, el puente iniciado desde el notebook debe seguir
   activo. La biblioteca y el procesamiento funcionan sin ese puente.

El puerto del puente es `8002`. Si lo cambias, usa el mismo valor en
`ASISTENTE_PUERTO` del `.env` principal y de `Asistente/.env` (opcional,
creado desde `Asistente/.env.example`).

Para revisar el backend usa `docker compose logs backend`. Los mensajes del
puente se guardan en `output/asistente/jupyter.log`. Para detener los servicios
usa `docker compose down`; los datos se conservan.

La base y el volumen pertenecen a `mundus-entrega1`. El catálogo comienza vacío;
su descarga y los PDF pueden tardar. Los modelos y datos generados no forman
parte de la entrega. El preinforme es una demostración documental y permite
revisar sus fuentes; no determina qué permisos aplican.

# Asistente Qwen por bloques

1. Abre `01_qwen_local.ipynb` en VS Code.
2. Selecciona el kernel `.venv-asistente/Scripts/python.exe`. En este equipo
   también puedes elegir el del proyecto original `Git Tavi/GPT`.
3. Mantén Ollama iniciado con `qwen2.5:7b`. Ejecuta las celdas de configuración,
   comprobación, modelo, prompt y cadena en orden.
4. Cambia `pregunta` y ejecuta su bloque para ver la respuesta y las métricas.
5. Ejecuta **Conectar con el frontend**. Deja activo el puente en el puerto
   `8002` y usa **Consulta de proyecto** en `http://localhost:8082/app/`.
6. Para terminar descomenta y ejecuta `detener_asistente()` en la última celda,
   **antes de cerrar o reiniciar el kernel**, y detén Docker con
   `docker compose down` cuando hayas acabado con el frontend.

El notebook muestra la llamada a Qwen paso a paso. El formulario añade la
recuperación de guías, las citas y las validaciones del asistente.

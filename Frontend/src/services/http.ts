export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
export async function api<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers: { Accept: 'application/json', ...options?.headers },
    });
  } catch (error) {
    if (options?.signal?.aborted) throw error;
    throw new Error(
      'No se pudo conectar con el servidor. Comprueba que los servicios estén iniciados.',
    );
  }
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = (await response.json()) as { detail?: string; message?: string; error?: string };
      message += `: ${body.detail || body.message || body.error || 'Error del servidor'}`;
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

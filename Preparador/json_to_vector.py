"""Convierte JSON de guías a FAISS y recupera fragmentos con sus páginas."""

import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import re
import sys
from uuid import uuid4


MODELO_PREDETERMINADO = "intfloat/multilingual-e5-base"
ESQUEMA_INDICE = "mundus-guias-faiss-v1"


def sha256_bytes(data):
    return hashlib.sha256(data).hexdigest()


def sha256_archivo(path):
    with Path(path).open("rb") as archivo:
        return hashlib.file_digest(archivo, "sha256").hexdigest()


def escribir_json(path, contenido):
    with Path(path).open("w", encoding="utf-8", newline="\n") as archivo:
        json.dump(contenido, archivo, ensure_ascii=False, sort_keys=True)
        archivo.write("\n")
        archivo.flush()
        os.fsync(archivo.fileno())


def entero_positivo(value, nombre):
    if type(value) is not int or value < 1:
        raise ValueError(f"{nombre} debe ser un entero positivo")
    return value


def comprobar_hash(value, nombre):
    if not isinstance(value, str) or not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError(f"{nombre} no es un SHA256 válido")
    return value


def texto_obligatorio(value, nombre):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Falta {nombre}")
    return value


def leer_fragmentos(entrada):
    archivos = sorted(Path(entrada).glob("*.json"))
    if not archivos:
        raise ValueError("La carpeta de entrada no contiene JSON de guías")
    fragmentos, fuentes, excluidos = [], [], []
    ids, documentos_ids = set(), set()
    criterios_omitidos = 0
    for archivo in archivos:
        datos = json.loads(archivo.read_text(encoding="utf-8"))
        if not isinstance(datos, dict) or datos.get("schema_version") != "mundus-rag-json-v1":
            raise ValueError(f"{archivo.name}: se esperaba mundus-rag-json-v1")
        documento = datos.get("documento", {})
        if documento.get("tipo") == "CRITERIO":
            criterios_omitidos += 1
            continue
        if documento.get("tipo") != "GUIA":
            raise ValueError(f"{archivo.name}: solo se admiten documentos GUIA")
        entero_positivo(documento.get("id"), "documento.id")
        entero_positivo(documento.get("version_id"), "documento.version_id")
        comprobar_hash(documento.get("sha256_pdf"), "documento.sha256_pdf")
        texto_obligatorio(documento.get("nombre"), "documento.nombre")
        texto_obligatorio(documento.get("url_pdf"), "documento.url_pdf")
        identidad = (documento["tipo"], documento["id"])
        if identidad in documentos_ids:
            raise ValueError("Hay dos JSON para la misma guía; exporta solo su versión actual")
        documentos_ids.add(identidad)
        paginas = {}
        if not isinstance(datos.get("paginas"), list) or not isinstance(datos.get("fragmentos"), list):
            raise ValueError(f"{archivo.name}: faltan páginas o fragmentos")
        for pagina in datos["paginas"]:
            numero = entero_positivo(pagina.get("pagina_pdf"), "pagina_pdf")
            texto = pagina.get("texto_limpio")
            if numero in paginas or not isinstance(texto, str):
                raise ValueError(f"{archivo.name}: página duplicada o texto inválido")
            if "texto_original" in pagina:
                original = pagina["texto_original"]
                if not isinstance(original, str) or sha256_bytes(original.encode("utf-8")) != pagina.get("sha256_texto_original"):
                    raise ValueError(f"{archivo.name}: hash del texto original inconsistente")
            paginas[numero] = texto
        fuentes.append({"archivo": archivo.name, "sha256_json": sha256_archivo(archivo), "documento": documento})
        for fragmento in datos["fragmentos"]:
            ident = texto_obligatorio(fragmento.get("id_fragmento"), "id_fragmento")
            if ident in ids:
                raise ValueError(f"Fragmento duplicado: {ident}")
            ids.add(ident)
            texto = fragmento.get("texto")
            if not isinstance(texto, str):
                raise ValueError(f"{ident}: texto inválido")
            if not texto.strip():
                excluidos.append({"id_fragmento": ident, "motivo": "SIN_TEXTO"})
                continue
            numero = entero_positivo(fragmento.get("pagina_inicio"), "pagina_inicio")
            if numero != fragmento.get("pagina_fin") or numero not in paginas:
                raise ValueError(f"{ident}: fragmento sin página de origen única")
            inicio, fin = fragmento.get("offset_inicio"), fragmento.get("offset_fin")
            if type(inicio) is not int or type(fin) is not int or inicio < 0 or fin <= inicio:
                raise ValueError(f"{ident}: offsets inválidos")
            pagina_utf16 = paginas[numero].encode("utf-16-le")
            if fin * 2 > len(pagina_utf16) or pagina_utf16[inicio * 2:fin * 2].decode("utf-16-le") != texto:
                raise ValueError(f"{ident}: el fragmento no coincide con su página y offsets UTF-16")
            if sha256_bytes(texto.encode("utf-8")) != fragmento.get("sha256_texto"):
                raise ValueError(f"{ident}: hash de texto inconsistente")
            alertas = fragmento.get("alertas_pagina", [])
            if not isinstance(alertas, list) or any(not isinstance(a, str) for a in alertas):
                raise ValueError(f"{ident}: alertas inválidas")
            if "\ufffd" in texto or any(a in {"CARACTER_REEMPLAZO", "POSIBLE_CODIFICACION"} for a in alertas):
                excluidos.append({"id_fragmento": ident, "motivo": "CODIFICACION_REQUIERE_REVISION"})
                continue
            fragmentos.append({
                "id_fragmento": ident, "texto": texto, "documento": dict(documento),
                "pagina_pdf": numero, "pagina_inicio": numero, "pagina_fin": numero,
                "offset_inicio": inicio, "offset_fin": fin,
                "offset_unidad": "UTF-16", "sha256_texto": fragmento["sha256_texto"],
                "alertas": alertas, "validado_para_llm": False,
            })
    if not fragmentos:
        raise ValueError("No hay fragmentos de guías utilizables para vectorizar")
    return fragmentos, fuentes, excluidos, criterios_omitidos


@contextlib.contextmanager
def modo_huggingface(permitir_descarga):
    # Bloquea también comprobaciones de metadatos cuando el backend opera sin red.
    valores = {n: os.environ.get(n) for n in ("HF_HUB_OFFLINE", "TRANSFORMERS_OFFLINE")}
    try:
        for nombre in valores:
            os.environ[nombre] = "0" if permitir_descarga else "1"
        yield
    finally:
        for nombre, valor in valores.items():
            if valor is None:
                os.environ.pop(nombre, None)
            else:
                os.environ[nombre] = valor


def cargar_modelo(nombre, permitir_descarga=False):
    try:
        with modo_huggingface(permitir_descarga), contextlib.redirect_stdout(sys.stderr):
            from sentence_transformers import SentenceTransformer
            return SentenceTransformer(nombre, local_files_only=not permitir_descarga,
                trust_remote_code=False, model_kwargs={"use_safetensors": True})
    except ImportError as error:
        raise RuntimeError("Instala Preparador/requirements-vector.txt en el Python configurado") from error


def importar_faiss():
    try:
        import faiss
        return faiss
    except ImportError as error:
        raise RuntimeError("Falta faiss-cpu; instala Preparador/requirements-vector.txt") from error


def convencion_modelo(nombre):
    # E5 requiere estos prefijos incluso para documentos en español.
    return {"pasaje": "passage: ", "consulta": "query: "} if "e5" in nombre.lower() else {"pasaje": "", "consulta": ""}


def codificar(modelo, textos):
    import numpy as np
    with contextlib.redirect_stdout(sys.stderr):
        vectores = modelo.encode(textos, normalize_embeddings=True, show_progress_bar=False, convert_to_numpy=True)
    vectores = np.ascontiguousarray(vectores, dtype="float32")
    if vectores.ndim != 2 or vectores.shape[0] != len(textos) or vectores.shape[1] < 1 or not np.isfinite(vectores).all():
        raise ValueError("El modelo produjo vectores inválidos")
    normas = np.linalg.norm(vectores, axis=1)
    if np.any(normas <= 0):
        raise ValueError("El modelo produjo un vector vacío")
    vectores /= normas[:, None]
    return vectores


def construir(entrada, salida, modelo=MODELO_PREDETERMINADO, permitir_descarga=False,
              _modelo=None, _faiss=None):
    fragmentos, fuentes, excluidos, criterios_omitidos = leer_fragmentos(entrada)
    faiss = _faiss or importar_faiss()
    encoder = _modelo if _modelo is not None else cargar_modelo(modelo, permitir_descarga)
    prefijos = convencion_modelo(modelo)
    vectores = codificar(encoder, [prefijos["pasaje"] + f["texto"] for f in fragmentos])
    con_vectores = {f["documento"]["id"] for f in fragmentos}
    sin_fragmentos = [f["documento"] for f in fuentes if f["documento"]["id"] not in con_vectores]
    indice = faiss.IndexFlatIP(vectores.shape[1])
    indice.add(vectores)
    salida = Path(salida).resolve()
    salida.mkdir(parents=True, exist_ok=True)
    generacion = uuid4().hex
    temporal = salida / (".construyendo-" + generacion)
    temporal.mkdir()
    faiss.write_index(indice, str(temporal / "indice.faiss"))
    with (temporal / "fragmentos.jsonl").open("w", encoding="utf-8", newline="\n") as archivo:
        for fragmento in fragmentos:
            archivo.write(json.dumps(fragmento, ensure_ascii=False, sort_keys=True) + "\n")
        archivo.flush()
        os.fsync(archivo.fileno())
    manifest = {
        "schema_version": ESQUEMA_INDICE, "generacion": generacion,
        "modelo": modelo, "prefijos": prefijos, "normalizado": True,
        "metrica": "producto_interno_vectores_unitarios", "dimension": int(vectores.shape[1]),
        "fragmentos": len(fragmentos), "documentos": len(fuentes), "fuentes": fuentes,
        "documentos_con_vectores": len(con_vectores),
        "documentos_sin_fragmentos": len(sin_fragmentos), "guias_sin_fragmentos": sin_fragmentos,
        "excluidos": excluidos, "criterios_omitidos": criterios_omitidos,
        "validado_para_llm": False, "archivos": {
            nombre: sha256_archivo(temporal / nombre) for nombre in ("indice.faiss", "fragmentos.jsonl")
        },
    }
    escribir_json(temporal / "manifest.json", manifest)
    # Índice y textos se publican juntos: CURRENT cambia solo al terminar.
    destino = salida / generacion
    os.replace(temporal, destino)
    puntero = salida / (".CURRENT-" + generacion)
    with puntero.open("w", encoding="ascii", newline="\n") as archivo:
        archivo.write(generacion + "\n")
        archivo.flush()
        os.fsync(archivo.fileno())
    os.replace(puntero, salida / "CURRENT")
    return {"estado": "COMPLETADO", "generacion": generacion, "modelo": modelo,
            "dimension": manifest["dimension"], "fragmentos": len(fragmentos),
            "documentos": len(fuentes), "excluidos": len(excluidos),
            "documentos_con_vectores": len(con_vectores), "documentos_sin_fragmentos": len(sin_fragmentos),
            "criterios_omitidos": criterios_omitidos, "ruta": str(destino),
            "validado_para_llm": False}


def abrir_generacion(salida, modelo_esperado=None, _faiss=None):
    salida = Path(salida).resolve()
    puntero = salida / "CURRENT"
    if not puntero.is_file():
        raise FileNotFoundError("Todavía no existe un índice de guías; ejecuta construir")
    generacion = puntero.read_text(encoding="ascii").strip()
    if not re.fullmatch(r"[0-9a-f]{32}", generacion):
        raise ValueError("El puntero CURRENT no contiene una generación válida")
    carpeta = salida / generacion
    manifest = json.loads((carpeta / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schema_version") != ESQUEMA_INDICE or manifest.get("generacion") != generacion:
        raise ValueError("Manifest de índice incompatible")
    modelo = texto_obligatorio(manifest.get("modelo"), "modelo del índice")
    if modelo_esperado is not None and modelo_esperado != modelo:
        raise ValueError("El modelo solicitado no coincide con el índice; reconstruye los vectores")
    if manifest.get("prefijos") != convencion_modelo(modelo) or manifest.get("normalizado") is not True:
        raise ValueError("Convención de embeddings incompatible")
    if manifest.get("metrica") != "producto_interno_vectores_unitarios":
        raise ValueError("Métrica del índice incompatible")
    dimension = entero_positivo(manifest.get("dimension"), "dimension")
    cantidad = entero_positivo(manifest.get("fragmentos"), "fragmentos")
    hashes = manifest.get("archivos", {})
    for nombre in ("indice.faiss", "fragmentos.jsonl"):
        esperado = comprobar_hash(hashes.get(nombre), "hash de " + nombre)
        if sha256_archivo(carpeta / nombre) != esperado:
            raise ValueError(f"El archivo {nombre} fue modificado o está incompleto")
    textos, ids = [], set()
    with (carpeta / "fragmentos.jsonl").open(encoding="utf-8") as archivo:
        for linea in archivo:
            dato = json.loads(linea)
            ident = texto_obligatorio(dato.get("id_fragmento"), "id_fragmento")
            if ident in ids or dato.get("documento", {}).get("tipo") != "GUIA":
                raise ValueError("Metadatos duplicados o ajenos a guías")
            ids.add(ident)
            texto = texto_obligatorio(dato.get("texto"), "texto de fragmento")
            if sha256_bytes(texto.encode("utf-8")) != dato.get("sha256_texto"):
                raise ValueError("Hash de fragmento almacenado inconsistente")
            textos.append(dato)
    faiss = _faiss or importar_faiss()
    indice = faiss.read_index(str(carpeta / "indice.faiss"))
    if len(textos) != cantidad or indice.ntotal != cantidad or indice.d != dimension:
        raise ValueError("El índice y los textos no contienen los mismos vectores/dimensiones")
    if hasattr(faiss, "METRIC_INNER_PRODUCT") and indice.metric_type != faiss.METRIC_INNER_PRODUCT:
        raise ValueError("El archivo FAISS no usa la métrica declarada")
    return manifest, indice, textos


def estado(salida, _faiss=None):
    if not (Path(salida) / "CURRENT").is_file():
        return {"estado": "SIN_INDICE", "disponible": False}
    manifest, _, _ = abrir_generacion(salida, _faiss=_faiss)
    return {"estado": "LISTO", "disponible": True, "generacion": manifest["generacion"],
            "modelo": manifest["modelo"], "dimension": manifest["dimension"],
            "fragmentos": manifest["fragmentos"], "documentos": manifest["documentos"],
            "documentos_con_vectores": manifest.get("documentos_con_vectores"),
            "documentos_sin_fragmentos": manifest.get("documentos_sin_fragmentos"),
            "excluidos": len(manifest["excluidos"]), "validado_para_llm": False}


def buscar(salida, consulta, top_k=3, modelo_esperado=None, _modelo=None, _faiss=None):
    entero_positivo(top_k, "top_k")
    texto_obligatorio(consulta, "consulta")
    if len(consulta) > 2000:
        raise ValueError("La consulta no puede superar 2000 caracteres")
    manifest, indice, textos = abrir_generacion(salida, modelo_esperado, _faiss)
    encoder = _modelo if _modelo is not None else cargar_modelo(manifest["modelo"])
    vector = codificar(encoder, [manifest["prefijos"]["consulta"] + consulta.strip()])
    if vector.shape[1] != indice.d:
        raise ValueError("La dimensión del modelo no coincide con el índice")
    distancias, indices = indice.search(vector, min(top_k, len(textos)))
    resultados = []
    for puntaje, posicion in zip(distancias[0], indices[0]):
        if not 0 <= int(posicion) < len(textos):
            raise ValueError("FAISS devolvió una posición sin texto asociado")
        resultados.append({**textos[int(posicion)], "puntuacion": float(puntaje)})
    return {"estado": "OK", "generacion": manifest["generacion"], "modelo": manifest["modelo"],
            "consulta": consulta, "resultados": resultados, "llm_utilizada": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    comandos = parser.add_subparsers(dest="comando", required=True)
    crear = comandos.add_parser("construir", help="JSON de guías a índice FAISS versionado")
    crear.add_argument("--entrada", required=True)
    crear.add_argument("--salida", required=True)
    crear.add_argument("--modelo", default=MODELO_PREDETERMINADO)
    crear.add_argument("--permitir-descarga", action="store_true", help="Solo tras autorizar la descarga del modelo")
    consulta = comandos.add_parser("buscar", help="Recuperar fragmentos, sin generar respuestas")
    consulta.add_argument("--salida", required=True)
    consulta.add_argument("--consulta", required=True)
    consulta.add_argument("--top-k", type=int, default=3)
    consulta.add_argument("--modelo", help="Comprobación opcional contra el modelo almacenado")
    estado_parser = comandos.add_parser("estado", help="Comprobar índice sin cargar el modelo")
    estado_parser.add_argument("--salida", required=True)
    args = parser.parse_args(argv)
    try:
        if args.comando == "construir":
            resultado = construir(args.entrada, args.salida, args.modelo, args.permitir_descarga)
        elif args.comando == "buscar":
            resultado = buscar(args.salida, args.consulta, args.top_k, args.modelo)
        else:
            resultado = estado(args.salida)
        print(json.dumps(resultado, ensure_ascii=False))
        return 0
    except Exception as error:
        print(json.dumps({"estado": "ERROR", "error": str(error), "tipo": type(error).__name__}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())

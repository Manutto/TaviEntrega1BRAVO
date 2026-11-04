"""Prepara el modelo de embeddings en la caché indicada."""

import argparse
import contextlib
import json
import os
from pathlib import Path
import sys

from json_to_vector import escribir_json, sha256_archivo


MODELO = "intfloat/multilingual-e5-base"
REVISION = "d128750597153bb5987e10b1c3493a34e5a4502a"
ARCHIVOS = [
    "model.safetensors", "config.json", "modules.json",
    "sentence_bert_config.json", "1_Pooling/config.json",
    "sentencepiece.bpe.model", "tokenizer.json",
    "special_tokens_map.json", "tokenizer_config.json",
]


def descargar(cache, confirmar_descarga=False):
    if not confirmar_descarga:
        raise ValueError("La descarga requiere --confirmar-descarga y autorización previa del usuario")
    cache = Path(cache).resolve()
    cache.mkdir(parents=True, exist_ok=True)
    # Se configura ANTES del import porque huggingface_hub lee sus variables al
    # cargar. Los pesos se conservan dentro del proyecto, no en la caché global.
    os.environ["HF_HUB_CACHE"] = str(cache)
    os.environ["HF_HOME"] = str(cache.parent)
    os.environ["HF_HUB_OFFLINE"] = "0"
    os.environ["TRANSFORMERS_OFFLINE"] = "0"
    os.environ["HF_HUB_DISABLE_XET"] = "1"
    from huggingface_hub import snapshot_download
    with contextlib.redirect_stdout(sys.stderr):
        snapshot = Path(snapshot_download(
            MODELO, revision=REVISION, cache_dir=str(cache),
            allow_patterns=ARCHIVOS, max_workers=3,
        ))
    auditoria = {"modelo": MODELO, "revision": REVISION, "snapshot": str(snapshot), "archivos": []}
    for nombre in ARCHIVOS:
        path = snapshot / nombre
        auditoria["archivos"].append({
            "nombre": nombre, "bytes": path.stat().st_size,
            "sha256": sha256_archivo(path),
        })
    # El alias main local apunta a la revisión fijada, permitiendo que el nombre
    # del modelo funcione con local_files_only=True sin consultar Hugging Face.
    refs = snapshot.parent.parent / "refs"
    refs.mkdir(exist_ok=True)
    (refs / "main").write_text(REVISION, encoding="ascii")
    escribir_json(cache / "modelo-e5-manifest.json", auditoria)
    return {"estado": "DESCARGADO", "modelo": MODELO, "revision": REVISION,
            "cache": str(cache), "snapshot": str(snapshot), "archivos": len(ARCHIVOS)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--confirmar-descarga", action="store_true")
    parser.add_argument("--cache", default="output/modelos/huggingface")
    args = parser.parse_args(argv)
    try:
        print(json.dumps(descargar(args.cache, args.confirmar_descarga), ensure_ascii=False))
        return 0
    except Exception as error:
        print(json.dumps({"estado": "ERROR", "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())

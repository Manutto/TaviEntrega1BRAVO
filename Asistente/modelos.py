"""Datos de entrada y salida del asistente."""

from typing import List, Optional, Literal
from pydantic import BaseModel, Field, ConfigDict


class FragmentoRecuperado(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    fragmentoId: int = Field(
        ...,
        description="Identificador del fragmento dentro de esta consulta, usado en las citas"
    )
    documentoTipo: str = Field(
        ...,
        description="Tipo de documento: GUIA"
    )
    documentoId: int = Field(
        ...,
        description="ID del documento al que pertenece el fragmento"
    )
    titulo: str = Field(
        ...,
        description="Título de la guía o documento oficial del SEA"
    )
    pagina: int = Field(
        ...,
        description="Número de página de origen dentro del documento oficial"
    )
    texto: str = Field(
        ...,
        description="Contenido textual del fragmento recuperado"
    )
    similitud: Optional[float] = Field(
        default=None,
        description="Similitud coseno del fragmento con la consulta"
    )


class PermisoPosible(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    pas: str = Field(
        ...,
        description="Identificador del Permiso Ambiental Sectorial, ej: 'PAS 140' o 'PAS 142'"
    )
    motivo: str = Field(
        ...,
        description="Explicación técnica y fundamentada de por qué aplica al proyecto analizado"
    )
    citas: List[int] = Field(
        default_factory=list,
        description="Lista de fragmentoId que respaldan directamente este permiso"
    )


class Alerta(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    nivel: Literal["ALTO", "MEDIO", "BAJO"] = Field(
        ...,
        description="Nivel de severidad o criticidad de la alerta ambiental"
    )
    descripcion: str = Field(
        ...,
        description="Detalle técnico del riesgo, impacto o exigencia detectada"
    )
    citas: List[int] = Field(
        default_factory=list,
        description="Lista de fragmentoId que evidencian la alerta"
    )


class MetadatosPreinforme(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    modelo: str = Field(
        default="",
        description="Modelo local utilizado, por ejemplo qwen2.5:7b"
    )
    usoRespaldo: bool = Field(
        default=False,
        description="False en esta demo, que utiliza directamente Ollama local"
    )
    tokensEntrada: int = Field(
        default=0,
        description="Número de tokens consumidos en el prompt de entrada"
    )
    tokensSalida: int = Field(
        default=0,
        description="Número de tokens generados en la respuesta"
    )
    latenciaMs: int = Field(
        default=0,
        description="Tiempo de inferencia y procesamiento en milisegundos"
    )


class PreInforme(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    resumen: str = Field(
        ...,
        description="Síntesis ejecutiva y técnica de la evaluación de factibilidad ambiental"
    )
    permisosPosibles: List[PermisoPosible] = Field(
        default_factory=list,
        description="Permisos Ambientales Sectoriales (PAS) detectados con sus respectivas citas"
    )
    alertas: List[Alerta] = Field(
        default_factory=list,
        description="Alertas tempranas identificadas con nivel de criticidad y citas"
    )
    nivelRiesgo: Literal["ALTO", "MEDIO", "BAJO"] = Field(
        ...,
        description="Nivel global de riesgo ambiental estimado para el proyecto"
    )
    sinEvidencia: List[str] = Field(
        default_factory=list,
        description="Aspectos, componentes o dudas que los fragmentos recuperados NO cubren"
    )
    advertencia: str = Field(
        default="Documento de apoyo. No reemplaza la evaluación del SEA ni tiene validez legal.",
        description="Descargo de responsabilidad legal obligatorio"
    )
    fuentes: List[FragmentoRecuperado] = Field(
        default_factory=list,
        description="Fragmentos oficiales citados en el pre-informe que respaldan el análisis"
    )
    metadatos: MetadatosPreinforme = Field(
        default_factory=MetadatosPreinforme,
        description="Metadatos técnicos de ejecución, latencia y consumo de tokens"
    )


class ConsultaProyectoInput(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    tipoProyecto: str = Field(..., description="Tipo o tipología del proyecto (ej: 'Extracción de áridos')")
    region: str = Field(..., description="Región político-administrativa donde se ubica el proyecto")
    comuna: str = Field(..., description="Comuna específica del proyecto")
    descripcion: str = Field(..., description="Descripción detallada de actividades, obras y alcances")


class GenerarPreinformeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    proyecto: ConsultaProyectoInput = Field(
        ...,
        description="Descripción del proyecto a consultar"
    )
    fragmentos: List[FragmentoRecuperado] = Field(
        default_factory=list,
        description="Fragmentos recuperados de las guías mediante FAISS"
    )

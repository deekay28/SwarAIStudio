"""Request/response models shared by the API and the providers."""

from __future__ import annotations

import unicodedata
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, field_validator

MAX_LYRICS_CHARS = 6000
MAX_CAPTION_CHARS = 1000


def _clean_text(value: str) -> str:
    # NFC keeps Devanagari matras and nuktas composed consistently.
    value = unicodedata.normalize("NFC", value).replace("\r\n", "\n").replace("\r", "\n")
    # Drop control characters except newline and tab; keep ZWJ/ZWNJ which Indic scripts need.
    return "".join(
        ch for ch in value if ch in "\n\t" or unicodedata.category(ch) != "Cc"
    ).strip()


class AudioFormat(str, Enum):
    mp3 = "mp3"
    wav = "wav"
    flac = "flac"


class GenerationRequest(BaseModel):
    provider: Optional[str] = Field(default=None, description="Provider id; default provider if omitted")
    caption: str = Field(min_length=1, max_length=MAX_CAPTION_CHARS, description="Style/genre/voice description")
    lyrics: str = Field(default="", max_length=MAX_LYRICS_CHARS)
    instrumental: bool = False
    language: str = Field(default="hi", pattern=r"^[a-z]{2,3}$")
    duration_seconds: int = Field(default=120, ge=10, le=600)
    bpm: Optional[int] = Field(default=None, ge=30, le=300)
    key_scale: Optional[str] = Field(default=None, max_length=24)
    negative_prompt: Optional[str] = Field(default=None, max_length=300)
    variations: int = Field(default=1, ge=1, le=4)
    seed: Optional[int] = Field(default=None, ge=0, le=2**31 - 1)
    audio_format: AudioFormat = AudioFormat.mp3

    @field_validator("caption", "lyrics", "negative_prompt")
    @classmethod
    def clean(cls, value: Optional[str]) -> Optional[str]:
        return None if value is None else _clean_text(value)

    @field_validator("key_scale")
    @classmethod
    def key_scale_chars(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        if not all(ch.isalnum() or ch in " #b" for ch in value):
            raise ValueError("key_scale may only contain letters, digits, spaces, # and b")
        return value.strip() or None


class Pricing(BaseModel):
    unit: str = "second_of_audio"
    usd_per_unit: float
    notes: str = ""


class Capabilities(BaseModel):
    text_to_music: bool = True
    vocals: bool = True
    instrumental: bool = True
    lyrics_languages: str = ""
    min_duration_seconds: int = 10
    max_duration_seconds: int = 600
    max_variations: int = 4
    cancel: str = "best_effort"
    formats: list[AudioFormat] = [AudioFormat.mp3]
    edit_operations: list[str] = []


class ProviderInfo(BaseModel):
    id: str
    name: str
    model: str
    hosting: str  # "hosted" (third-party cloud) or "self_hosted"
    sends_data_to: str
    license: str
    is_ai_music: bool = True
    pricing: Pricing
    capabilities: Capabilities


class JobStatus(str, Enum):
    queued = "queued"
    running = "running"
    succeeded = "succeeded"
    failed = "failed"
    cancelled = "cancelled"


class OutputInfo(BaseModel):
    index: int
    status: JobStatus
    audio_path: Optional[str] = None
    format: Optional[str] = None
    size_bytes: Optional[int] = None
    duration_seconds: Optional[float] = None
    seed: Optional[int] = None
    lyrics_used: Optional[str] = None
    error: Optional[str] = None


class JobInfo(BaseModel):
    id: str
    provider: str
    status: JobStatus
    created_at: float
    estimated_cost_usd: float
    outputs: list[OutputInfo]
    error: Optional[str] = None


class Estimate(BaseModel):
    provider: str
    audio_seconds: int
    estimated_cost_usd: float
    pricing: Pricing
    remaining_daily_audio_seconds: int

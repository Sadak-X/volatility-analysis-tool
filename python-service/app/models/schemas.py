from __future__ import annotations

from datetime import date
from decimal import Decimal
from typing import Any

from pydantic import BaseModel, Field


class TaskPayload(BaseModel):
    taskNo: str | None = None
    taskId: int | None = None
    userId: int | None = None
    taskType: str = "ANALYSIS"
    dataSourceType: str = "AKSHARE"
    stockMode: str = "SINGLE"
    stockCodes: list[str] = Field(default_factory=list)
    dateStart: date
    dateEnd: date
    chartSpanModes: list[str] = Field(default_factory=lambda: ["YEAR", "MONTH", "WEEK"])
    confidenceLevel: Decimal = Decimal("0.90")
    useDefaultParams: bool = True
    timeGranularity: str = "MONTH"
    windowSize: int = 20
    forecastType: str = "MONTH"
    records: list[dict[str, Any]] | None = None
    fileId: str | None = None
    uploadedFilePath: str | None = None

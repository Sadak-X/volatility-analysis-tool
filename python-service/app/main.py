from __future__ import annotations

from typing import Any

import pandas as pd
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from app.models.schemas import TaskPayload
from app.services.assessment_service import assess_frame, build_assessment_items
from app.services.forecast_service import build_forecast_items, forecast_frame
from app.services.market_service import ingest_items, load_excel_result, load_market_frame, search_stock_pool
from app.services.volatility_service import build_calc_items, calculate_metrics

app = FastAPI(title="Volatility Python Service", version="1.0.0")


@app.exception_handler(ValueError)
def handle_value_error(request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"detail": str(exc)})


def load_frames(payload: TaskPayload) -> list[pd.DataFrame]:
    if payload.dataSourceType.upper() == "EXCEL" and payload.uploadedFilePath:
        return load_excel_result(payload.uploadedFilePath, payload.stockCodes)["frames"]
    return [load_market_frame(stock_code, payload.dateStart, payload.dateEnd) for stock_code in payload.stockCodes]


@app.post("/py-api/ingest/market")
def ingest_market(payload: TaskPayload) -> dict[str, Any]:
    if not payload.stockCodes:
        raise HTTPException(status_code=400, detail="缺少股票代码")
    frames = load_frames(payload)
    return ingest_items(frames)


@app.post("/py-api/ingest/excel")
def ingest_excel(payload: TaskPayload) -> dict[str, Any]:
    if not payload.uploadedFilePath:
        raise HTTPException(status_code=400, detail="缺少上传文件路径")
    result = load_excel_result(payload.uploadedFilePath, payload.stockCodes)
    data = ingest_items(result["frames"])
    data["errorCount"] = result["error_count"]
    data["errorFilePath"] = result["error_file_path"]
    data["errorFileName"] = result["error_file_name"]
    return data


@app.post("/py-api/calc/volatility")
def calc_volatility(payload: TaskPayload) -> dict[str, Any]:
    return build_calc_items(load_frames(payload), payload.windowSize, payload.chartSpanModes)


@app.post("/py-api/assess/run")
def assess_run(payload: TaskPayload) -> dict[str, Any]:
    return build_assessment_items(load_frames(payload), payload.windowSize)


@app.post("/py-api/forecast/run")
def forecast_run(payload: TaskPayload) -> dict[str, Any]:
    return build_forecast_items(load_frames(payload), payload.forecastType, payload.confidenceLevel)


@app.post("/py-api/ai/payload")
def ai_payload(payload: TaskPayload) -> dict[str, Any]:
    frame = load_frames(payload)[0]
    calc = calculate_metrics(frame, payload.windowSize, payload.chartSpanModes)
    assess = assess_frame(frame, payload.windowSize)
    forecast = forecast_frame(frame, payload.forecastType, payload.confidenceLevel)
    latest_close = float(frame.iloc[-1]["close"])
    donchian = assess["donchian"]
    return {
        "stockCode": str(frame.iloc[-1]["stock_code"]),
        "stockName": str(frame.iloc[-1]["stock_name"]),
        "analysisPeriod": f"{payload.dateStart} ~ {payload.dateEnd}",
        "historicalVolatility": calc["yzVolatility"],
        "impliedVolatility": calc["impliedVolatility"],
        "impliedVolatilityMethod": calc["impliedVolatilityMethod"],
        "scoreTotal": assess["scoreTotal"],
        "scoreStability": assess["scoreStability"],
        "scoreRisk": assess["scoreRisk"],
        "riskLevel": assess["riskLevel"],
        "qualitativeLabel": assess["qualitativeLabel"],
        "industryAvgVol": assess["industryAvgVol"],
        "predictVolatility": forecast["predictVolatility"],
        "ciLower": forecast["ciLower"],
        "ciUpper": forecast["ciUpper"],
        "modelName": forecast["modelName"],
        "volTrend": infer_trend(calc["trendSeries"]),
        "forecastInterval": f"{forecast['ciLower']:.2%} ~ {forecast['ciUpper']:.2%}",
        "donchianSummary": build_donchian_summary(latest_close, donchian),
        "confidenceLevel": f"{float(payload.confidenceLevel):.0%}",
        "confidenceLevelValue": float(payload.confidenceLevel),
        "donchianUpper": donchian.get("upper"),
        "donchianMiddle": donchian.get("middle"),
        "donchianLower": donchian.get("lower"),
        "donchianLatestClose": donchian.get("latestClose"),
    }


@app.get("/py-api/task/{task_no}/health")
def task_health(task_no: str) -> dict[str, Any]:
    return {"taskNo": task_no, "status": "ALIVE"}


@app.post("/py-api/rank/high-volatility")
def high_volatility_rank(payload: TaskPayload) -> dict[str, Any]:
    frames = load_frames(payload)
    forecast = build_forecast_items(frames, payload.forecastType, payload.confidenceLevel)
    ranked = sorted(forecast["items"], key=lambda item: item["predictVolatility"], reverse=True)
    return {"items": ranked}


@app.get("/py-api/stock/search")
def stock_search(keyword: str = "") -> dict[str, Any]:
    return {"items": search_stock_pool(keyword)}


def infer_trend(series: list[dict[str, Any]]) -> str:
    values = [item["value"] for item in series[-10:]]
    if len(values) < 2:
        return "走势平稳"
    diff = values[-1] - values[0]
    if diff > 0.03:
        return "近期明显上行"
    if diff > 0.01:
        return "近期缓慢上行"
    if diff < -0.03:
        return "近期明显回落"
    if diff < -0.01:
        return "近期缓慢回落"
    return "走势平稳"


def build_donchian_summary(latest_close: float, donchian: dict[str, Any]) -> str:
    upper = float(donchian["upper"])
    lower = float(donchian["lower"])
    middle = float(donchian["middle"])

    if latest_close >= upper:
        return "当前价格接近上轨，存在突破后的放大波动风险"
    if latest_close <= lower:
        return "当前价格靠近下轨，需关注下行波动放大"
    if latest_close >= middle:
        return "当前价格位于中上轨之间，未出现明显突破"
    return "当前价格位于中下轨之间，整体仍在通道内运行"

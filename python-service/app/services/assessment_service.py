from __future__ import annotations

from typing import Any

import numpy as np
import pandas as pd

from app.services.volatility_service import calculate_metrics


def build_assessment_items(frames: list[pd.DataFrame], window_size: int) -> dict[str, Any]:
    items = [assess_frame(frame, window_size) for frame in frames]
    return {"items": items}

def assess_frame(frame: pd.DataFrame, window_size: int) -> dict[str, Any]:
    calc = calculate_metrics(frame, window_size)
    trend_values = [point["value"] for point in calc["trendSeries"]]
    latest_vol = float(calc["yzVolatility"])

    if len(trend_values) >= 20:
        recent = trend_values[-20:]
        x = np.arange(len(recent))
        slope = float(np.polyfit(x, recent, 1)[0])
    else:
        slope = 0.0

    vol_cv = np.std(trend_values[-20:]) / (np.mean(trend_values[-20:]) + 1e-6)
    stability = (1 / (1 + vol_cv * 3)) * 100

    sorted_vols = np.sort(trend_values)
    pos = np.searchsorted(sorted_vols, latest_vol)
    risk_percentile = pos / len(sorted_vols)
    score_risk = risk_percentile * 100

    slope_norm = np.clip(slope * 2000, -1, 1)
    trend_score = (slope_norm + 1) / 2 * 100

    score_total = 0.6 * score_risk + 0.2 * trend_score + 0.2 * stability

    if score_total < 33:
        risk_level = "LOW"
    elif score_total < 67:
        risk_level = "MEDIUM"
    else:
        risk_level = "HIGH"

    qualitative_label = f"{'低' if score_total<33 else '中' if score_total<67 else '高'}风险 / {'稳定' if stability>66 else '一般' if stability>33 else '不稳定'}"



    donchian = build_donchian(frame, window_size)

    return {
        "stockCode": str(frame.iloc[-1]["stock_code"]),
        "scoreTotal": round(score_total, 2),
        "scoreStability": round(stability, 2),
        "scoreRisk": round(score_risk, 2),
        "riskLevel": risk_level,
        "qualitativeLabel": qualitative_label,
        "donchian": donchian,
    }

def build_donchian(frame: pd.DataFrame, window_size: int) -> dict[str, Any]:
    series = build_donchian_series(frame, window_size)
    if not series:
        return {}
    latest = series[-1]
    return {
        "date": latest["date"],
        "upper": latest["upper"],
        "middle": latest["middle"],
        "lower": latest["lower"],
        "latestClose": latest["latestClose"],
        "series": series,
    }

def build_donchian_series(frame: pd.DataFrame, window_size: int) -> list[dict[str, Any]]:
    df = frame.copy()
    df["trade_date"] = pd.to_datetime(df["trade_date"], errors="coerce")
    df = df.dropna(subset=["trade_date", "high", "low", "close"]).sort_values("trade_date").reset_index(drop=True)
    if df.empty:
        return []

    points: list[dict[str, Any]] = []
    for _, month_frame in df.groupby(pd.Grouper(key="trade_date", freq="ME")):
        if month_frame.empty:
            continue
        point_date = month_frame["trade_date"].max()
        window = df[df["trade_date"] <= point_date].tail(window_size)
        if window.empty:
            continue
        points.append(build_donchian_point(point_date, window))
    return points

def build_donchian_point(point_date: pd.Timestamp, window: pd.DataFrame) -> dict[str, Any]:
    return {
        "date": str(pd.to_datetime(point_date).date()),
        "upper": round(float(window["high"].max()), 4),
        "middle": round(float(window["close"].mean()), 4),
        "lower": round(float(window["low"].min()), 4),
        "latestClose": round(float(window.iloc[-1]["close"]), 4),
    }
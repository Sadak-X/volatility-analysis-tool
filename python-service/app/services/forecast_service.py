from __future__ import annotations
from decimal import Decimal
from statistics import NormalDist
from typing import Any, Tuple

import numpy as np
import pandas as pd

try:
    from arch import arch_model
except Exception:
    arch_model = None

SPAN_POINT_COUNTS = {"DAY": 20, "MONTH": 6, "YEAR": 3}


def build_forecast_items(frames: list[pd.DataFrame], forecast_type: str, confidence_level: Decimal) -> dict[str, Any]:
    items = [forecast_frame(frame, forecast_type, confidence_level) for frame in frames]
    return {"items": items}


def forecast_frame(frame: pd.DataFrame, forecast_type: str, confidence_level: Decimal) -> dict[str, Any]:
    returns = np.log(frame["close"]).diff().dropna() * 100
    z_value = NormalDist().inv_cdf((1 + float(confidence_level)) / 2)
    requested_type = normalize_forecast_type(forecast_type)
    last_trade_date = pd.Timestamp(frame.iloc[-1]["trade_date"])

    spans_info = []
    all_offsets = []
    for span_type in ["DAY", "MONTH", "YEAR"]:
        target_dates = resolve_target_dates(last_trade_date, span_type)
        offsets = resolve_business_day_offsets(last_trade_date, target_dates)
        spans_info.append((span_type, target_dates, offsets))
        all_offsets.extend(offsets)
    max_offset = max(all_offsets) if all_offsets else 1

    sigma_full, model_name, long_term_vol = fit_and_forecast_sigma(returns, max_offset)

    span_series = {}
    span_summary = {}
    for span_type, target_dates, offsets in spans_info:
        selected_sigma = np.array([float(sigma_full[offset - 1]) for offset in offsets])
        summary = build_forecast_summary_v2(
            target_dates, offsets, selected_sigma, z_value,
            center_value=long_term_vol
        )
        span_series[span_type] = summary["details"]
        span_summary[span_type] = {
            "predictVolatility": summary["predictVolatility"],
            "ciLower": summary["ciLower"],
            "ciUpper": summary["ciUpper"],
            "serValue": summary["serValue"],
        }

    selected_summary = span_summary[requested_type]
    details = span_series[requested_type]
    predict_vol = long_term_vol
    ci_lower = selected_summary["ciLower"]
    ci_upper = selected_summary["ciUpper"]
    ser_value = selected_summary["serValue"]
    risk_level = "HIGH" if predict_vol >= 0.28 else "MEDIUM" if predict_vol >= 0.18 else "LOW"

    return {
        "stockCode": str(frame.iloc[-1]["stock_code"]),
        "forecastType": requested_type,
        "predictVolatility": predict_vol,
        "ciLower": ci_lower,
        "ciUpper": ci_upper,
        "serValue": ser_value,
        "riskLevel": risk_level,
        "modelName": model_name,
        "details": details,
        "spanSeries": span_series,
        "spanSummary": span_summary,
    }


def fit_and_forecast_sigma(returns: pd.Series, horizon: int) -> Tuple[np.ndarray, str, float]:
    if arch_model is not None and len(returns) > 60:
        try:
            fitted = arch_model(returns, mean="Zero", vol="Garch", p=1, q=1, dist="normal").fit(disp="off")
            alpha = fitted.params["alpha[1]"]
            beta = fitted.params["beta[1]"]
            persistence = alpha + beta
            if fitted.convergence_flag != 0 or persistence >= 0.99:
                raise RuntimeError("GARCH unreliable")
            omega = fitted.params["omega"]
            long_term_var = omega / (1 - alpha - beta)
            long_term_vol = np.sqrt(max(long_term_var, 1e-9)) / 100 * np.sqrt(252)
            var_forecast = fitted.forecast(horizon=horizon).variance.iloc[-1].to_numpy()
            sigma = np.sqrt(np.maximum(var_forecast, 1e-9)) / 100 * np.sqrt(252)
            return sigma, "GARCH(1,1)", long_term_vol
        except Exception:
            pass

    sigma = ewma_sigma_fixed(returns.to_numpy(), horizon)
    long_term_vol = sigma[-1]
    return sigma, "EWMA", long_term_vol


def ewma_sigma_fixed(returns: np.ndarray, horizon: int, lam: float = 0.94) -> np.ndarray:
    if len(returns) < 30:
        var = np.var(returns)
    else:
        var = np.var(returns[-30:])
    var = lam * var + (1 - lam) * (returns[-1] ** 2)
    sigma = np.sqrt(max(var, 1e-9)) / 100 * np.sqrt(252)
    return np.full(horizon, sigma)


def build_forecast_summary_v2(
    target_dates: list[pd.Timestamp],
    selected_sigma: np.ndarray,
    z_value: float,
    scale_factor: float = 0.2,
    center_value: float | None = None,
) -> dict[str, Any]:
    n = len(selected_sigma)
    if center_value is None:
        center_value = float(np.mean(selected_sigma))

    widths = z_value * selected_sigma * scale_factor

    details = []
    for date, pred, w in zip(target_dates, selected_sigma, widths):
        pred_val = float(pred)
        w_val = float(w)
        details.append({
            "date": str(date.date()),
            "predValue": round(pred_val, 6),
            "ciLower": round(max(pred_val - w_val, 0), 6),
            "ciUpper": round(pred_val + w_val, 6),
            "serValue": round(abs(pred_val - center_value) / max(center_value, 1e-6), 6),
        })

    mean_width = np.sqrt(np.mean(widths ** 2)) / np.sqrt(n) if n > 1 else float(widths[0])

    return {
        "predictVolatility": round(center_value, 6),
        "ciLower": round(max(center_value - mean_width, 0), 6),
        "ciUpper": round(center_value + mean_width, 6),
        "serValue": round(float(np.mean([item["serValue"] for item in details])), 6),
        "details": details,
    }


def normalize_forecast_type(forecast_type: str) -> str:
    value = str(forecast_type or "MONTH").upper()
    return value if value in {"DAY", "MONTH", "YEAR"} else "MONTH"


def resolve_target_dates(last_trade_date: pd.Timestamp, forecast_type: str) -> list[pd.Timestamp]:
    normalized = normalize_forecast_type(forecast_type)
    start = pd.Timestamp(last_trade_date) + pd.offsets.BDay(1)
    point_count = SPAN_POINT_COUNTS[normalized]

    if normalized == "DAY":
        return [pd.Timestamp(start + pd.offsets.BDay(i)) for i in range(point_count)]
    if normalized == "MONTH":
        first = pd.offsets.BMonthEnd().rollforward(start)
        return [pd.Timestamp(first + pd.offsets.BMonthEnd(i)) for i in range(point_count)]
    first = pd.offsets.BYearEnd().rollforward(start)
    return [pd.Timestamp(first + pd.offsets.BYearEnd(i)) for i in range(point_count)]


def resolve_business_day_offsets(last_trade_date: pd.Timestamp, target_dates: list[pd.Timestamp]) -> list[int]:
    start = last_trade_date + pd.offsets.BDay(1)
    return [np.busday_count(start.date(), target_date.date()) for target_date in target_dates]
from __future__ import annotations

import re
from typing import Any

import numpy as np
import pandas as pd
from scipy.stats import norm

try:
    from arch import arch_model

    ARCH_AVAILABLE = True
except ImportError:
    ARCH_AVAILABLE = False


def black_scholes_call(S: float, K: float, T: float, r: float, sigma: float) -> float:
    if sigma <= 0 or T <= 0:
        return max(S - K * np.exp(-r * T), 0)
    d1 = (np.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * np.sqrt(T))
    d2 = d1 - sigma * np.sqrt(T)
    return S * norm.cdf(d1) - K * np.exp(-r * T) * norm.cdf(d2)


def vega(S: float, K: float, T: float, r: float, sigma: float) -> float:
    if sigma <= 0 or T <= 0:
        return 0
    d1 = (np.log(S / K) + (r + 0.5 * sigma ** 2) * T) / (sigma * np.sqrt(T))
    return S * norm.pdf(d1) * np.sqrt(T)


def implied_volatility_newton(
        S: float, K: float, T: float, r: float, market_price: float,
        sigma_init: float = 0.2, tol: float = 1e-6, max_iter: int = 100
) -> float:
    sigma = sigma_init
    for _ in range(max_iter):
        price = black_scholes_call(S, K, T, r, sigma)
        v = vega(S, K, T, r, sigma)
        diff = price - market_price
        if abs(diff) < tol:
            return sigma
        if v < 1e-8:
            sigma = sigma * 1.05 if price < market_price else sigma * 0.95
        else:
            sigma = sigma - diff / v
        sigma = max(0.01, min(3.0, sigma))
    return sigma


def forecast_volatility_with_garch(returns: pd.Series, horizon: int = 30) -> float:
    if not ARCH_AVAILABLE or len(returns) < 60:
        lam = 0.94
        variance = np.var(returns[-30:]) if len(returns) >= 30 else np.var(returns)
        for _ in range(horizon):
            variance = lam * variance + (1 - lam) * (returns.iloc[-1] ** 2)
        return np.sqrt(max(variance, 1e-9)) * np.sqrt(252)

    try:
        returns_pct = returns * 100
        model = arch_model(returns_pct, mean='Zero', vol='Garch', p=1, q=1, dist='normal')
        fitted = model.fit(disp='off')
        forecast = fitted.forecast(horizon=horizon)
        variance_forecast = forecast.variance.iloc[-1, -1] / 10000
        return np.sqrt(max(variance_forecast, 1e-9)) * np.sqrt(252)
    except Exception:
        lam = 0.94
        variance = np.var(returns[-30:]) if len(returns) >= 30 else np.var(returns)
        for _ in range(horizon):
            variance = lam * variance + (1 - lam) * (returns.iloc[-1] ** 2)
        return np.sqrt(max(variance, 1e-9)) * np.sqrt(252)


def calculate_implied_volatility_with_fake_option(
        S: float,
        returns: pd.Series,
        yz_vol: float,
        T_days: int = 30,
        r: float = 0.025,
        method: str = "garch"
) -> float:
    K = S
    T = T_days / 365.0
    if method == "garch":
        forecast_vol = forecast_volatility_with_garch(returns, horizon=T_days)
    elif method == "ewma":
        lam = 0.94
        variance = np.var(returns[-30:]) if len(returns) >= 30 else np.var(returns)
        for _ in range(T_days):
            variance = lam * variance + (1 - lam) * (returns.iloc[-1] ** 2)
        forecast_vol = np.sqrt(max(variance, 1e-9)) * np.sqrt(252)
    else:
        forecast_vol = yz_vol * 1.05

    fake_market_price = black_scholes_call(S, K, T, r, forecast_vol)
    return implied_volatility_newton(S, K, T, r, fake_market_price, sigma_init=forecast_vol)


def build_calc_items(frames: list[pd.DataFrame], window_size: int, chart_span_modes: list[str] | None = None) -> dict[str, Any]:
    items = [calculate_metrics(frame, window_size, chart_span_modes) for frame in frames]
    return {"items": items}


def calculate_metrics(frame: pd.DataFrame, window_size: int, chart_span_modes: list[str] | None = None) -> dict[str, Any]:
    df = frame.copy()
    df["prev_close"] = df["close"].shift(1)
    df["log_oc"] = np.log(df["close"] / df["open"])
    df["log_co"] = np.log(df["open"] / df["prev_close"])
    df["log_ho"] = np.log(df["high"] / df["open"])
    df["log_lo"] = np.log(df["low"] / df["open"])
    df["rs"] = df["log_ho"] * (df["log_ho"] - df["log_oc"]) + df["log_lo"] * (df["log_lo"] - df["log_oc"])
    df = df.dropna().reset_index(drop=True)

    if len(df) < max(window_size + 2, 30):
        raise ValueError("有效数据不足，无法计算波动率")

    yz_series = []
    dates = []
    for index in range(window_size, len(df)):
        window = df.iloc[index - window_size:index+1]
        k = 0.34 / (1.34 + (window_size + 1) / (window_size - 1))
        sigma_o = window["log_co"].var(ddof=1)
        sigma_c = window["log_oc"].var(ddof=1)
        sigma_rs = window["rs"].mean()
        yz = np.sqrt(max(sigma_o + k * sigma_c + (1 - k) * sigma_rs, 0.0)) * np.sqrt(252)
        yz_series.append(float(yz))
        dates.append(window.iloc[-1]["trade_date"])

    trend_series, implied_vol, implied_method = build_dual_volatility_series(frame, dates, yz_series, window_size)
    latest_yz = yz_series[-1]
    aggregations = {
        "YEAR": aggregate_dual_series(trend_series, "YE"),
        "MONTH": aggregate_dual_series(trend_series, "ME"),
        "WEEK": aggregate_dual_series(trend_series, "W-FRI"),
    }

    for mode in normalize_custom_span_modes(chart_span_modes):
        days = custom_span_days(mode)
        if days is not None:
            aggregations[mode] = aggregate_custom_dual_series(trend_series, days)

    return {
        "stockCode": str(frame.iloc[-1]["stock_code"]),
        "calcDate": str(pd.to_datetime(frame.iloc[-1]["trade_date"]).date()),
        "yzVolatility": round(latest_yz, 6),
        "impliedVolatility": round(implied_vol, 6),
        "impliedVolatilityMethod": implied_method,
        "windowSize": window_size,
        "trendSeries": trend_series,
        "aggregations": aggregations,
    }


def build_dual_volatility_series(
        frame: pd.DataFrame,
        dates: list[Any],
        yz_series: list[float],
        window_size: int,
        option_T_days: int = 30,
        risk_free_rate: float = 0.025,
        pricing_method: str = "garch",
) -> tuple[list[dict[str, Any]], float, str]:
    historical_frame = pd.DataFrame(
        {
            "date": pd.to_datetime(dates, errors="coerce"),
            "value": pd.to_numeric(yz_series, errors="coerce"),
        }
    ).dropna(subset=["date", "value"])

    trade_dates = pd.to_datetime(frame["trade_date"], errors="coerce")
    close_prices = pd.to_numeric(frame["close"], errors="coerce")
    returns = np.log(close_prices / close_prices.shift(1)).dropna()

    results = []
    for _, row in historical_frame.iterrows():
        current_date = row["date"]
        yz_vol = row["value"]
        S = close_prices[trade_dates == current_date].values
        if len(S) == 0 or yz_vol <= 0:
            results.append({"date": current_date, "value": yz_vol, "impliedValue": yz_vol})
            continue
        S = float(S[0])
        current_returns = returns[trade_dates.iloc[1:] <= current_date]
        if len(current_returns) < 30:
            results.append({"date": current_date, "value": yz_vol, "impliedValue": yz_vol})
            continue
        try:
            iv = calculate_implied_volatility_with_fake_option(
                S=S, returns=current_returns, yz_vol=yz_vol,
                T_days=option_T_days, r=risk_free_rate, method=pricing_method
            )
            results.append({"date": current_date, "value": yz_vol, "impliedValue": iv})
        except Exception:
            results.append({"date": current_date, "value": yz_vol, "impliedValue": yz_vol})

    result_df = pd.DataFrame(results).dropna(subset=["date", "impliedValue"])
    if result_df.empty:
        return [], 0.0, "BSM_FAKE_OPTION_FAILED"

    points = series_to_points(result_df)
    latest_implied = float(result_df["impliedValue"].iloc[-1])
    return points, latest_implied, f"BSM_FAKE_OPTION_{pricing_method.upper()}"


def aggregate_dual_series(points: list[dict[str, Any]], rule: str) -> list[dict[str, Any]]:
    series = pd.DataFrame(points)
    if series.empty:
        return []
    series["date"] = pd.to_datetime(series["date"], errors="coerce")
    series["value"] = pd.to_numeric(series["value"], errors="coerce")
    series["impliedValue"] = pd.to_numeric(series.get("impliedValue"), errors="coerce")
    grouped = (
        series.groupby(pd.Grouper(key="date", freq=rule))
        .agg(date=("date", "max"), value=("value", "mean"), impliedValue=("impliedValue", "mean"))
        .dropna(subset=["date", "value"], how="all")
        .reset_index(drop=True)
    )
    return [
        {
            "date": str(row.date.date()),
            "value": round(float(row.value), 6),
            "impliedValue": round(float(row.impliedValue), 6) if pd.notna(row.impliedValue) else None,
        }
        for row in grouped.itertuples(index=False)
    ]


def series_to_points(frame: pd.DataFrame) -> list[dict[str, Any]]:
    return [
        {
            "date": str(row.date.date()) if hasattr(row.date, 'date') else str(row.date),
            "value": round(float(row.value), 6),
            "impliedValue": round(float(row.impliedValue), 6) if pd.notna(row.impliedValue) else None,
        }
        for row in frame.itertuples(index=False)
        if pd.notna(row.date) and pd.notna(row.value)
    ]


def normalize_custom_span_modes(chart_span_modes: list[str] | None) -> list[str]:
    if not chart_span_modes:
        return []
    result: list[str] = []
    for mode in chart_span_modes:
        value = str(mode or "").strip().upper()
        if custom_span_days(value) is not None and value not in result:
            result.append(value)
    return result


def custom_span_days(mode: str) -> int | None:
    match = re.fullmatch(r"CUSTOM_(\d{1,3})", str(mode or "").strip().upper())
    if not match:
        return None
    days = int(match.group(1))
    return days if 2 <= days <= 252 else None


def aggregate_custom_dual_series(points: list[dict[str, Any]], days: int) -> list[dict[str, Any]]:
    series = pd.DataFrame(points)
    if series.empty:
        return []
    series["date"] = pd.to_datetime(series["date"], errors="coerce")
    series["value"] = pd.to_numeric(series["value"], errors="coerce")
    series["impliedValue"] = pd.to_numeric(series.get("impliedValue"), errors="coerce")
    series = series.dropna(subset=["date", "value"]).sort_values("date").reset_index(drop=True)
    if series.empty:
        return []
    series["groupNo"] = series.index // days
    grouped = (
        series.groupby("groupNo")
        .agg(date=("date", "max"), value=("value", "mean"), impliedValue=("impliedValue", "mean"))
        .reset_index(drop=True)
    )
    return [
        {
            "date": str(row.date.date()),
            "value": round(float(row.value), 6),
            "impliedValue": round(float(row.impliedValue), 6) if pd.notna(row.impliedValue) else None,
        }
        for row in grouped.itertuples(index=False)
    ]
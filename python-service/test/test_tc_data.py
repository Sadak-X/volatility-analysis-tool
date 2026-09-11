
import pandas as pd
import pytest

from app.services.market_service import (
    normalize_excel_rows,
    normalize_compact_price_excel,
)


def build_valid_frame(**kwargs):
    data = {
        "stock_code": ["000001"],
        "trade_date": ["2024-01-01"],
        "open": [10.0],
        "high": [11.0],
        "low": [9.0],
        "close": [10.5],
        "volume": [1000],
        "amount": [10000],
    }
    for key, value in kwargs.items():
        data[key] = [value]
    return pd.DataFrame(data)


@pytest.mark.tc_data
def test_TC_DATA_001_missing_required_column():
    """TC-DATA-001: Excel缺失必填列"""
    frame = build_valid_frame().drop(columns=["close"])

    with pytest.raises(ValueError, match="Excel 缺少必填列:.*close"):
        normalize_excel_rows(frame, fallback_code=None)


@pytest.mark.tc_data
def test_TC_DATA_002_stock_code_fallback():
    """TC-DATA-002: 股票代码为空时使用fallback"""
    frame = build_valid_frame().drop(columns=["stock_code"])

    normalized, errors = normalize_excel_rows(frame, fallback_code="000001")

    assert errors == []
    assert len(normalized) == 1
    assert normalized.iloc[0]["stock_code"] == "000001"


@pytest.mark.tc_data
def test_TC_DATA_003_invalid_trade_date():
    """TC-DATA-003: 交易日期格式非法"""
    frame = build_valid_frame(trade_date="二零二三年")

    normalized, errors = normalize_excel_rows(frame, fallback_code="000001")

    assert len(normalized) == 0
    assert errors[0]["rule_code"] == "CLN-02"


@pytest.mark.tc_data
def test_TC_DATA_004_missing_price():
    """TC-DATA-004: 核心价格缺失"""
    frame = build_valid_frame(high=None)

    normalized, errors = normalize_excel_rows(frame, fallback_code="000001")

    assert len(normalized) == 0
    assert errors[0]["rule_code"] == "CLN-03"


@pytest.mark.tc_data
def test_TC_DATA_005_zero_price():
    """TC-DATA-005: 价格出现负数或0"""
    frame = build_valid_frame(open=0)

    normalized, errors = normalize_excel_rows(frame, fallback_code="000001")

    assert len(normalized) == 0
    assert errors[0]["rule_code"] == "CLN-06"


@pytest.mark.tc_data
def test_TC_DATA_006_price_extreme_logic():
    """TC-DATA-006: 极值逻辑异常"""
    frame = build_valid_frame(high=10.0, close=11.0)

    normalized, errors = normalize_excel_rows(frame, fallback_code="000001")

    assert errors == []
    assert normalized.iloc[0]["high"] >= normalized.iloc[0]["close"]


@pytest.mark.tc_data
def test_TC_DATA_007_compact_excel_normalization():
    """TC-DATA-007: 截断Excel数据"""
    compact = pd.DataFrame(
        {
            "日期": ["2024-01-01"],
            "收盘价": [10.5],
        }
    )

    normalized = normalize_compact_price_excel(compact)

    assert {"trade_date", "open", "high", "low", "close"}.issubset(normalized.columns)
    assert normalized.iloc[0]["open"] == 10.5
    assert normalized.iloc[0]["high"] == 10.5
    assert normalized.iloc[0]["low"] == 10.5

import numpy as np

from app.services.volatility_service import (
    calculate_metrics,
    custom_span_days,
    implied_volatility_newton,
)


def build_market_frame(rows=30, flat=False):
    dates = pd.date_range("2024-01-01", periods=rows)
    prices = np.full(rows, 10.0) if flat else np.linspace(10, 12, rows)
    return pd.DataFrame({
        "stock_code": ["000001"] * rows,
        "trade_date": dates,
        "open": prices,
        "high": prices,
        "low": prices,
        "close": prices,
    })


@pytest.mark.tc_data
def test_TC_DATA_008_calculate_metrics_exact_window_boundary():
    """TC-DATA-008: 数据量满足windowSize边界时成功计算"""
    result = calculate_metrics(build_market_frame(30), window_size=20)
    assert result["yzVolatility"] >= 0


@pytest.mark.tc_data
def test_TC_DATA_009_calculate_metrics_insufficient_data():
    """TC-DATA-009: 数据不足抛出ValueError"""
    with pytest.raises(ValueError, match="有效数据不足"):
        calculate_metrics(build_market_frame(29), window_size=20)


@pytest.mark.tc_data
def test_TC_DATA_010_flat_market_yz_volatility_zero():
    """TC-DATA-010: 平盘行情YZ波动率为0"""
    result = calculate_metrics(build_market_frame(30, flat=True), window_size=20)
    assert result["yzVolatility"] == 0.0


@pytest.mark.tc_data
def test_TC_DATA_011_implied_volatility_newton_positive():
    """TC-DATA-011: 牛顿法隐含波动率返回正值"""
    value = implied_volatility_newton(10, 10, 30 / 365, 0.025, 0.25)
    assert value > 0


@pytest.mark.tc_data
def test_TC_DATA_012_vega_small_branch(monkeypatch):
    """TC-DATA-012: vega极小时触发保护逻辑"""
    from app.services import volatility_service
    monkeypatch.setattr(volatility_service, "vega", lambda *args: 0.0)
    monkeypatch.setattr(volatility_service, "black_scholes_call", lambda *args: 0.1)
    value = volatility_service.implied_volatility_newton(10, 10, 30 / 365, 0.025, 0.2)
    assert value > 0


@pytest.mark.tc_data
def test_TC_DATA_013_insufficient_return_fallback_to_yz():
    """TC-DATA-013: 收益样本不足时impliedValue回退YZ"""
    from app.services.volatility_service import build_dual_volatility_series
    frame = build_market_frame(30)
    points, _, _ = build_dual_volatility_series(frame, list(frame["trade_date"].iloc[20:]), [0.2] * 10, 20)
    assert points
    assert all(item["value"] == item["impliedValue"] for item in points)


@pytest.mark.tc_data
def test_TC_DATA_014_custom_span_mode():
    """TC-DATA-014: CUSTOM_10解析成功"""
    assert custom_span_days("CUSTOM_10") == 10


@pytest.mark.tc_data
def test_TC_DATA_015_custom_span_out_of_range():
    """TC-DATA-015: CUSTOM_300越界返回None"""
    assert custom_span_days("CUSTOM_300") is None


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

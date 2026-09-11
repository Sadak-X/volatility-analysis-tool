
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

from app.services.assessment_service import assess_frame
from app.main import infer_trend, build_donchian_summary


def build_assessment_frame(rows=80):
    dates = pd.date_range("2024-01-01", periods=rows, freq="D")
    prices = [10 + i * 0.01 for i in range(rows)]
    return pd.DataFrame({
        "stock_code": ["000001"] * rows,
        "trade_date": dates,
        "open": prices,
        "high": [p + 0.1 for p in prices],
        "low": [p - 0.1 for p in prices],
        "close": prices,
    })


@pytest.mark.tc_data
def test_TC_DATA_016_low_risk_level_boundary():
    """TC-DATA-016: 低风险综合评分判定"""
    from app.services import assessment_service

    def fake_calc(*args, **kwargs):
        return {"yzVolatility": 1, "trendSeries": [{"value": 1}] * 20}

    original = assessment_service.calculate_metrics
    assessment_service.calculate_metrics = fake_calc
    try:
        result = assess_frame(build_assessment_frame(), 20)
        assert result["riskLevel"] == "LOW"
    finally:
        assessment_service.calculate_metrics = original


@pytest.mark.tc_data
def test_TC_DATA_017_medium_risk_level_boundary(monkeypatch):
    """TC-DATA-017:中风险判定"""

    from app.services import assessment_service


    trend_values = [
        100 + 0.001 * i
        for i in range(20)
    ]

    yz_vol = trend_values[8] + 0.0001

    monkeypatch.setattr(
        assessment_service,
        "calculate_metrics",
        lambda *args, **kwargs: {
            "yzVolatility": yz_vol,
            "trendSeries": [
                {"value": value}
                for value in trend_values
            ]
        }
    )

    result = assess_frame(
        build_assessment_frame(),
        20
    )

    assert result["riskLevel"] == "MEDIUM"


@pytest.mark.tc_data
def test_TC_DATA_018_high_risk_level_boundary(monkeypatch):
    """TC-DATA-018:高风险判定"""

    from app.services import assessment_service

    trend_values = [
        100 + 0.001 * i
        for i in range(20)
    ]

    yz_vol = trend_values[9] + 0.0001

    monkeypatch.setattr(
        assessment_service,
        "calculate_metrics",
        lambda *args, **kwargs: {
            "yzVolatility": yz_vol,
            "trendSeries": [
                {"value": value}
                for value in trend_values
            ]
        }
    )

    result = assess_frame(
        build_assessment_frame(),
        20
    )

    assert result["riskLevel"] == "HIGH"


@pytest.mark.tc_data
def test_TC_DATA_019_infer_trend_up():
    """TC-DATA-019: 上行走势推断"""
    assert infer_trend([{"value": 1}, {"value": 1.031}]) == "近期明显上行"


@pytest.mark.tc_data
def test_TC_DATA_020_infer_trend_down():
    """TC-DATA-020: 回落走势推断"""
    assert infer_trend([{"value": 1}, {"value": 0.98}]) == "近期缓慢回落"


@pytest.mark.tc_data
def test_TC_DATA_021_donchian_upper_break():
    """TC-DATA-021: 唐奇安通道上轨突破"""
    assert build_donchian_summary(10, {"upper": 10, "lower": 8, "middle": 9}) == "当前价格接近上轨，存在突破后的放大波动风险"


@pytest.mark.tc_data
def test_TC_DATA_022_donchian_lower_break():
    """TC-DATA-022: 唐奇安通道下轨跌破"""
    assert build_donchian_summary(8, {"upper": 10, "lower": 8, "middle": 9}) == "当前价格靠近下轨，需关注下行波动放大"

from decimal import Decimal


def build_forecast_test_frame(days=120):
    """构造预测测试行情数据"""
    dates = pd.date_range(
        "2024-01-01",
        periods=days,
        freq="B"
    )

    close = np.linspace(
        10,
        12,
        days
    )

    return pd.DataFrame(
        {
            "stock_code": ["000001"] * days,
            "trade_date": dates,
            "close": close,
        }
    )


@pytest.mark.tc_data
def test_TC_DATA_023_forecast_day_target_dates():
    """
    TC-DATA-023:
    forecastType=DAY
    应生成未来20个工作日预测序列
    """
    from app.services.forecast_service import forecast_frame

    frame = build_forecast_test_frame(100)

    result = forecast_frame(
        frame,
        "DAY",
        Decimal("0.90")
    )

    assert result["forecastType"] == "DAY"

    # DAY模式固定20个预测点
    assert len(result["details"]) == 20

    dates = [
        item["date"]
        for item in result["details"]
    ]

    assert len(dates) == 20
    assert dates == sorted(dates)


@pytest.mark.tc_data
def test_TC_DATA_024_garch_model_execute(monkeypatch):
    """
    TC-DATA-024:
    大样本情况下触发GARCH模型
    modelName = GARCH(1,1)
    """
    from app.services import forecast_service

    class MockFitResult:

        convergence_flag = 0

        params = {
            "alpha[1]": 0.05,
            "beta[1]": 0.90,
            "omega": 0.01,
        }

        def forecast(self, horizon):
            class Forecast:

                variance = pd.DataFrame(
                    [
                        [0.02] * horizon
                    ]
                )

            return Forecast()


    class MockArch:

        def fit(self, disp="off"):
            return MockFitResult()


    def mock_arch_model(*args, **kwargs):
        return MockArch()


    monkeypatch.setattr(
        forecast_service,
        "arch_model",
        mock_arch_model
    )

    returns = pd.Series(
        np.random.normal(
            0,
            0.01,
            100
        )
    )

    sigma, model_name, _ = (
        forecast_service.fit_and_forecast_sigma(
            returns,
            20
        )
    )

    assert model_name == "GARCH(1,1)"
    assert len(sigma) == 20



@pytest.mark.tc_data
def test_TC_DATA_025_garch_failure_fallback_ewma(monkeypatch):
    """
    TC-DATA-025:
    GARCH失败后降级EWMA
    """
    from app.services import forecast_service


    class MockArch:

        def fit(self, disp="off"):
            raise RuntimeError(
                "GARCH convergence failed"
            )


    monkeypatch.setattr(
        forecast_service,
        "arch_model",
        lambda *args, **kwargs: MockArch()
    )


    returns = pd.Series(
        np.random.normal(
            0,
            0.01,
            100
        )
    )


    sigma, model_name, _ = (
        forecast_service.fit_and_forecast_sigma(
            returns,
            20
        )
    )


    assert model_name == "EWMA"
    assert len(sigma) == 20



@pytest.mark.tc_data
def test_TC_DATA_026_small_sample_use_ewma(monkeypatch):
    """
    TC-DATA-026:
    returns长度50
    不满足GARCH >60条件
    直接使用EWMA
    """
    from app.services import forecast_service


    called = {
        "garch": False
    }


    def mock_arch_model(*args, **kwargs):
        called["garch"] = True


    monkeypatch.setattr(
        forecast_service,
        "arch_model",
        mock_arch_model
    )


    returns = pd.Series(
        np.random.normal(
            0,
            0.01,
            50
        )
    )


    sigma, model_name, _ = (
        forecast_service.fit_and_forecast_sigma(
            returns,
            20
        )
    )


    assert called["garch"] is False
    assert model_name == "EWMA"
    assert len(sigma) == 20



@pytest.mark.tc_data
def test_TC_DATA_027_confidence_interval_dynamic(monkeypatch):
    """
    TC-DATA-027:
    confidenceLevel=0.99
    置信区间宽度应大于0.90
    """
    from app.services.forecast_service import (
        build_forecast_summary_v2
    )
    from statistics import NormalDist


    target_dates = list(
        pd.date_range(
            "2024-02-01",
            periods=20,
            freq="B"
        )
    )

    sigma = np.full(
        20,
        0.2
    )


    z90 = NormalDist().inv_cdf(
        (1 + 0.90) / 2
    )

    z99 = NormalDist().inv_cdf(
        (1 + 0.99) / 2
    )


    result90 = build_forecast_summary_v2(
        target_dates,
        sigma,
        z90
    )

    result99 = build_forecast_summary_v2(
        target_dates,
        sigma,
        z99
    )


    width90 = (
        result90["ciUpper"]
        -
        result90["ciLower"]
    )

    width99 = (
        result99["ciUpper"]
        -
        result99["ciLower"]
    )


    assert width99 > width90

@pytest.mark.tc_data
def test_TC_DATA_028_forecast_first_business_day_sigma_offset():
    """TC-DATA-028: 验证预测第一天是否正确使用sigma索引"""
    from app.services import forecast_service

    sigma_full = [
        0.01,
        0.02,
        0.03,
        0.04,
        0.05,
    ]

    target_dates = [
        pd.Timestamp("2024-02-01"),
        pd.Timestamp("2024-02-02"),
    ]

    offsets = forecast_service.resolve_business_day_offsets(
        pd.Timestamp("2024-01-31"),
        target_dates
    )

    assert offsets[0] >= 1

    assert offsets[0] != 0



@pytest.mark.tc_data
def test_TC_DATA_029_yz_window_size_boundary():
    """TC-DATA-029: YZ波动率窗口数量"""
    from app.services.volatility_service import calculate_metrics

    frame = pd.DataFrame(
        {
            "stock_code": ["000001"] * 21,
            "trade_date": pd.date_range(
                "2024-01-01",
                periods=21,
                freq="B"
            ),
            "open": np.linspace(
                10,
                11,
                21
            ),
            "high": np.linspace(
                10.1,
                11.1,
                21
            ),
            "low": np.linspace(
                9.9,
                10.9,
                21
            ),
            "close": np.linspace(
                10,
                11,
                21
            ),
        }
    )


    result = calculate_metrics(
        frame,
        window_size=20
    )


    assert "yzVolatility" in result

    assert result["yzVolatility"] is not None



@pytest.mark.tc_data
def test_TC_DATA_030_yz_volatility_no_nan_in_flat_market():
    """TC-DATA-030:极小窗口情况下计算产生NaN传播"""
    from app.services.volatility_service import calculate_metrics


    frame = pd.DataFrame(
        {
            "stock_code": ["000001"] * 5,
            "trade_date": pd.date_range(
                "2024-01-01",
                periods=5,
                freq="B"
            ),
            "open": [10] * 5,
            "high": [10] * 5,
            "low": [10] * 5,
            "close": [10] * 5,
        }
    )


    result = calculate_metrics(
        frame,
        window_size=1
    )


    assert not np.isnan(
        result["yzVolatility"]
    )



@pytest.mark.tc_data
def test_TC_DATA_031_garch_missing_parameter_fallback(monkeypatch):
    """TC-DATA-031: GARCH模型返回参数缺失"""
    from app.services import forecast_service


    class BrokenFitResult:

        convergence_flag = 0

        params = {}

        def forecast(self, horizon):
            return None



    class MockArch:

        def fit(self, disp="off"):
            return BrokenFitResult()



    monkeypatch.setattr(
        forecast_service,
        "arch_model",
        lambda *args, **kwargs: MockArch()
    )


    returns = pd.Series(
        np.random.normal(
            0,
            0.01,
            100
        )
    )


    sigma, model_name, _ = (
        forecast_service.fit_and_forecast_sigma(
            returns,
            20
        )
    )


    assert model_name == "EWMA"

    assert len(sigma) == 20



@pytest.mark.tc_data
def test_TC_DATA_032_invalid_confidence_level():
    """TC-DATA-032: 非法置信度输入"""
    from app.services.forecast_service import build_forecast_summary_v2


    target_dates = [
        pd.Timestamp("2024-02-01")
    ]

    sigma = np.array(
        [0.2]
    )


    with pytest.raises(Exception):

        build_forecast_summary_v2(
            target_dates,
            sigma,
            confidence_level=1.5
        )



@pytest.mark.tc_data
def test_TC_DATA_033_empty_donchian_summary():
    """TC-DATA-033: 唐奇安通道为空"""
    from app.services.volatility_service import (
        build_donchian_summary
    )


    empty_result = {}


    result = build_donchian_summary(
        empty_result
    )


    assert result is not None



@pytest.mark.tc_data
def test_TC_DATA_034_unsorted_market_data():
    """TC-DATA-034: 输入行情日期乱序"""

    from app.services.volatility_service import calculate_metrics


    dates = [
        pd.Timestamp("2024-01-05"),
        pd.Timestamp("2024-01-01"),
        pd.Timestamp("2024-01-03"),
    ]


    frame = pd.DataFrame(
        {
            "stock_code": [
                "000001"
            ] * 3,

            "trade_date": dates,

            "open": [
                10,
                10.2,
                10.4
            ],

            "high": [
                10.5,
                10.7,
                10.9
            ],

            "low": [
                9.8,
                10,
                10.2
            ],

            "close": [
                10.3,
                10.5,
                10.7
            ],
        }
    )


    result = calculate_metrics(
        frame,
        window_size=1
    )


    assert result is not None

    assert "trendSeries" in result

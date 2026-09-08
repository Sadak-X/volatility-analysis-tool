from __future__ import annotations

import json
import os
from datetime import date
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any


PROXY_ENV_KEYS = ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "ALL_PROXY", "all_proxy")
NO_PROXY_HOSTS = "push2his.eastmoney.com,push2.eastmoney.com,quote.eastmoney.com"
TRUE_ENV_VALUES = {"1", "true", "yes"}
USE_REAL_MARKET_ENV = "VOLASCOPE_USE_REAL_MARKET"


def disable_broken_market_proxy() -> None:
    if os.getenv("VOLASCOPE_USE_PROXY", "").lower() in TRUE_ENV_VALUES:
        return
    for key in PROXY_ENV_KEYS:
        os.environ.pop(key, None)
    existing_no_proxy = os.getenv("NO_PROXY") or os.getenv("no_proxy") or ""
    hosts = [host for host in [existing_no_proxy, NO_PROXY_HOSTS] if host]
    os.environ["NO_PROXY"] = ",".join(hosts)
    os.environ["no_proxy"] = os.environ["NO_PROXY"]


disable_broken_market_proxy()

import akshare as ak
import numpy as np
import pandas as pd


EXCEL_RENAME_MAP = {
    "stock_code": "stock_code",
    "股票代码": "stock_code",
    "trade_date": "trade_date",
    "交易日期": "trade_date",
    "日期": "trade_date",
    "open": "open",
    "开盘": "open",
    "high": "high",
    "最高": "high",
    "low": "low",
    "最低": "low",
    "close": "close",
    "收盘": "close",
    "volume": "volume",
    "成交量": "volume",
    "amount": "amount",
    "成交额": "amount",
    "source_name": "source_name",
}

STOCK_POOL_CACHE_FILE = Path(__file__).resolve().parent.parent / "core" / "stock_pool_cache.json"
STOCK_POOL_REFRESH_INTERVAL = timedelta(hours=12)
STOCK_POOL_RETRY_INTERVAL = timedelta(minutes=10)
_stock_pool_cache: list[dict[str, str]] | None = None
_stock_pool_cache_expire_at: datetime | None = None
_stock_pool_last_attempt_at: datetime | None = None

os.environ["VOLASCOPE_USE_REAL_MARKET"] = "1"
def load_market_frame(stock_code: str, start: date, end: date) -> pd.DataFrame:
    if not use_real_market():
        frame = generate_market_frame(stock_code, start, end)
        print(f"[market] {stock_code} source=mock rows={len(frame)}")
        return clean_market_frame(frame)

    try:
        frame = fetch_market_frame_from_akshare(stock_code, start, end)
        if frame.empty:
            raise ValueError(f"Real market source returned no rows for {stock_code}")
        source = frame.attrs.get("source", "akshare")
        print(f"[market] {stock_code} source={source} rows={len(frame)}")
        return clean_market_frame(frame)
    except Exception as exc:
        print(f"[market] {stock_code} source=real-market failed: {exc}")
        if not allow_mock_market():
            raise

    frame = generate_market_frame(stock_code, start, end)
    print(f"[market] {stock_code} source=mock rows={len(frame)}")
    return clean_market_frame(frame)


def use_real_market() -> bool:
    return os.getenv(USE_REAL_MARKET_ENV, "").lower() in TRUE_ENV_VALUES


def allow_mock_market() -> bool:
    return os.getenv("VOLASCOPE_ALLOW_MOCK_MARKET", "1").lower() in TRUE_ENV_VALUES


def fetch_market_frame_from_akshare(stock_code: str, start: date, end: date) -> pd.DataFrame:
    if market_type_for_code(stock_code) == "HK":
        frame = fetch_hk_market_frame_from_akshare(stock_code, start, end)
        frame.attrs["source"] = "akshare-hk"
        return frame

    normalized_code = normalize_stock_code(stock_code)
    try:
        frame = fetch_cn_market_frame_from_sina(normalized_code, start, end)
        frame.attrs["source"] = "akshare-sina"
        return frame
    except Exception as exc:
        print(f"[market] {stock_code} source=akshare-sina failed: {exc}")
        frame = fetch_cn_market_frame_from_eastmoney(normalized_code, start, end)
        frame.attrs["source"] = "akshare-eastmoney"
        return frame


def fetch_cn_market_frame_from_eastmoney(stock_code: str, start: date, end: date) -> pd.DataFrame:
    hist = ak.stock_zh_a_hist(
        symbol=stock_code,
        period="daily",
        start_date=start.strftime("%Y%m%d"),
        end_date=end.strftime("%Y%m%d"),
        adjust="",
    )
    if hist is None or hist.empty:
        raise ValueError(f"AKShare Eastmoney returned no rows for {stock_code}")

    return build_cn_market_frame_from_columns(
        hist,
        stock_code=stock_code,
        trade_date=0,
        open_price=2,
        close=3,
        high=4,
        low=5,
        volume=6,
        amount=7,
    )


def fetch_cn_market_frame_from_sina(stock_code: str, start: date, end: date) -> pd.DataFrame:
    hist = ak.stock_zh_a_daily(
        symbol=cn_sina_symbol(stock_code),
        start_date=start.strftime("%Y%m%d"),
        end_date=end.strftime("%Y%m%d"),
        adjust="",
    )
    if hist is None or hist.empty:
        raise ValueError(f"AKShare Sina returned no rows for {stock_code}")

    return build_cn_market_frame_from_columns(
        hist,
        stock_code=stock_code,
        trade_date="date",
        open_price="open",
        close="close",
        high="high",
        low="low",
        volume="volume",
        amount="amount" if "amount" in hist.columns else None,
    )


def build_cn_market_frame_from_columns(
    hist: pd.DataFrame,
    stock_code: str,
    trade_date: str | int,
    open_price: str | int,
    close: str | int,
    high: str | int,
    low: str | int,
    volume: str | int,
    amount: str | int | None,
) -> pd.DataFrame:
    frame = pd.DataFrame(
        {
            "trade_date": pd.to_datetime(column_values(hist, trade_date)),
            "open": pd.to_numeric(column_values(hist, open_price), errors="coerce"),
            "high": pd.to_numeric(column_values(hist, high), errors="coerce"),
            "low": pd.to_numeric(column_values(hist, low), errors="coerce"),
            "close": pd.to_numeric(column_values(hist, close), errors="coerce"),
            "volume": pd.to_numeric(column_values(hist, volume), errors="coerce"),
            "amount": pd.to_numeric(column_values(hist, amount), errors="coerce") if amount is not None else 0,
        }
    )
    frame["stock_code"] = stock_code
    frame["stock_name"] = get_stock_name(stock_code)
    frame["industry_name"] = DEFAULT_INDUSTRIES.get(stock_code, "未知行业")
    frame["market_type"] = "CN"
    return frame


def column_values(frame: pd.DataFrame, column: str | int) -> pd.Series:
    if isinstance(column, int):
        return frame.iloc[:, column]
    return frame[column]


def cn_sina_symbol(stock_code: str) -> str:
    normalized_code = normalize_stock_code(stock_code)
    if normalized_code.startswith("6"):
        return f"sh{normalized_code}"
    if normalized_code.startswith(("4", "8")):
        return f"bj{normalized_code}"
    return f"sz{normalized_code}"


def fetch_hk_market_frame_from_akshare(stock_code: str, start: date, end: date) -> pd.DataFrame:
    normalized_code = normalize_stock_code(stock_code)
    symbol = normalized_code.replace(".HK", "")
    hist = ak.stock_hk_hist(
        symbol=symbol,
        period="daily",
        start_date=start.strftime("%Y%m%d"),
        end_date=end.strftime("%Y%m%d"),
        adjust="",
    )
    if hist is None or hist.empty:
        raise ValueError(f"AKShare returned no rows for {normalized_code}")

    frame = pd.DataFrame(
        {
            "trade_date": pd.to_datetime(hist[pick_column(hist, ["日期", "date"])]),
            "open": pd.to_numeric(hist[pick_column(hist, ["开盘", "open"])], errors="coerce"),
            "high": pd.to_numeric(hist[pick_column(hist, ["最高", "high"])], errors="coerce"),
            "low": pd.to_numeric(hist[pick_column(hist, ["最低", "low"])], errors="coerce"),
            "close": pd.to_numeric(hist[pick_column(hist, ["收盘", "close"])], errors="coerce"),
            "volume": pd.to_numeric(hist[pick_column(hist, ["成交量", "volume"])], errors="coerce"),
            "amount": pd.to_numeric(hist[pick_column(hist, ["成交额", "amount"])], errors="coerce"),
        }
    )
    frame["stock_code"] = normalized_code
    frame["stock_name"] = get_stock_name(normalized_code)
    frame["industry_name"] = DEFAULT_INDUSTRIES.get(normalized_code, "港股")
    frame["market_type"] = "HK"
    return frame


def pick_column(frame: pd.DataFrame, candidates: list[str]) -> str:
    normalized_columns = {normalize_search_text(column): column for column in frame.columns}
    for candidate in candidates:
        normalized = normalize_search_text(candidate)
        if normalized in normalized_columns:
            return normalized_columns[normalized]
    raise KeyError(f"Missing column, expected one of: {', '.join(candidates)}")


def load_excel_result(file_path: str, fallback_stock_codes: list[str] | None = None) -> dict[str, Any]:
    source = Path(file_path)
    if not source.exists():
        raise FileNotFoundError(f"Excel file does not exist: {file_path}")

    raw = pd.read_excel(source)
    raw.columns = [str(column).strip() for column in raw.columns]
    raw = raw.rename(columns={column: EXCEL_RENAME_MAP.get(column, column) for column in raw.columns})
    raw = normalize_compact_price_excel(raw)

    fallback_code = fallback_stock_codes[0] if fallback_stock_codes else None
    normalized, errors = normalize_excel_rows(raw, fallback_code)

    error_file_path = None
    error_file_name = None
    if errors:
        error_file_path = write_error_file(source, errors)
        error_file_name = Path(error_file_path).name

    if normalized.empty:
        raise ValueError("Excel 中没有可用的有效数据，请下载错误明细修正后重试")

    cleaned = clean_market_frame(normalized)
    frames: list[pd.DataFrame] = []
    for stock_code, group in cleaned.groupby("stock_code"):
        stock_frame = group.copy().reset_index(drop=True)
        if stock_frame["stock_name"].isna().all():
            stock_frame["stock_name"] = get_stock_name(stock_code)
        frames.append(stock_frame)

    return {
        "frames": frames,
        "error_count": len(errors),
        "error_file_path": error_file_path,
        "error_file_name": error_file_name,
    }


def normalize_excel_rows(raw: pd.DataFrame, fallback_code: str | None) -> tuple[pd.DataFrame, list[dict[str, Any]]]:
    errors: list[dict[str, Any]] = []
    valid_rows: list[dict[str, Any]] = []

    required_columns = {"trade_date", "open", "high", "low", "close"}
    missing_columns = [column for column in required_columns if column not in raw.columns]
    if missing_columns:
        raise ValueError("Excel 缺少必填列: " + ", ".join(missing_columns))

    for index, row in raw.iterrows():
        row_no = index + 2
        stock_code = normalize_stock_code(first_present(row.get("stock_code"), fallback_code, "000001"))

        if not stock_code:
            errors.append(build_error(row_no, row, "CLN-01", "stock_code 为空"))
            continue

        trade_date = pd.to_datetime(row.get("trade_date"), errors="coerce")
        if pd.isna(trade_date):
            errors.append(build_error(row_no, row, "CLN-02", "trade_date 非法"))
            continue

        values = {}
        failed = False
        for column in ["open", "high", "low", "close", "volume", "amount"]:
            values[column] = pd.to_numeric(row.get(column), errors="coerce")

        if any(pd.isna(values[column]) for column in ["open", "high", "low", "close"]):
            errors.append(build_error(row_no, row, "CLN-03", "OHLC 存在缺失"))
            continue

        if any(values[column] <= 0 for column in ["open", "high", "low", "close"]):
            errors.append(build_error(row_no, row, "CLN-06", "价格小于等于 0"))
            continue

        high = max(values["high"], values["open"], values["close"])
        low = min(values["low"], values["open"], values["close"])
        if high < max(values["open"], values["close"]) or low > min(values["open"], values["close"]):
            errors.append(build_error(row_no, row, "CLN-04", "最高价/最低价异常"))
            failed = True

        if failed:
            continue

        valid_rows.append(
            {
                "stock_code": stock_code,
                "trade_date": trade_date,
                "open": float(values["open"]),
                "high": float(high),
                "low": float(low),
                "close": float(values["close"]),
                "volume": float(values["volume"]) if not pd.isna(values["volume"]) else 0.0,
                "amount": float(values["amount"]) if not pd.isna(values["amount"]) else 0.0,
                "stock_name": get_stock_name(stock_code),
                "industry_name": DEFAULT_INDUSTRIES.get(stock_code, "Excel导入"),
            }
        )

    normalized = pd.DataFrame(valid_rows)
    if not normalized.empty:
        normalized = normalized.drop_duplicates(subset=["stock_code", "trade_date"], keep="last")
    return normalized, errors


def normalize_compact_price_excel(raw: pd.DataFrame) -> pd.DataFrame:
    if {"trade_date", "open", "high", "low", "close"}.issubset(raw.columns):
        return raw
    if len(raw.columns) < 2:
        return raw

    date_column = find_date_like_column(raw)
    close_column = find_numeric_like_column(raw, exclude={date_column} if date_column else set())
    if date_column is None or close_column is None:
        return raw

    normalized = raw.copy()
    normalized["trade_date"] = normalized[date_column]
    normalized["close"] = normalized[close_column]
    normalized["open"] = normalized.get("open", normalized["close"])
    normalized["high"] = normalized.get("high", normalized["close"])
    normalized["low"] = normalized.get("low", normalized["close"])

    remaining_columns = [column for column in normalized.columns if column not in {date_column, close_column}]
    volume_column = find_numeric_like_column(normalized[remaining_columns], exclude=set()) if remaining_columns else None
    normalized["volume"] = normalized[volume_column] if volume_column else 0
    normalized["amount"] = pd.to_numeric(normalized["close"], errors="coerce") * pd.to_numeric(normalized["volume"], errors="coerce").fillna(0)
    return normalized


def find_date_like_column(raw: pd.DataFrame) -> Any | None:
    for column in raw.columns:
        values = pd.to_datetime(raw[column], errors="coerce")
        if values.notna().mean() >= 0.8:
            return column
    return None


def find_numeric_like_column(raw: pd.DataFrame, exclude: set[Any]) -> Any | None:
    for column in raw.columns:
        if column in exclude:
            continue
        values = pd.to_numeric(raw[column], errors="coerce")
        if values.notna().mean() >= 0.8:
            return column
    return None


def first_present(*values: Any) -> Any:
    for value in values:
        if value is not None and not pd.isna(value):
            return value
    return None


def write_error_file(source: Path, errors: list[dict[str, Any]]) -> str:
    error_dir = source.parent / "errors"
    error_dir.mkdir(parents=True, exist_ok=True)
    error_file = error_dir / f"{source.stem}-errors.xlsx"
    pd.DataFrame(errors).to_excel(error_file, index=False)
    return str(error_file.resolve())


def build_error(row_no: int, row: pd.Series, rule_code: str, message: str) -> dict[str, Any]:
    return {
        "row_no": row_no,
        "rule_code": rule_code,
        "error_message": message,
        "stock_code": row.get("stock_code"),
        "trade_date": row.get("trade_date"),
        "open": row.get("open"),
        "high": row.get("high"),
        "low": row.get("low"),
        "close": row.get("close"),
        "volume": row.get("volume"),
        "amount": row.get("amount"),
    }


def generate_market_frame(stock_code: str, start: date, end: date) -> pd.DataFrame:
    normalized_code = normalize_stock_code(stock_code)
    dates = pd.bdate_range(start=start, end=end)
    if dates.empty:
        raise ValueError(f"No business days in selected range: {start} ~ {end}")

    seed = sum(ord(char) for char in normalized_code)
    rng = np.random.default_rng(seed)
    base_price = 40 + (seed % 200)
    drift = rng.normal(0.0004, 0.0003, len(dates))
    shock = rng.normal(0, 0.018, len(dates))
    close = base_price * np.exp(np.cumsum(drift + shock))
    open_price = close * (1 + rng.normal(0, 0.008, len(dates)))
    high = np.maximum(open_price, close) * (1 + rng.uniform(0.001, 0.02, len(dates)))
    low = np.minimum(open_price, close) * (1 - rng.uniform(0.001, 0.02, len(dates)))
    volume = rng.integers(1_500_000, 8_500_000, len(dates))

    frame = pd.DataFrame(
        {
            "trade_date": dates.date,
            "open": open_price,
            "high": high,
            "low": low,
            "close": close,
            "volume": volume,
            "amount": volume * close,
        }
    )
    frame["stock_code"] = normalized_code
    frame["stock_name"] = get_stock_name(normalized_code)
    frame["industry_name"] = DEFAULT_INDUSTRIES.get(stock_code, "综合")
    frame["market_type"] = market_type_for_code(normalized_code)
    return frame


def clean_market_frame(frame: pd.DataFrame) -> pd.DataFrame:
    cleaned = frame.copy()
    cleaned["trade_date"] = pd.to_datetime(cleaned["trade_date"], errors="coerce")
    for column in ["open", "high", "low", "close", "volume", "amount"]:
        if column in cleaned.columns:
            cleaned[column] = pd.to_numeric(cleaned[column], errors="coerce")
    cleaned = cleaned.dropna(subset=["trade_date", "open", "high", "low", "close"])
    cleaned = cleaned[(cleaned["open"] > 0) & (cleaned["high"] > 0) & (cleaned["low"] > 0) & (cleaned["close"] > 0)]
    cleaned["high"] = cleaned[["high", "open", "close"]].max(axis=1)
    cleaned["low"] = cleaned[["low", "open", "close"]].min(axis=1)
    cleaned["stock_code"] = cleaned["stock_code"].map(normalize_stock_code)
    if "stock_name" not in cleaned.columns:
        cleaned["stock_name"] = cleaned["stock_code"].map(get_stock_name)
    if "industry_name" not in cleaned.columns:
        cleaned["industry_name"] = "未知行业"
    if "market_type" not in cleaned.columns:
        cleaned["market_type"] = cleaned["stock_code"].map(market_type_for_code)
    cleaned = cleaned.drop_duplicates(subset=["stock_code", "trade_date"], keep="last")
    cleaned = cleaned.sort_values(["stock_code", "trade_date"]).reset_index(drop=True)
    return cleaned


def ingest_items(frames: list[pd.DataFrame]) -> dict[str, Any]:
    items: list[dict[str, Any]] = []
    for frame in frames:
        latest = frame.iloc[-1]
        previous = frame.iloc[-2] if len(frame) > 1 else latest
        prev_close = float(previous["close"]) if float(previous["close"]) != 0 else float(latest["close"])
        change_rate = ((float(latest["close"]) - prev_close) / prev_close) * 100 if prev_close else 0.0
        items.append(
            {
                "stockCode": str(latest["stock_code"]),
                "stockName": str(latest["stock_name"]),
                "industryName": str(latest["industry_name"]),
                "marketType": str(latest.get("market_type", market_type_for_code(str(latest["stock_code"])))),
                "latestPrice": round(float(latest["close"]), 4),
                "changeRate": round(float(change_rate), 4),
                "volume": round(float(latest.get("volume", 0.0)), 2),
                "rowCount": int(len(frame)),
            }
        )
    return {"items": items}


def stock_pool_records() -> list[dict[str, str]]:
    global _stock_pool_cache
    global _stock_pool_cache_expire_at
    global _stock_pool_last_attempt_at

    now = datetime.now()
    if _stock_pool_cache and _stock_pool_cache_expire_at and now < _stock_pool_cache_expire_at:
        return _stock_pool_cache

    should_retry = _stock_pool_last_attempt_at is None or now - _stock_pool_last_attempt_at >= STOCK_POOL_RETRY_INTERVAL
    if should_retry:
        _stock_pool_last_attempt_at = now
        fresh_cache = fetch_stock_pool_from_akshare()
        if fresh_cache:
            _stock_pool_cache = fresh_cache
            _stock_pool_cache_expire_at = now + STOCK_POOL_REFRESH_INTERVAL
            write_stock_pool_cache(fresh_cache)
            return fresh_cache

    file_cache = read_stock_pool_cache()
    if file_cache:
        _stock_pool_cache = file_cache
        _stock_pool_cache_expire_at = now + STOCK_POOL_REFRESH_INTERVAL
        return file_cache

    fallback = default_stock_pool_records()
    _stock_pool_cache = fallback
    _stock_pool_cache_expire_at = now + STOCK_POOL_RETRY_INTERVAL
    return fallback


def stock_name_map() -> dict[str, str]:
    return {record["stockCode"]: record["stockName"] for record in stock_pool_records()}


def fetch_stock_pool_from_akshare() -> list[dict[str, str]]:
    records = []
    records.extend(fetch_cn_stock_pool_from_akshare())
    records.extend(fetch_hk_stock_pool_from_akshare())
    return merge_stock_records(records)


def fetch_cn_stock_pool_from_akshare() -> list[dict[str, str]]:
    try:
        frame = ak.stock_info_a_code_name()
    except Exception:
        return []
    if frame is None or frame.empty:
        return []
    return [
        {"stockCode": str(row["code"]).zfill(6), "stockName": str(row["name"]), "marketType": "CN"}
        for _, row in frame.iterrows()
    ]


def fetch_hk_stock_pool_from_akshare() -> list[dict[str, str]]:
    for fetcher in (ak.stock_hk_spot_em, ak.stock_hk_spot):
        try:
            frame = fetcher()
        except Exception:
            continue
        records = parse_hk_stock_pool_frame(frame)
        if records:
            return records
    return []


def parse_hk_stock_pool_frame(frame: pd.DataFrame | None) -> list[dict[str, str]]:
    if frame is None or frame.empty:
        return []
    try:
        code_column = pick_column(frame, ["代码", "证券代码", "symbol", "code"])
        name_column = pick_column(frame, ["名称", "证券简称", "name"])
    except KeyError:
        return []
    records = []
    for _, row in frame.iterrows():
        code = normalize_stock_code(row.get(code_column))
        if market_type_for_code(code) == "HK":
            records.append({"stockCode": code, "stockName": str(row.get(name_column)), "marketType": "HK"})
    return records


def read_stock_pool_cache() -> list[dict[str, str]]:
    if not STOCK_POOL_CACHE_FILE.exists():
        return []
    try:
        data = json.loads(STOCK_POOL_CACHE_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return []
    if isinstance(data, dict):
        return merge_stock_records(
            {"stockCode": normalize_stock_code(code), "stockName": str(name), "marketType": market_type_for_code(code)}
            for code, name in data.items()
        )
    if not isinstance(data, list):
        return []
    return merge_stock_records(data)


def write_stock_pool_cache(stock_records: list[dict[str, str]]) -> None:
    try:
        STOCK_POOL_CACHE_FILE.parent.mkdir(parents=True, exist_ok=True)
        STOCK_POOL_CACHE_FILE.write_text(
            json.dumps(stock_records, ensure_ascii=False, sort_keys=True),
            encoding="utf-8",
        )
    except OSError:
        pass


def search_stock_pool(keyword: str) -> list[dict[str, str]]:
    search = keyword.strip()
    normalized_search = normalize_search_text(search)
    quick_records = filter_stock_records(cached_stock_pool_records(), normalized_search)
    if normalized_search and quick_records:
        return quick_records[:20]
    if not normalized_search:
        return quick_records[:20]

    return filter_stock_records(stock_pool_records(), normalized_search)[:20]


def filter_stock_records(records: list[dict[str, str]], normalized_search: str) -> list[dict[str, str]]:
    return [
        record
        for record in records
        if not normalized_search
        or normalized_search in normalize_search_text(record["stockCode"])
        or normalized_search in normalize_search_text(record["stockName"])
        or normalized_search in normalize_search_text(record.get("marketType", ""))
    ]


def cached_stock_pool_records() -> list[dict[str, str]]:
    if _stock_pool_cache:
        return _stock_pool_cache
    file_cache = read_stock_pool_cache()
    if file_cache:
        return file_cache
    return default_stock_pool_records()


def merge_stock_records(records: Any) -> list[dict[str, str]]:
    merged: dict[str, dict[str, str]] = {}
    for record in records:
        if not isinstance(record, dict):
            continue
        code = normalize_stock_code(record.get("stockCode") or record.get("code"))
        name = str(record.get("stockName") or record.get("name") or code).strip()
        if not code or not name:
            continue
        merged[code] = {
            "stockCode": code,
            "stockName": name,
            "marketType": str(record.get("marketType") or market_type_for_code(code)),
        }
    for record in default_stock_pool_records():
        merged.setdefault(record["stockCode"], record)
    return list(merged.values())


def default_stock_pool_records() -> list[dict[str, str]]:
    return [
        {"stockCode": normalize_stock_code(code), "stockName": name, "marketType": market_type_for_code(code)}
        for code, name in DEFAULT_STOCK_NAMES.items()
    ]


def normalize_stock_code(value: Any) -> str:
    if isinstance(value, (int, np.integer)):
        return str(int(value)).zfill(6)
    if isinstance(value, (float, np.floating)) and float(value).is_integer():
        return str(int(value)).zfill(6)
    raw = str(value or "").strip().upper()
    if not raw:
        return ""
    compact = raw.replace(" ", "")
    if compact.endswith(".0") and compact[:-2].isdigit():
        compact = compact[:-2]
    if compact.endswith(".HK"):
        return f"{only_digits(compact[:-3]).zfill(5)}.HK"
    if compact.startswith("HK"):
        return f"{only_digits(compact).zfill(5)}.HK"
    digits = only_digits(compact)
    if len(digits) == 5:
        return f"{digits}.HK"
    if digits:
        return digits.zfill(6)
    return compact


def market_type_for_code(stock_code: Any) -> str:
    return "HK" if normalize_stock_code(stock_code).endswith(".HK") else "CN"


def only_digits(value: Any) -> str:
    return "".join(ch for ch in str(value) if ch.isdigit())


def normalize_search_text(value: str) -> str:
    return "".join(ch.lower() for ch in str(value) if ch.isalnum())


def get_stock_name(stock_code: str) -> str:
    code = normalize_stock_code(stock_code)
    if code in DEFAULT_STOCK_NAMES:
        return DEFAULT_STOCK_NAMES[code]
    for record in cached_stock_pool_records():
        if record["stockCode"] == code:
            return record["stockName"]
    return stock_name_map().get(code, f"股票{code}")


DEFAULT_STOCK_NAMES = {
    "600519": "贵州茅台",
    "002594": "比亚迪",
    "300750": "宁德时代",
    "601318": "中国平安",
    "000333": "美的集团",
    "01810.HK": "小米集团-W",
}

DEFAULT_INDUSTRIES = {
    "600519": "白酒",
    "002594": "新能源汽车",
    "300750": "动力电池",
    "601318": "保险",
    "000333": "家电",
    "01810.HK": "消费电子",
}

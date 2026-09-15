#!/usr/bin/env python3
"""
run_all_tests.py
================
volatility-analysis-tool 一键运行全部测试

  1. Python 单元测试  (pytest)     — python-service/test/
  2. 前端 E2E 测试    (Playwright) — frontend/tests/
  3. Java 后端测试    (JUnit)      — backend/  (Maven)

目录结构（固定）：
    volatility-analysis-tool/
    ├── run_all_tests.py           ← 本脚本
    ├── backend/                   Java 后端 (pom.xml)
    ├── frontend/                  Vue 前端 (package.json, playwright.config.ts, tests/)
    ├── python-service/            Python 数据分析 (conftest.py, app/, test/)
    └── sql/

用法：
    python run_all_tests.py                # 全部
    python run_all_tests.py --py           # 只跑 Python
    python run_all_tests.py --skip-fe      # 跳过前端
    python run_all_tests.py --fe --java    # 只跑前端 + Java

环境变量（可选覆盖）：
    PY_DIR / FE_DIR / JAVA_DIR
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent

# ---------------------------------------------------------------------------
# 目录定位：环境变量优先，其次默认目录名
# ---------------------------------------------------------------------------

def _pick_dir(env_var: str, default_name: str) -> Path:
    if env_var in os.environ:
        return Path(os.environ[env_var]).resolve()
    return (HERE / default_name).resolve()


PY_DIR   = _pick_dir("PY_DIR",   "python-service")
FE_DIR   = _pick_dir("FE_DIR",   "frontend")
JAVA_DIR = _pick_dir("JAVA_DIR", "backend")

# Python 测试实际所在的子目录候选（兼容 test/ 与 tests/）
PY_TEST_SUBDIRS = ["test", "tests"]

# 前端 Playwright 用例目录候选
FE_TEST_SUBDIRS = ["tests", "e2e", "test"]

# ---------------------------------------------------------------------------
# 工具
# ---------------------------------------------------------------------------

@dataclass
class Result:
    name: str
    code: int = 0
    seconds: float = 0.0
    skipped: bool = False
    reason: str = ""

    @property
    def ok(self) -> bool:
        return self.skipped or self.code == 0

    @property
    def icon(self) -> str:
        if self.skipped:
            return "\033[1;33m⏭ \033[0m"
        return "\033[1;32m✔ \033[0m" if self.code == 0 else "\033[1;31m✘ \033[0m"


def has(cmd: str) -> bool:
    if shutil.which(cmd):
        return True
    if os.name == "nt":
        for ext in (".cmd", ".exe", ".bat"):
            if shutil.which(cmd + ext):
                return True
    return False
def resolve_cmd(cmd: list[str]) -> list[str]:
    """Windows 下把 cmd[0] 换成带后缀的真实路径。"""
    if os.name != "nt":
        return cmd

    exe = cmd[0]

    # which 本身就会尝试 PATHEXT，直接用它返回的完整路径
    p = shutil.which(exe)
    if p:
        return [p] + cmd[1:]

    # 兜底：显式尝试常见后缀
    for ext in (".cmd", ".exe", ".bat"):
        p = shutil.which(exe + ext)
        if p:
            return [p] + cmd[1:]

    return cmd
def run(name: str, cmd: list[str], cwd: Path) -> Result:
    cmd = resolve_cmd(cmd)

    if os.name == "nt" and cmd[0].lower().endswith((".cmd", ".bat")):
        cmd = ["cmd.exe", "/c"] + cmd

    bar = "─" * 72
    print(f"\n\033[1;36m{bar}\033[0m")
    print(f"\033[1;36m▶ {name}\033[0m")
    print(f"  cwd : {cwd}")
    print(f"  cmd : {' '.join(cmd)}")
    print(f"\033[1;36m{bar}\033[0m")
    t0 = time.time()
    try:
        code = subprocess.run(cmd, cwd=str(cwd)).returncode
    except FileNotFoundError:
        print(f"\033[1;31m[!] 找不到可执行文件: {cmd[0]}\033[0m")
        code = 127
    return Result(name, code, time.time() - t0)

def skip(name: str, reason: str) -> Result:
    print(f"\n\033[1;33m⏭  跳过 {name}: {reason}\033[0m")
    return Result(name, skipped=True, reason=reason)


def first_existing(base: Path, names: list[str]) -> Path | None:
    for n in names:
        p = base / n
        if p.is_dir():
            return p
    return None

# ---------------------------------------------------------------------------
# 1) Python (pytest)
# ---------------------------------------------------------------------------

def run_python() -> Result:
    name = "Python (pytest)"
    if not PY_DIR.exists():
        return skip(name, f"目录不存在: {PY_DIR}")

    test_dir = first_existing(PY_DIR, PY_TEST_SUBDIRS) or PY_DIR
    if not list(test_dir.glob("test_*.py")):
        return skip(name, f"{test_dir} 下未找到 test_*.py")

    # 选择 pytest 入口
    if has("pytest"):
        base_cmd = ["pytest"]
    else:
        try:
            subprocess.run([sys.executable, "-m", "pytest", "--version"],
                           capture_output=True, check=True)
            base_cmd = [sys.executable, "-m", "pytest"]
        except Exception:
            return skip(name, "未安装 pytest (pip install pytest)")

    # 关键：从 PY_DIR 运行 pytest，让 conftest.py 生效，
    # 并把 app 包加入 sys.path（conftest.py 里已经处理）
    cmd = base_cmd + ["-v", "--tb=short", str(test_dir.relative_to(PY_DIR))]
    return run(name, cmd, PY_DIR)

# ---------------------------------------------------------------------------
# 2) 前端 (Playwright)
# ---------------------------------------------------------------------------

def run_frontend() -> Result:
    name = "前端 (Playwright)"
    if not FE_DIR.exists():
        return skip(name, f"目录不存在: {FE_DIR}")

    test_dir = first_existing(FE_DIR, FE_TEST_SUBDIRS)
    if test_dir is None or not list(test_dir.rglob("*.spec.ts")):
        return skip(name, f"{FE_DIR} 下未找到 *.spec.ts")

    if not (FE_DIR / "node_modules").exists():
        print(f"\033[1;33m[!] 未检测到 node_modules，请先执行: cd {FE_DIR} && npm install\033[0m")

    if has("npx"):
        cmd = ["npx", "playwright", "test"]
    elif has("pnpm"):
        cmd = ["pnpm", "exec", "playwright", "test"]
    elif has("yarn"):
        cmd = ["yarn", "playwright", "test"]
    else:
        return skip(name, "未找到 npx / pnpm / yarn")

    return run(name, cmd, FE_DIR)

# ---------------------------------------------------------------------------
# 3) Java (Maven / Gradle)
# ---------------------------------------------------------------------------

def run_java() -> Result:
    name = "Java (JUnit)"
    if not JAVA_DIR.exists():
        return skip(name, f"目录不存在: {JAVA_DIR}")

    if (JAVA_DIR / "pom.xml").exists():
        if not has("mvn"):
            return skip(name, "未安装 Maven (mvn)")
        # -B 批处理模式，避免刷屏；-q 只显示结果
        cmd = ["mvn", "-B", "test"]
    elif (JAVA_DIR / "gradlew").exists() or (JAVA_DIR / "gradlew.bat").exists():
        wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
        cmd = [wrapper, "test", "--no-daemon"]
    elif (JAVA_DIR / "build.gradle").exists() or (JAVA_DIR / "build.gradle.kts").exists():
        if not has("gradle"):
            return skip(name, "未安装 Gradle")
        cmd = ["gradle", "test", "--no-daemon"]
    else:
        return skip(name, f"未找到 pom.xml / build.gradle in {JAVA_DIR}")

    return run(name, cmd, JAVA_DIR)

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------

def print_summary(results: list[Result]) -> int:
    print("\n" + "═" * 72)
    print("                         测 试 汇 总")
    print("═" * 72)
    failed = 0
    for r in results:
        if not r.ok:
            failed += 1
        dur = f"{r.seconds:6.1f}s" if not r.skipped else "   --  "
        note = f"   ({r.reason})" if r.skipped else ""
        print(f"  {r.icon} {r.name:<24} {dur}  exit={r.code:>3}{note}")
    print("─" * 72)
    if failed == 0:
        print(f"\033[1;32m全部通过 ✔   (共 {len(results)} 个套件)\033[0m")
        return 0
    print(f"\033[1;31m存在失败 ✘   ({failed}/{len(results)} 个套件失败)\033[0m")
    return 1

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(description="volatility-analysis-tool 一键运行全部测试")
    ap.add_argument("--py",   action="store_true", help="仅运行 Python 测试")
    ap.add_argument("--fe",   action="store_true", help="仅运行前端测试")
    ap.add_argument("--java", action="store_true", help="仅运行 Java 测试")
    ap.add_argument("--skip-py",   action="store_true")
    ap.add_argument("--skip-fe",   action="store_true")
    ap.add_argument("--skip-java", action="store_true")
    args = ap.parse_args()

    # 选择套件
    if args.py or args.fe or args.java:
        selected = set()
        if args.py:
            selected.add("py")
        if args.fe:
            selected.add("fe")
        if args.java:
            selected.add("java")
    else:
        selected = {"py", "fe", "java"}

    if args.skip_py:
        selected.discard("py")
    if args.skip_fe:
        selected.discard("fe")
    if args.skip_java:
        selected.discard("java")

    if not selected:
        print("没有需要运行的测试套件。")
        return 0

    print(f"\033[1m待运行测试套件: {', '.join(sorted(selected))}\033[0m")
    print(f"  PY_DIR   = {PY_DIR}")
    print(f"  FE_DIR   = {FE_DIR}")
    print(f"  JAVA_DIR = {JAVA_DIR}")

    results: list[Result] = []
    t0 = time.time()
    if "py" in selected:
        results.append(run_python())
    if "fe" in selected:
        results.append(run_frontend())
    if "java" in selected:
        results.append(run_java())
    print(f"\n总耗时: {time.time() - t0:.1f}s")

    return print_summary(results)


if __name__ == "__main__":
    sys.exit(main())
# 股票波动率分析系统

一个覆盖数据导入、波动率计算、波动率评估、波动率预测全链路的一体化分析平台。项目由Python数据分析服务、Spring Boot业务后端、Vue 前端三部分组成，并配有完整的三层自动化测试体系。


## 一、项目结构

```
volatility-analysis-tool/
│
├── run_all_tests.py            # 三层测试一键运行脚本
├── README.md                   # 本文件
│
├── backend/                    # Java 业务后端（Spring Boot）
│   ├── pom.xml                 # Maven 配置
│   └── src/
│       ├── main/java/          # 生产代码
│       │   └── com/volatility/
│       │       ├── common/     # 通用工具、异常、安全
│       │       └── modules/    # 业务模块
│       │           ├── auth/         # 鉴权
│       │           ├── file/         # 文件上传
│       │           ├── task/         # 任务管理
│       │           ├── analysis/     # 波动率分析
│       │           ├── assessment/   # 风险评估
│       │           ├── forecast/     # 波动率预测
│       │           ├── stock/        # 股票基础信息
│       │           └── ai/           # AI 解读（DeepSeek）
│       ├── main/resources/     # application.yml 等配置
│       └── test/java/          # JUnit 5 + Mockito + AssertJ
│
├── frontend/                   # Vue 前端
│   ├── package.json
│   ├── playwright.config.ts    # Playwright 核心配置
│   ├── src/                    # 页面、组件、路由、接口
│   │   ├── views/              # 页面
│   │   ├── components/         # 组件（图表、卡片等）
│   │   ├── router/             # 路由
│   │   └── api/                # Axios 接口封装
│   └── tests/                  # Playwright E2E 用例
│       ├── fixtures/           # 测试用静态资源
│       │   ├── test_100.xlsx   # 100 行合法 Excel
│       │   └── dummy.txt       # 非 Excel 文件
│       ├── helpers.ts
│       ├── part1_basic.spec.ts     # TC-FE-001 ~ TC-FE-010
│       └── part2_advanced.spec.ts  # TC-FE-011 ~ TC-FE-018
│
├── python-service/             # Python 数据分析服务（FastAPI）
│   ├── requirements.txt
│   ├── pytest.ini
│   ├── conftest.py
│   ├── app/                    # 业务代码
│   │   ├── main.py             # FastAPI 入口
│   │   └── services/           # 核心服务
│   │       ├── market_service.py       # 行情/Excel 解析
│   │       ├── volatility_service.py   # YZ 波动率、隐含波动率
│   │       ├── assessment_service.py   # 风险评估
│   │       └── forecast_service.py     # GARCH / EWMA 预测
│   └── test/                   # pytest 单元测试
│       └── test_tc_data.py     # TC-DATA-001 ~ TC-DATA-034
│
└── sql/                        # 数据库初始化脚本
    └── init.sql                # 建库建表 + 初始数据
```

## 二、环境配置

### 2.1 工具版本要求

| 工具 | 建议版本  | 验证命令 |
|---|-------|---|
| JDK | 17    | `java -version` |
| Maven | 3.8+  | `mvn -version` |
| Node.js | 20+   | `node --version` |
| npm | 8+    | `npm --version` |
| Python | 3.10+ | `python --version` |
| MySQL | 8.0+  | `mysql --version` |

### 2.2 依赖安装（首次一次性）

**Python 服务**

```bash
cd python-service
python -m pip install -r requirements.txt
```

关键依赖：`fastapi`、`uvicorn`、`pandas`、`numpy`、`arch`、`pytest`

**前端**

```bash
cd frontend
npm install
npx playwright install chromium
```

**Java 后端**

```bash
cd backend
mvn -B dependency:resolve
```


### 2.3 环境变量配置

**DeepSeek API Key**（AI 解读功能依赖）

PowerShell：

```powershell
$env:DEEPSEEK_API_KEY = "你的 DeepSeek API Key"
```

若要持久化，可写入用户环境变量：

## 三、启动前准备

### 3.1 启动 MySQL

确保本机 MySQL 服务已启动。默认连接配置（见 `backend/src/main/resources/application.yml`）：

| 项 | 值 |
|---|---|
| Host | `localhost:3306` |
| 用户名 | `root` |
| 密码 | `123456` |
| 数据库 | `volatility` |

**默认账号**

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 管理员 |

## 四、启动项目

三个服务需分别启动，建议开三个终端窗口。

### 4.1 Python 分析服务

```bash
cd python-service
python -m uvicorn app.main:app --reload --port 9001
```

### 4.2 Java 业务后端

```bash
cd backend
mvn spring-boot:run
```


### 4.3 Vue 前端

```bash
cd frontend
npm run dev
```

## 五、测试运行

### 5.1 测试架构概览

| 层级 | 框架 | 用例数 | 覆盖范围 |
|---|---|---|---|
| Python | pytest | 34 | Excel 解析、YZ 波动率、GARCH、置信区间 |
| 前端 | Playwright | 18 | 登录、数据导入、任务管理、图表、AI 分析 |
| Java | JUnit 5 | 36 | 分析 / 评估 / 鉴权 / 文件 / 预测 各服务单测 |

### 5.2 一键运行全部测试

在项目根目录：

```powershell
python run_all_tests.py
```

**输出示例**

```
待运行测试套件: fe, java, py
  PY_DIR   = D:\test\volatility-analysis-tool\python-service
  FE_DIR   = D:\test\volatility-analysis-tool\frontend
  JAVA_DIR = D:\test\volatility-analysis-tool\backend

────────────────────────────────────────────────────────────────────────
▶ Python (pytest)
...
✔  Python (pytest)             4.9s  exit=  0
✔  前端 (Playwright)          11.4s  exit=  0
✔  Java (JUnit)               22.1s  exit=  0
────────────────────────────────────────────────────────────────────────
全部通过 ✔   (共 3 个套件)
```


#### 参数说明

| 参数 | 作用 |
|---|---|
| （无） | 运行全部三层 |
| `--py` / `--fe` / `--java` | 只跑某一层 |
| `--skip-py` / `--skip-fe` / `--skip-java` | 跳过某一层 |

**常用组合**

```powershell
python run_all_tests.py --py              # 只跑 Python
python run_all_tests.py --py --java       # 跳过前端
python run_all_tests.py --skip-fe         # 同上一行效果
```


### 5.3 Python 测试（pytest）

```bash
cd python-service
python -m pytest test -v
```

**覆盖内容（34 条）**

| 用例段 | 范围 |
|---|---|
| TC-DATA-001 ~ 007 | Excel 解析、字段校验、格式归一化 |
| TC-DATA-008 ~ 015 | YZ 波动率窗口计算、隐含波动率牛顿法 |
| TC-DATA-016 ~ 022 | 风险评估边界、趋势推断、唐奇安通道 |
| TC-DATA-023 ~ 028 | 预测日期序列、GARCH / EWMA 降级 |
| TC-DATA-029 ~ 034 | 边界与异常场景、乱序数据 |

### 5.4 前端测试（Playwright）

**必须先启动三层服务**

```bash
cd frontend

# UI 模式（推荐调试）
npx playwright test --ui

# 无头模式跑全部
npx playwright test

# 查看 HTML 报告
npx playwright show-report
```

**测试模块**

| 模块 | 用例范围 | 覆盖 |
|---|---|---|
| Part 1 基础 | TC-FE-001 ~ 010 | 登录与路由守卫、全局布局、仪表盘图表、Excel 上传与拦截 |
| Part 2 进阶 | TC-FE-011 ~ 018 | 任务列表状态与增删、评估与 AI 分析弹窗、预测弹窗、图表异常检测 |


### 5.5 Java 测试（JUnit）


```bash
cd backend
mvn -B test
```

单跑某测试类 / 某方法：

```bash
mvn -B test -Dtest=AnalysisServiceTest
mvn -B test -Dtest=AnalysisServiceTest#overviewReturnsEmptyAnalysisStateWhenCalcResultIsMissing
```

**测试类清单（36 条）**

| 测试类 | 覆盖模块 |
|---|---|
| `AnalysisServiceTest` | 波动率分析结果查询 |
| `AssessmentServiceTest` | 风险评估结果查询 |
| `AuthServiceTest` | 用户注册 / 登录 / 鉴权 |
| `FileStorageServiceTest` | Excel 文件上传、大小/格式校验 |
| `ForecastServiceTest` | 预测结果、批量股票聚合 |



## 六、常用命令
```powershell
# 启动项目（三个终端）
cd python-service && python -m uvicorn app.main:app --reload --port 9001
cd backend       && mvn spring-boot:run
cd frontend      && npm run dev

# 一键跑全部测试
python run_all_tests.py

# 只跑某层测试
python run_all_tests.py --py
python run_all_tests.py --java

# 股票波动率分析系统

## 环境配置（我现在配的）

JDK	17
Node.js	v24.20.0 应该20+都行
npm	11.19.0 应该8+都行
Python	3.13（或 3.10+）
MySQL	8.0

## 启动前准备

### 1. MySQL

确保本机已启动 MySQL，后端默认使用以下配置：

- 用户名：`root`
- 密码：`123456`
- 数据库：`volatility`

```

### 2. DeepSeek API Key

PowerShell 中先设置：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
```

## 启动方式

### 1. Python 分析服务

```bash
cd python-service
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 9001
```

### 2. Spring Boot 业务后端
  
  配maven


### 3. Vue 前端

```bash
cd frontend
npm install
npm run dev
```

## 默认账号

## 前端自动化测试 (Playwright)

本项目包含了“股票波动率分析工具”前端模块的自动化回归测试用例。测试基于Playwright框架，覆盖了登录鉴权、数据导入、任务增删、图表渲染、AI分析与预测详情等核心业务流程。

### 测试概览

目前包含18条自动化测试用例，分为两个主要测试模块：
Part 1 基础模块 (TC-FE-001 ~ TC-FE-010)：涵盖登录与路由守卫、全局布局、仪表盘图表渲染、Excel 文件上传与拦截等基础交互。
Part 2 进阶模块 (TC-FE-011 ~ TC-FE-018)：涵盖任务列表状态增删、评估与 AI 分析弹窗、波动率预测弹窗、图表数值异常检测。

测试相关的代码主要集中在 `frontend` 目录下，结构如下：

```text
frontend/
├── playwright.config.ts         # Playwright 核心配置文件
├── package.json                 # 项目依赖
├── tests/                       # 自动化测试用例根目录
│   ├── fixtures/                # 测试所需的静态资源文件
│   │   ├── test_100.xlsx        # 正常导入测试用的100行合法Excel数据
│   │   └── dummy.txt            # 用于测试非Excel文件格式拦截
│   ├── helpers.ts               # 公共工具方法
│   ├── part1_basic.spec.ts      # TC-FE-001 ~ TC-FE-010
│   └── part2_advanced.spec.ts   # TC-FE-011 ~ TC-FE-018
└── ...
 ```

## 环境配置
在运行自动化测试之前，请确保本地环境满足以下条件：

### 安装依赖：进入 frontend 目录，执行以下命令：
```bash
cd frontend
npm install
npx playwright install chromium
```

### 服务启动前置条件：自动化测试依赖真实的数据交互，执行测试前必须确保以下服务全部处于运行状态：
1.Python 后端服务 

2.Java 后端服务

3.前端本地服务 

## 测试运行方式
所有测试命令均需要在 frontend 目录下的终端中执行。

1.以 UI 模式运行：
```bash
npx playwright test --ui
```
2.以无头模式运行全部测试：
```bash
npx playwright test
```
3.仅运行指定的测试文件：
```bash
npx playwright test tests/part1_basic.spec.ts
```
或者
npx playwright test tests/part2_advanced.spec.ts
4.仅运行某一条测试用例
```bash
npx playwright test -g "TC-FE-018"
```
5.查看测试报告
```bash
npx playwright show-report
```


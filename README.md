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

- 用户名：`admin`
- 密码：`admin123`


CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname VARCHAR(64),
  status TINYINT NOT NULL DEFAULT 1,
  role_code VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS task_main (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_no VARCHAR(32) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  task_status VARCHAR(32) NOT NULL,
  stock_mode VARCHAR(16) NOT NULL,
  stock_count INT NOT NULL DEFAULT 1,
  progress INT NOT NULL DEFAULT 0,
  stock_code VARCHAR(16),
  stock_name VARCHAR(64),
  result_summary VARCHAR(500),
  start_time DATETIME,
  end_time DATETIME,
  error_msg VARCHAR(500),
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS task_param_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL UNIQUE,
  data_source_type VARCHAR(16) NOT NULL,
  stock_codes_json LONGTEXT NOT NULL,
  date_start DATE NOT NULL,
  date_end DATE NOT NULL,
  confidence_level DECIMAL(5,2) NOT NULL,
  time_granularity VARCHAR(8) NOT NULL,
  forecast_horizon VARCHAR(8) NOT NULL,
  param_json LONGTEXT NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS task_step_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  step_code VARCHAR(32) NOT NULL,
  step_status VARCHAR(32) NOT NULL,
  message_text VARCHAR(500),
  detail_json LONGTEXT,
  create_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS data_source_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_no VARCHAR(32) NOT NULL UNIQUE,
  original_file_name VARCHAR(255) NOT NULL,
  stored_file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  content_type VARCHAR(128),
  file_size BIGINT NOT NULL,
  file_sha256 VARCHAR(64) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_basic (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stock_code VARCHAR(16) NOT NULL UNIQUE,
  stock_name VARCHAR(64) NOT NULL,
  industry_name VARCHAR(64),
  market_type VARCHAR(16),
  latest_price DECIMAL(18,4),
  change_rate DECIMAL(10,4),
  volume DECIMAL(20,2),
  historical_volatility DECIMAL(12,6),
  implied_volatility DECIMAL(12,6),
  predicted_volatility DECIMAL(12,6),
  total_score DECIMAL(8,2),
  risk_level VARCHAR(16),
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS volatility_calc_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  stock_code VARCHAR(16) NOT NULL,
  calc_date DATE NOT NULL,
  yz_volatility DECIMAL(12,6),
  implied_volatility DECIMAL(12,6),
  window_size INT NOT NULL,
  calc_status VARCHAR(16) NOT NULL,
  result_json LONGTEXT,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS volatility_assess_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  stock_code VARCHAR(16) NOT NULL,
  score_total DECIMAL(8,2),
  score_stability DECIMAL(8,2),
  score_risk DECIMAL(8,2),
  risk_level VARCHAR(16),
  qualitative_label VARCHAR(64),
  industry_avg_vol DECIMAL(12,6),
  donchian_json LONGTEXT,
  result_json LONGTEXT,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS volatility_forecast_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  stock_code VARCHAR(16) NOT NULL,
  forecast_type VARCHAR(8) NOT NULL,
  predict_volatility DECIMAL(12,6),
  ci_lower DECIMAL(12,6),
  ci_upper DECIMAL(12,6),
  ser_value DECIMAL(12,6),
  risk_level VARCHAR(16),
  model_name VARCHAR(64),
  result_json LONGTEXT,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS volatility_forecast_detail (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  forecast_result_id BIGINT NOT NULL,
  forecast_date DATE NOT NULL,
  pred_value DECIMAL(12,6),
  ci_lower DECIMAL(12,6),
  ci_upper DECIMAL(12,6),
  ser_value DECIMAL(12,6),
  rank_no INT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_analysis_report (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  stock_code VARCHAR(16) NOT NULL,
  analysis_mode VARCHAR(16) NOT NULL,
  prompt_hash VARCHAR(64) NOT NULL,
  input_json LONGTEXT,
  summary_text LONGTEXT,
  full_report_md LONGTEXT,
  risk_disclaimer VARCHAR(500),
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_volatility_rank_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  biz_date DATE NOT NULL,
  sort_scope VARCHAR(16) NOT NULL,
  stock_code VARCHAR(16) NOT NULL,
  stock_name VARCHAR(64) NOT NULL,
  pred_volatility DECIMAL(12,6),
  change_rate DECIMAL(10,4),
  last_price DECIMAL(18,4),
  risk_level VARCHAR(16),
  rank_no INT NOT NULL,
  snapshot_time DATETIME NOT NULL
);
CREATE TABLE IF NOT EXISTS ai_conclusion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(32) NOT NULL,
    input_hash VARCHAR(64) NOT NULL UNIQUE,
    insight_json LONGTEXT NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
    );

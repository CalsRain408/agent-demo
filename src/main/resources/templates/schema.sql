-- documents 表：存储上传文件的元数据
CREATE TABLE IF NOT EXISTS documents (
    id           VARCHAR(36)  PRIMARY KEY,
    library_name VARCHAR(255) NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    file_type    VARCHAR(50),
    file_size    BIGINT,
    upload_time  TIMESTAMP,
    description  TEXT,
    chunk_count  INTEGER,
    content_hash VARCHAR(32)
);

-- pipeline_configs 表：存储各 Handler 的可热更新配置
CREATE TABLE IF NOT EXISTS pipeline_configs (
    handler_name         VARCHAR(50) PRIMARY KEY,
    model                VARCHAR(100),
    temperature          DOUBLE PRECISION,
    max_tokens           INTEGER,
    system_prompt        TEXT,
    top_k                INTEGER,
    similarity_threshold DOUBLE PRECISION,
    updated_at           TIMESTAMP
);

-- 迁移补丁：对已存在的 documents 表补加 content_hash 列（幂等）
ALTER TABLE documents ADD COLUMN IF NOT EXISTS content_hash VARCHAR(32);

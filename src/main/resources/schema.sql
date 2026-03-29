-- libraries 表：知识库，名称全局唯一
CREATE TABLE IF NOT EXISTS libraries (
    id   VARCHAR(36)  PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

-- documents 表：上传文件元数据，通过 library_id 关联知识库
CREATE TABLE IF NOT EXISTS documents (
    id           VARCHAR(36)  PRIMARY KEY,
    library_id   VARCHAR(36)  NOT NULL REFERENCES libraries(id),
    filename     VARCHAR(255) NOT NULL,
    file_type    VARCHAR(50),
    file_size    BIGINT,
    upload_time  TIMESTAMP,
    description  TEXT,
    chunk_count  INTEGER,
    content_hash VARCHAR(32)
);

-- pipeline_configs 表：各 Handler 的可热更新配置
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

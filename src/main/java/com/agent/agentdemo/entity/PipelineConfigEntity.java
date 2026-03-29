package com.agent.agentdemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 流水线配置的持久化实体，一行对应一个 Handler 的全部可配置项。
 *
 * handlerName 取值：
 *   analysis  — AnalysisHandler（HandlerConfig 字段有效）
 *   response  — ResponseHandler（HandlerConfig 字段有效）
 *   retrieval — ToolCallHandler（RetrievalConfig 字段有效）
 *
 * 两类 Handler 共用一张表，无关字段留 null。
 */
@TableName("pipeline_configs")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PipelineConfigEntity {

    @TableId(value = "handler_name", type = IdType.INPUT)
    private String handlerName;

    // ── HandlerConfig ─────────────────────────────
    private String model;

    private Double temperature;

    private Integer maxTokens;

    private String systemPrompt;

    // ── RetrievalConfig ───────────────────────────
    private Integer topK;

    private Double similarityThreshold;

    // ── 审计 ──────────────────────────────────────
    private LocalDateTime updatedAt;
}

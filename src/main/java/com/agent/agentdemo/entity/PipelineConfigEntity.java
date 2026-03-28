package com.agent.agentdemo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
@Entity
@Table(name = "pipeline_configs")
@Getter
@Setter
@NoArgsConstructor
public class PipelineConfigEntity {

    @Id
    @Column(name = "handler_name", length = 50)
    private String handlerName;

    // ── HandlerConfig ─────────────────────────────
    @Column(length = 100)
    private String model;

    private Double temperature;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    // ── RetrievalConfig ───────────────────────────
    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "similarity_threshold")
    private Double similarityThreshold;

    // ── 审计 ──────────────────────────────────────
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

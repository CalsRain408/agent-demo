package com.agent.agentdemo.config.pipeline;

import lombok.Data;

/**
 * 向量检索阶段的配置，对应 YAML 中 agent.pipeline.retrieval 节点。
 */
@Data
public class RetrievalConfig {

    /** 返回最相似的 TopK 个 chunk */
    private int topK = 5;

    /**
     * cosine 相似度阈值，低于此值的结果将被过滤掉。
     * 设为 0.0 表示不过滤（由 topK 决定），建议生产环境设为 0.6～0.75。
     */
    private double similarityThreshold = 0.65;
}

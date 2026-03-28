package com.agent.agentdemo.controller;

import com.agent.agentdemo.config.pipeline.DatabasePipelineConfigProvider;
import com.agent.agentdemo.config.pipeline.HandlerConfig;
import com.agent.agentdemo.config.pipeline.RetrievalConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pipeline 配置管理接口，支持运行时查看和热更新，无需重启服务。
 *
 * <pre>
 * GET  /api/admin/pipeline-configs            — 查看所有 Handler 的当前配置
 * PUT  /api/admin/pipeline-configs/analysis   — 更新 AnalysisHandler 配置（立即生效）
 * PUT  /api/admin/pipeline-configs/response   — 更新 ResponseHandler 配置（立即生效）
 * PUT  /api/admin/pipeline-configs/retrieval  — 更新 ToolCallHandler 检索配置（立即生效）
 * POST /api/admin/pipeline-configs/reload     — 从 DB 全量重载（用于 DBA 手工改表后同步）
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/pipeline-configs")
public class PipelineConfigController {

    private final DatabasePipelineConfigProvider configProvider;

    public PipelineConfigController(DatabasePipelineConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /** 查看所有 Handler 的当前内存缓存配置 */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        return ResponseEntity.ok(Map.of(
                "analysis",  configProvider.getAnalysisConfig(),
                "response",  configProvider.getResponseConfig(),
                "retrieval", configProvider.getRetrievalConfig()
        ));
    }

    /** 更新 AnalysisHandler 的模型参数和 system prompt，立即生效 */
    @PutMapping("/analysis")
    public ResponseEntity<HandlerConfig> updateAnalysis(@RequestBody HandlerConfig config) {
        return ResponseEntity.ok(configProvider.updateAnalysis(config));
    }

    /** 更新 ResponseHandler 的模型参数和 system prompt 模板，立即生效 */
    @PutMapping("/response")
    public ResponseEntity<HandlerConfig> updateResponse(@RequestBody HandlerConfig config) {
        return ResponseEntity.ok(configProvider.updateResponse(config));
    }

    /** 更新 ToolCallHandler 的检索参数（topK / similarityThreshold），立即生效 */
    @PutMapping("/retrieval")
    public ResponseEntity<RetrievalConfig> updateRetrieval(@RequestBody RetrievalConfig config) {
        return ResponseEntity.ok(configProvider.updateRetrieval(config));
    }

    /** 从 DB 全量重载到内存缓存，用于 DBA 手工修改数据库后同步应用状态 */
    @PostMapping("/reload")
    public ResponseEntity<Void> reload() {
        configProvider.reload();
        return ResponseEntity.ok().build();
    }
}

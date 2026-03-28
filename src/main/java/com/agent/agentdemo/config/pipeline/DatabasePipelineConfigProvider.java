package com.agent.agentdemo.config.pipeline;

import com.agent.agentdemo.entity.PipelineConfigEntity;
import com.agent.agentdemo.repository.PipelineConfigRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 基于 PostgreSQL 的 Pipeline 配置实现。
 *
 * <p>生命周期：
 * <ol>
 *   <li>应用启动时，若 {@code pipeline_configs} 表为空，将 YAML 默认值种入 DB</li>
 *   <li>将 DB 中的配置加载到内存缓存（volatile 字段），Handler 直接读缓存，零 DB 查询</li>
 *   <li>通过 {@link #update} 系列方法更新时，同步写 DB 并刷新缓存，立即生效</li>
 *   <li>{@link #reload} 方法可从 DB 全量重载，用于外部直接修改 DB 后同步缓存</li>
 * </ol>
 *
 * <p>标记 {@code @Primary}，Spring 在注入 {@link PipelineConfigProvider} 时优先选择本类。
 */
@Slf4j
@Primary
@Component
public class DatabasePipelineConfigProvider implements PipelineConfigProvider {

    public static final String ANALYSIS  = "analysis";
    public static final String RESPONSE  = "response";
    public static final String RETRIEVAL = "retrieval";

    @Resource
    private PipelineConfigRepository repository;

    @Resource
    private DefaultPipelineConfigProvider yamlDefaults;

    // volatile 保证跨线程可见性；写操作发生在管理接口调用处（通常低频），无需锁
    private volatile HandlerConfig   analysisConfig;
    private volatile HandlerConfig   responseConfig;
    private volatile RetrievalConfig retrievalConfig;

    @PostConstruct
    void init() {
        seedDefaults();
        reload();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PipelineConfigProvider 接口实现（读缓存，零 DB 查询）
    // ─────────────────────────────────────────────────────────────────────────

    @Override public HandlerConfig   getAnalysisConfig()  { return analysisConfig; }
    @Override public HandlerConfig   getResponseConfig()  { return responseConfig; }
    @Override public RetrievalConfig getRetrievalConfig() { return retrievalConfig; }

    // ─────────────────────────────────────────────────────────────────────────
    // 更新接口（写 DB + 刷新缓存，立即生效）
    // ─────────────────────────────────────────────────────────────────────────

    public HandlerConfig updateAnalysis(HandlerConfig config) {
        repository.save(toEntity(ANALYSIS, config));
        analysisConfig = config;
        log.info("Pipeline config updated: handler=analysis model={}", config.getModel());
        return analysisConfig;
    }

    public HandlerConfig updateResponse(HandlerConfig config) {
        repository.save(toEntity(RESPONSE, config));
        responseConfig = config;
        log.info("Pipeline config updated: handler=response model={}", config.getModel());
        return responseConfig;
    }

    public RetrievalConfig updateRetrieval(RetrievalConfig config) {
        repository.save(toEntity(RETRIEVAL, config));
        retrievalConfig = config;
        log.info("Pipeline config updated: handler=retrieval topK={} threshold={}",
                config.getTopK(), config.getSimilarityThreshold());
        return retrievalConfig;
    }

    /**
     * 从 DB 全量重载配置到内存缓存。
     * 用于外部直接修改 DB 后（如 DBA 手工改表）通知应用同步。
     */
    public void reload() {
        analysisConfig  = loadHandlerConfig(ANALYSIS,  yamlDefaults.getAnalysisConfig());
        responseConfig  = loadHandlerConfig(RESPONSE,  yamlDefaults.getResponseConfig());
        retrievalConfig = loadRetrievalConfig(RETRIEVAL, yamlDefaults.getRetrievalConfig());
        log.info("Pipeline configs reloaded from DB");
    }


    /** 若 DB 中不存在对应行，将 YAML 默认值写入，保证首次启动无需手动初始化 */
    private void seedDefaults() {
        if (!repository.existsById(ANALYSIS)) {
            repository.save(toEntity(ANALYSIS, yamlDefaults.getAnalysisConfig()));
            log.info("Pipeline config seeded from YAML defaults: handler=analysis");
        }
        if (!repository.existsById(RESPONSE)) {
            repository.save(toEntity(RESPONSE, yamlDefaults.getResponseConfig()));
            log.info("Pipeline config seeded from YAML defaults: handler=response");
        }
        if (!repository.existsById(RETRIEVAL)) {
            repository.save(toEntity(RETRIEVAL, yamlDefaults.getRetrievalConfig()));
            log.info("Pipeline config seeded from YAML defaults: handler=retrieval");
        }
    }

    private HandlerConfig loadHandlerConfig(String name, HandlerConfig fallback) {
        return repository.findById(name)
                .map(e -> {
                    HandlerConfig cfg = new HandlerConfig();
                    cfg.setModel(e.getModel());
                    cfg.setTemperature(e.getTemperature());
                    cfg.setMaxTokens(e.getMaxTokens());
                    cfg.setSystemPrompt(e.getSystemPrompt());
                    return cfg;
                })
                .orElse(fallback);
    }

    private RetrievalConfig loadRetrievalConfig(String name, RetrievalConfig fallback) {
        return repository.findById(name)
                .map(e -> {
                    RetrievalConfig cfg = new RetrievalConfig();
                    if (e.getTopK()               != null) cfg.setTopK(e.getTopK());
                    if (e.getSimilarityThreshold() != null) cfg.setSimilarityThreshold(e.getSimilarityThreshold());
                    return cfg;
                })
                .orElse(fallback);
    }

    private PipelineConfigEntity toEntity(String name, HandlerConfig cfg) {
        PipelineConfigEntity e = new PipelineConfigEntity();
        e.setHandlerName(name);
        e.setModel(cfg.getModel());
        e.setTemperature(cfg.getTemperature());
        e.setMaxTokens(cfg.getMaxTokens());
        e.setSystemPrompt(cfg.getSystemPrompt());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    private PipelineConfigEntity toEntity(String name, RetrievalConfig cfg) {
        PipelineConfigEntity e = new PipelineConfigEntity();
        e.setHandlerName(name);
        e.setTopK(cfg.getTopK());
        e.setSimilarityThreshold(cfg.getSimilarityThreshold());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }
}

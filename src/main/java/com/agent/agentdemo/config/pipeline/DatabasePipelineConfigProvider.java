package com.agent.agentdemo.config.pipeline;

import com.agent.agentdemo.entity.PipelineConfigEntity;
import com.agent.agentdemo.mapper.PipelineConfigMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 基于 PostgreSQL + Redis 的 Pipeline 配置实现。
 *
 * <h3>缓存注解语义</h3>
 * <ul>
 *   <li>{@code @Cacheable}  — 读时优先命中 Redis；Miss 时查 DB 并回填缓存</li>
 *   <li>{@code @CachePut}   — 写 DB 的同时更新 Redis，下次读直接命中新值</li>
 *   <li>{@code @CacheEvict} — 驱逐缓存，下次读时重新从 DB 加载（用于手动同步）</li>
 * </ul>
 *
 * <h3>多实例一致性</h3>
 * 所有实例共享同一个 Redis，任意实例通过 {@code @CachePut} 更新后，
 * 其他实例下次读取即可感知到新配置，无需广播。
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DatabasePipelineConfigProvider implements PipelineConfigProvider {

    static final String CACHE_NAME = "pipeline-configs";
    static final String ANALYSIS   = "analysis";
    static final String RESPONSE   = "response";
    static final String RETRIEVAL  = "retrieval";

    private final PipelineConfigMapper         pipelineConfigMapper;
    private final DefaultPipelineConfigProvider yamlDefaults;

    // ─────────────────────────────────────────────────────────────────────────
    // 启动初始化：将 YAML 默认值种入 DB（仅当 DB 中不存在时执行一次）
    // ─────────────────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        seedIfAbsent(ANALYSIS,  toEntity(ANALYSIS,  yamlDefaults.getAnalysisConfig()));
        seedIfAbsent(RESPONSE,  toEntity(RESPONSE,  yamlDefaults.getResponseConfig()));
        seedIfAbsent(RETRIEVAL, toEntity(RETRIEVAL, yamlDefaults.getRetrievalConfig()));
    }

    private void seedIfAbsent(String name, PipelineConfigEntity entity) {
        if (pipelineConfigMapper.selectById(name) == null) {
            pipelineConfigMapper.insert(entity);
            log.info("Pipeline config seeded from YAML defaults: handler={}", name);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 读：@Cacheable — Redis 命中则直接返回，Miss 则查 DB 并回填
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'" + ANALYSIS + "'")
    public HandlerConfig getAnalysisConfig() {
        log.debug("Cache miss: loading analysis config from DB");
        PipelineConfigEntity e = pipelineConfigMapper.selectById(ANALYSIS);
        return toHandlerConfig(e != null ? e : toEntity(ANALYSIS, yamlDefaults.getAnalysisConfig()));
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'" + RESPONSE + "'")
    public HandlerConfig getResponseConfig() {
        log.debug("Cache miss: loading response config from DB");
        PipelineConfigEntity e = pipelineConfigMapper.selectById(RESPONSE);
        return toHandlerConfig(e != null ? e : toEntity(RESPONSE, yamlDefaults.getResponseConfig()));
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "'" + RETRIEVAL + "'")
    public RetrievalConfig getRetrievalConfig() {
        log.debug("Cache miss: loading retrieval config from DB");
        PipelineConfigEntity e = pipelineConfigMapper.selectById(RETRIEVAL);
        return toRetrievalConfig(e != null ? e : toEntity(RETRIEVAL, yamlDefaults.getRetrievalConfig()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 写：持久化到 DB，同步删除 Redis
    // ─────────────────────────────────────────────────────────────────────────

    @CacheEvict(cacheNames = CACHE_NAME, key = "'" + ANALYSIS + "'")
    public HandlerConfig updateAnalysis(HandlerConfig config) {
        pipelineConfigMapper.updateById(toEntity(ANALYSIS, config));
        log.info("Pipeline config updated: handler=analysis model={}", config.getModel());
        return config;
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "'" + RESPONSE + "'")
    public HandlerConfig updateResponse(HandlerConfig config) {
        pipelineConfigMapper.updateById(toEntity(RESPONSE, config));
        log.info("Pipeline config updated: handler=response model={}", config.getModel());
        return config;
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "'" + RETRIEVAL + "'")
    public RetrievalConfig updateRetrieval(RetrievalConfig config) {
        pipelineConfigMapper.updateById(toEntity(RETRIEVAL, config));
        log.info("Pipeline config updated: handler=retrieval topK={} threshold={}",
                config.getTopK(), config.getSimilarityThreshold());
        return config;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 强制重载：@CacheEvict — 驱逐所有缓存项，下次读时从 DB 重新加载
    // 用于 DBA 直接改库后通知应用同步
    // ─────────────────────────────────────────────────────────────────────────

    @CacheEvict(cacheNames = CACHE_NAME, allEntries = true)
    public void reload() {
        log.info("Pipeline configs cache evicted. Will reload from DB on next access.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity <-> Config 转换（私有，不走代理，不能加缓存注解）
    // ─────────────────────────────────────────────────────────────────────────

    private HandlerConfig toHandlerConfig(PipelineConfigEntity e) {
        HandlerConfig cfg = new HandlerConfig();
        cfg.setModel(e.getModel());
        cfg.setTemperature(e.getTemperature());
        cfg.setMaxTokens(e.getMaxTokens());
        cfg.setSystemPrompt(e.getSystemPrompt());
        return cfg;
    }

    private RetrievalConfig toRetrievalConfig(PipelineConfigEntity e) {
        RetrievalConfig cfg = new RetrievalConfig();
        if (e.getTopK()                != null) cfg.setTopK(e.getTopK());
        if (e.getSimilarityThreshold() != null) cfg.setSimilarityThreshold(e.getSimilarityThreshold());
        return cfg;
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

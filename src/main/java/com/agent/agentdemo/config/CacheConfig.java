package com.agent.agentdemo.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * 全局缓存配置，启用 Spring Cache 并指定 Redis 作为实现层。
 *
 * <p>序列化策略：GenericJackson2JsonRedisSerializer 将缓存值存为 JSON，
 * 并在 JSON 中写入 {@code @class} 类型字段，反序列化时按类型还原，
 * 不依赖 Java 原生序列化，可读性好，且对版本升级更友好。
 *
 * <p>TTL 为 24 小时——Pipeline 配置变更通过 {@code @CachePut / @CacheEvict} 立即同步，
 * TTL 仅作为兜底防止 Redis 中遗留的脏数据永久存活。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                );
    }
}

package com.shopmanagement.crmservice.meter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Optional Redis INCR for monthly API meters. Enabled with {@code crm.meters.redis-enabled=true}.
 * Fail-soft: any Redis error falls back to {@link DbApiCallCounter}.
 */
@Component
@Primary
@ConditionalOnProperty(name = "crm.meters.redis-enabled", havingValue = "true")
public class RedisApiCallCounter implements ApiCallCounter {

  private static final Logger log = LoggerFactory.getLogger(RedisApiCallCounter.class);

  private final DbApiCallCounter dbFallback;
  private final String host;
  private final int port;

  private LettuceConnectionFactory factory;
  private StringRedisTemplate redis;
  private volatile boolean available;

  public RedisApiCallCounter(
      DbApiCallCounter dbFallback,
      @Value("${crm.meters.redis-host:localhost}") String host,
      @Value("${crm.meters.redis-port:6379}") int port) {
    this.dbFallback = dbFallback;
    this.host = host;
    this.port = port;
  }

  @PostConstruct
  void connect() {
    try {
      RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration(host, port);
      factory = new LettuceConnectionFactory(conf);
      factory.afterPropertiesSet();
      redis = new StringRedisTemplate(factory);
      redis.afterPropertiesSet();
      redis.opsForValue().get("crm:meter:ping");
      available = true;
      log.info("CRM Redis API meters connected at {}:{}", host, port);
    } catch (Exception ex) {
      available = false;
      log.warn(
          "CRM Redis meters unavailable ({}:{}); using DB counters: {}",
          host,
          port,
          ex.getMessage());
      destroyQuietly();
    }
  }

  @PreDestroy
  void destroyQuietly() {
    try {
      if (factory != null) {
        factory.destroy();
      }
    } catch (Exception ignored) {
      // fail-soft shutdown
    }
    factory = null;
    redis = null;
    available = false;
  }

  @Override
  public long increment(String tenantId, String periodKey) {
    if (!available || redis == null) {
      return dbFallback.increment(tenantId, periodKey);
    }
    try {
      Long next = redis.opsForValue().increment(key(tenantId, periodKey));
      return next == null ? dbFallback.increment(tenantId, periodKey) : next;
    } catch (RuntimeException ex) {
      log.warn("Redis API meter INCR failed, falling back to DB: {}", ex.getMessage());
      available = false;
      return dbFallback.increment(tenantId, periodKey);
    }
  }

  @Override
  public long get(String tenantId, String periodKey) {
    if (!available || redis == null) {
      return dbFallback.get(tenantId, periodKey);
    }
    try {
      String v = redis.opsForValue().get(key(tenantId, periodKey));
      if (v != null && !v.isBlank()) {
        return Long.parseLong(v.trim());
      }
      return dbFallback.get(tenantId, periodKey);
    } catch (RuntimeException ex) {
      log.warn("Redis API meter GET failed, falling back to DB: {}", ex.getMessage());
      available = false;
      return dbFallback.get(tenantId, periodKey);
    }
  }

  private static String key(String tenantId, String periodKey) {
    return "crm:meter:api:" + tenantId + ":" + periodKey;
  }
}

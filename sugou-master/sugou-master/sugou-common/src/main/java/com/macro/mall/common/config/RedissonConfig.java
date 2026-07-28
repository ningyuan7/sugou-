package com.macro.mall.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 * 提供看门狗（Watch Dog）自动续期机制，解决锁超时和锁过期问题
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;

        // 看门狗默认锁超时时间：30秒（业务执行期间每10秒自动续期）
        config.setLockWatchdogTimeout(30000);

        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(password.isEmpty() ? null : password)
                // 连接池配置
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8)
                .setConnectTimeout(10000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}

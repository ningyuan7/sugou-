package com.macro.mall.common.service.impl;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁工具类
 * 基于 Redisson 的 RLock 实现，支持看门狗自动续期，解决锁超时和锁过期问题
 */
@Component
public class RedissonLockServiceImpl {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 尝试获取锁（带看门狗自动续期）
     * 不指定 leaseTime 时，看门狗每 10 秒自动续期 30 秒，业务完成后自动释放
     *
     * @param lockKey   锁的 key
     * @param waitTime  最大等待时间
     * @param timeUnit  时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 尝试获取锁（指定锁过期时间，不使用看门狗）
     *
     * @param lockKey   锁的 key
     * @param waitTime  最大等待时间
     * @param leaseTime 锁的持有时间（超过自动释放）
     * @param timeUnit  时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放锁（只有当前线程持有才释放）
     *
     * @param lockKey 锁的 key
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 在锁保护下执行业务逻辑（看门狗自动续期）
     *
     * @param lockKey  锁的 key
     * @param waitTime 最大等待时间
     * @param timeUnit 时间单位
     * @param supplier 业务逻辑
     * @return 业务执行结果，获取锁失败返回 null
     */
    public <T> T executeWithLock(String lockKey, long waitTime, TimeUnit timeUnit, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, timeUnit)) {
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 在锁保护下执行无返回值的业务逻辑（看门狗自动续期）
     *
     * @param lockKey  锁的 key
     * @param waitTime 最大等待时间
     * @param timeUnit 时间单位
     * @param runnable 业务逻辑
     * @return 是否成功获取锁并执行
     */
    public boolean executeWithLock(String lockKey, long waitTime, TimeUnit timeUnit, Runnable runnable) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, timeUnit)) {
                try {
                    runnable.run();
                    return true;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

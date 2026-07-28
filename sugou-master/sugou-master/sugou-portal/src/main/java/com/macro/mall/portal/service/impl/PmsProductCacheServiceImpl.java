package com.macro.mall.portal.service.impl;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.common.service.impl.RedissonLockServiceImpl;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.PmsProductExample;
import com.macro.mall.portal.service.PmsProductCacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PmsProductCacheServiceImpl implements  PmsProductCacheService{

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedissonLockServiceImpl redissonLockService;

    @Autowired
    private PmsProductMapper pmsProductMapper;

    //redis配置
    @Value("${redis.database}")
    private String REDIS_DATABASE;

    @Value("${redis.key.product}")
    private String REDIS_KEY_PRODUCT;

    @Value("${redis.expire.product}")
    private String REDIS_EXPIRE_PRODUCT;

    // ==================== 布隆过滤器 ====================

    /**
     * 布隆过滤器实例
     * 用于快速判断商品ID是否存在，防止缓存穿透
     */
    private BloomFilter<String> bloomFilter;

    // 预期商品数量：100万
    private static final int EXPECTED_INSERTIONS = 1000000;

    // 误判率：0.01%
    private static final double FPP = 0.0001;

    // ==================== Redis Key前缀定义 ====================

    /**
     * 商品缓存Key前缀
     * 格式：mall:product:{productId}
     */
    private static final String PRODUCT_CACHE_KEY = "mall:product:";

    /**
     * 分布式锁Key前缀
     * 格式：lock:product:{productId}
     */
    private static final String PRODUCT_LOCK_KEY = "lock:product:";

    /**
     * 空值缓存Key前缀
     * 格式：mall:product:null:{productId}
     * 用于缓存不存在的数据，防止反复查询数据库
     */
    private static final String PRODUCT_NULL_KEY = "mall:product:null:";

    /**
     * 随机偏移量最大值（秒）
     * 用于打散过期时间，防止雪崩
     */
    private static final int RANDOM_OFFSET_MAX = 300;

    /**
     * 空值缓存过期时间（秒）
     * 5分钟
     */
    private static final long NULL_CACHE_EXPIRE = 300;

    /**
     * 重试等待时间（毫秒）
     */
    private static final int RETRY_WAIT_MS = 50;

    /**
     * 热点商品预热数量
     */
    private static final int HOT_PRODUCT_COUNT = 100;


    @PostConstruct
    public void initBloomFilter(){
        log.info("============开始初始化布隆过滤器==========");

        bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(Charset.defaultCharset()),
                EXPECTED_INSERTIONS,
                FPP
        );

        PmsProductExample example = new PmsProductExample();
        example.createCriteria().andDeleteStatusEqualTo(0);
        List<PmsProduct> products = pmsProductMapper.selectByExample(example);

        for(PmsProduct pmsProduct: products){
            bloomFilter.put(String.valueOf(pmsProduct.getId()));
        }
        log.info("布隆过滤器初始化完成，共加载 {} 个商品ID，预期容量：{}，误判率：{}",
                products.size(), EXPECTED_INSERTIONS, FPP);
        log.info("========== 布隆过滤器初始化完成 ==========");
    }


    public void run(ApplicationArguments args)throws Exception{
        log.info("开始预热商品缓存");
        preloadHotProducts();
        log.info("预热完成");
    }

    @Override
    public PmsProduct getProductById(Long productId) {
        //使用布隆过滤器检查商品id是否存在，若不存在就返回null；
        if(!mightContainProduct(productId)){
            return null;
        }

        String cacheKey = PRODUCT_CACHE_KEY + productId;
        String nullKey = PRODUCT_NULL_KEY + productId;
        String lockKey = PRODUCT_LOCK_KEY + productId;
        //检查空值缓存是否存在，若存在直接返回null
        if(redisService.hasKey(nullKey)){
            return null;
        }

        PmsProduct product =(PmsProduct) redisService.get(cacheKey);

        //检查redis缓存是否存在，若存在就返回product
        if(product!=null){
            return product;
        }

        //若不存在则先上分布式锁，去db里面查询（Redisson看门狗自动续期）
        boolean locked = redissonLockService.tryLock(lockKey, RETRY_WAIT_MS, TimeUnit.MILLISECONDS);
        if(locked) {
            try {
                //双重检查，可能其他线程已写入缓存
                product = (PmsProduct) redisService.get(cacheKey);
                if (product != null) {
                    return product;
                }
                product = pmsProductMapper.selectByPrimaryKey(productId);
                //再存入缓存
                if (product != null && product.getId() != 0) {

                    long expireTime = Long.parseLong(REDIS_EXPIRE_PRODUCT) + new Random().nextInt(RANDOM_OFFSET_MAX);

                    redisService.set(cacheKey, product, expireTime);

                }else{
                    redisService.set(nullKey,"",NULL_CACHE_EXPIRE);
                }

            } finally {
                redissonLockService.unlock(lockKey);
            }
        }else{
            //获取锁失败，短暂等待后递归重试
            try{
                Thread.sleep(RETRY_WAIT_MS);
            }catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
            return getProductById(productId);
        }

        return product;
    }

    @Override
    public void delProductCache(Long productId) {

        String cacheKey = PRODUCT_CACHE_KEY + productId;
        String nullKey = PRODUCT_NULL_KEY + productId;

        redisService.del(cacheKey);
        redisService.del(nullKey);

    }

    @Override
    public void preloadHotProducts() {
        log.info("开始查询热点商品");

        //把未删除以及已上架的商品sql构建出来，按销量排名
        PmsProductExample example = new PmsProductExample();
        example.createCriteria()
                .andDeleteStatusEqualTo(0)
                .andPublishStatusEqualTo(1);
        example.setOrderByClause("sale desc");

        //查询
        List<PmsProduct> hotProducts = pmsProductMapper.selectByExample(example);

        //只预热前N个热点商品
        int preloadCount = Math.min(HOT_PRODUCT_COUNT, hotProducts.size());

        //逐个预热到redis
        int successCount = 0;
        for(int i=0;i<preloadCount;i++){
            PmsProduct product = hotProducts.get(i);
            String key = PRODUCT_CACHE_KEY + product.getId();
            //此处要看看
            long expireTime = Long.parseLong(REDIS_EXPIRE_PRODUCT) + new Random().nextInt(RANDOM_OFFSET_MAX);

            redisService.set(key,product,expireTime);
            successCount++;

        }
        log.info("预热成功{}个商品", successCount);

    }

    @Override
    public boolean mightContainProduct(Long productId) {
        boolean exists = bloomFilter.mightContain(String.valueOf(productId));
        if(!exists){
            log.debug("布隆过滤器拦截：productId={}",productId);
        }

        return exists;
    }
}

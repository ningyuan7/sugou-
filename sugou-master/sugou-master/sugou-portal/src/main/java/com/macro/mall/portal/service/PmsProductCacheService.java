package com.macro.mall.portal.service;

import com.macro.mall.model.PmsProduct;


public interface PmsProductCacheService {
    //利用产品id得到缓存中得到商品信息
    PmsProduct getProductById(Long productId);
    //删除商品缓存
    void delProductCache(Long productId);
    //预热商品缓存
    void preloadHotProducts();
    //验证商品是否存在于缓存
    boolean mightContainProduct(Long productId);

}

package com.macro.mall.portal.service;

/**
 * AI Agent 数据查询服务接口
 * 供 Python LangChain Agent 通过网关调用
 */
public interface AiAgentService {

    /** 查询商品SKU库存 */
    String queryStock(Long productId);

    /** 查询用户订单 */
    String queryOrder(Long memberId, Integer status);

    /** 查询可领取优惠券 */
    String listCoupons(Long memberId);

    /** 查询商品分类 */
    String listCategories(Long parentId);

    /** 商品推荐 */
    String recommendProducts(String type);

    /** 商品详情 */
    String getProductDetail(Long productId);
}
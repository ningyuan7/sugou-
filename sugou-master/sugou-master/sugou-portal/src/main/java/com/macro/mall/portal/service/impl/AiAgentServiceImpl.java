package com.macro.mall.portal.service.impl;

import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.PmsProductCategoryMapper;
import com.macro.mall.mapper.PmsProductMapper;
import com.macro.mall.mapper.PmsSkuStockMapper;
import com.macro.mall.mapper.SmsCouponMapper;
import com.macro.mall.model.*;
import com.macro.mall.portal.service.AiAgentService;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * AI Agent 数据查询服务实现
 */
@Slf4j
@Service
public class AiAgentServiceImpl implements AiAgentService {

    @Autowired(required = false)
    private PmsSkuStockMapper skuStockMapper;

    @Autowired(required = false)
    private OmsOrderMapper orderMapper;

    @Autowired(required = false)
    private PmsProductMapper productMapper;

    @Autowired(required = false)
    private PmsProductCategoryMapper categoryMapper;

    @Autowired(required = false)
    private SmsCouponMapper couponMapper;

    @Override
    public String queryStock(Long productId) {
        if (skuStockMapper == null) return "库存服务暂不可用";
        PmsSkuStockExample example = new PmsSkuStockExample();
        example.createCriteria().andProductIdEqualTo(productId);
        List<PmsSkuStock> stocks = skuStockMapper.selectByExample(example);
        if (stocks.isEmpty()) return "该商品暂无库存信息";
        StringBuilder sb = new StringBuilder("商品库存信息：\n");
        for (PmsSkuStock stock : stocks) {
            sb.append("  - SKU: ").append(stock.getSkuCode())
              .append(" 规格: ").append(stock.getSpData())
              .append(" 价格: ¥").append(stock.getPrice())
              .append(" 库存: ").append(stock.getStock())
              .append(" 锁定: ").append(stock.getLockStock()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String queryOrder(Long memberId, Integer status) {
        if (orderMapper == null) return "订单服务暂不可用";
        OmsOrderExample example = new OmsOrderExample();
        OmsOrderExample.Criteria criteria = example.createCriteria();
        criteria.andMemberIdEqualTo(memberId);
        if (status != null) criteria.andStatusEqualTo(status);
        example.setOrderByClause("create_time desc");
        PageHelper.startPage(1, 5);
        List<OmsOrder> orders = orderMapper.selectByExample(example);
        if (orders.isEmpty()) return "暂无订单信息";
        StringBuilder sb = new StringBuilder("订单信息：\n");
        for (OmsOrder order : orders) {
            String statusText = switch (order.getStatus()) {
                case 0 -> "待付款"; case 1 -> "待发货"; case 2 -> "已发货";
                case 3 -> "已完成"; case 4 -> "已关闭"; default -> "未知";
            };
            sb.append("  - 订单号: ").append(order.getOrderSn())
              .append(" 金额: ¥").append(order.getPayAmount())
              .append(" 状态: ").append(statusText)
              .append(" 时间: ").append(order.getCreateTime()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String listCoupons(Long memberId) {
        if (couponMapper == null) return "优惠券服务暂不可用";
        SmsCouponExample example = new SmsCouponExample();
        example.createCriteria().andEndTimeGreaterThan(new Date());
        example.setOrderByClause("amount desc");
        List<SmsCoupon> coupons = couponMapper.selectByExample(example);
        if (coupons.isEmpty()) return "当前暂无可用优惠券";
        StringBuilder sb = new StringBuilder("当前可领取的优惠券：\n");
        for (SmsCoupon c : coupons) {
            String typeText = switch (c.getType() != null ? c.getType() : -1) {
                case 0 -> "全场赠券"; case 1 -> "会员赠券";
                case 2 -> "购物赠券"; case 3 -> "注册赠券"; default -> "通用券";
            };
            String threshold = (c.getMinPoint() != null && c.getMinPoint().compareTo(BigDecimal.ZERO) > 0)
                    ? "满¥" + c.getMinPoint() + "可用" : "无门槛";
            sb.append("  - ").append(c.getName())
              .append(" 优惠¥").append(c.getAmount())
              .append("（").append(threshold)
              .append(" 类型: ").append(typeText)
              .append(" 有效期至: ").append(c.getEndTime()).append("）\n");
        }
        return sb.toString();
    }

    @Override
    public String listCategories(Long parentId) {
        if (categoryMapper == null) return "分类服务暂不可用";
        PmsProductCategoryExample example = new PmsProductCategoryExample();
        example.createCriteria().andParentIdEqualTo(parentId).andShowStatusEqualTo(1);
        example.setOrderByClause("sort desc");
        List<PmsProductCategory> categories = categoryMapper.selectByExample(example);
        if (categories.isEmpty()) {
            return parentId == 0 ? "暂无商品分类" : "该分类下暂无子分类";
        }
        String levelText = parentId == 0 ? "一级" : "二级";
        StringBuilder sb = new StringBuilder("商品").append(levelText).append("分类：\n");
        for (PmsProductCategory cat : categories) {
            sb.append("  - ID: ").append(cat.getId())
              .append(" 名称: ").append(cat.getName())
              .append(" 商品数: ").append(cat.getProductCount() != null ? cat.getProductCount() : 0).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String recommendProducts(String type) {
        if (productMapper == null) return "商品服务暂不可用";
        PmsProductExample example = new PmsProductExample();
        PmsProductExample.Criteria criteria = example.createCriteria();
        criteria.andDeleteStatusEqualTo(0).andPublishStatusEqualTo(1);
        String title;
        switch (type) {
            case "new" -> { criteria.andNewStatusEqualTo(1); example.setOrderByClause("id desc"); title = "新品推荐"; }
            case "promotion" -> { criteria.andPromotionTypeGreaterThan(0); example.setOrderByClause("promotion_price asc"); title = "促销商品"; }
            default -> { example.setOrderByClause("sale desc"); title = "热销排行"; }
        }
        PageHelper.startPage(1, 5);
        List<PmsProduct> products = productMapper.selectByExample(example);
        if (products.isEmpty()) return "暂无" + title + "商品";
        StringBuilder sb = new StringBuilder(title + "：\n");
        for (int i = 0; i < products.size(); i++) {
            PmsProduct p = products.get(i);
            sb.append(i + 1).append(". ").append(p.getName()).append("\n");
            sb.append("   - 价格: ¥").append(p.getPrice()).append("\n");
            sb.append("   - 销量: ").append(p.getSale()).append(" 件\n");
            sb.append("   - 库存: ").append(p.getStock()).append(" 件\n");
        }
        return sb.toString();
    }

    @Override
    public String getProductDetail(Long productId) {
        if (productMapper == null) return "商品服务暂不可用";
        PmsProduct product = productMapper.selectByPrimaryKey(productId);
        if (product == null || product.getDeleteStatus() == 1) return "未找到该商品";
        StringBuilder sb = new StringBuilder();
        sb.append("商品详细信息：\n");
        sb.append("  名称: ").append(product.getName()).append("\n");
        sb.append("  品牌: ").append(nvl(product.getBrandName())).append("\n");
        sb.append("  分类: ").append(nvl(product.getProductCategoryName())).append("\n");
        sb.append("  售价: ¥").append(product.getPrice()).append("\n");
        sb.append("  销量: ").append(product.getSale()).append(" 件\n");
        sb.append("  库存: ").append(product.getStock()).append(" 件\n");
        return sb.toString();
    }

    private String nvl(Object obj) { return obj == null ? "未知" : String.valueOf(obj); }
}
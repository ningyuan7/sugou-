package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.service.AiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * AI Agent 数据查询接口
 * 供 Python LangChain Agent 通过网关调用
 * 业务逻辑委托给 AiAgentService
 */
@Slf4j
@Controller
@Tag(name = "AiAgentController", description = "AI Agent 数据查询接口")
@RequestMapping("/ai/agent")
public class AiAgentController {

    @Autowired
    private AiAgentService aiAgentService;

    @Operation(summary = "查询商品库存")
    @RequestMapping(value = "/stock", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> queryStock(@RequestParam Long productId) {
        return CommonResult.success(aiAgentService.queryStock(productId));
    }

    @Operation(summary = "查询订单")
    @RequestMapping(value = "/order", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> queryOrder(@RequestParam Long memberId,
                                           @RequestParam(required = false) Integer status) {
        return CommonResult.success(aiAgentService.queryOrder(memberId, status));
    }

    @Operation(summary = "查询优惠券")
    @RequestMapping(value = "/coupons", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> listCoupons(@RequestParam(required = false) Long memberId) {
        return CommonResult.success(aiAgentService.listCoupons(memberId));
    }

    @Operation(summary = "查询商品分类")
    @RequestMapping(value = "/categories", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> listCategories(@RequestParam(defaultValue = "0") Long parentId) {
        return CommonResult.success(aiAgentService.listCategories(parentId));
    }

    @Operation(summary = "商品推荐")
    @RequestMapping(value = "/recommend", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> recommendProducts(@RequestParam(defaultValue = "hot") String type) {
        return CommonResult.success(aiAgentService.recommendProducts(type));
    }

    @Operation(summary = "商品详情")
    @RequestMapping(value = "/product", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<String> getProductDetail(@RequestParam Long productId) {
        return CommonResult.success(aiAgentService.getProductDetail(productId));
    }
}
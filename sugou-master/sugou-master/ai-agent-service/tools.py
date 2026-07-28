"""
LangChain 工具函数
通过 Gateway 调 sugou-portal 已有 REST API 获取数据
"""
from __future__ import annotations

import json
from typing import Optional, Type

import httpx
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field

from config import GATEWAY_BASE_URL


def _get(url: str, params: dict | None = None) -> str:
    """GET 请求 Gateway -> 返回 data 字段字符串"""
    try:
        with httpx.Client(timeout=15.0) as client:
            resp = client.get(f"{GATEWAY_BASE_URL}{url}", params=params)
            resp.raise_for_status()
            body = resp.json()
            data = body.get("data", body)
            if isinstance(data, str):
                return data
            return json.dumps(data, ensure_ascii=False, default=str)
    except Exception as e:
        return f"查询失败: {e}"


# ======================== Tool 参数 ========================

class ProductDetailInput(BaseModel):
    product_id: int = Field(description="商品ID")

class SearchProductInput(BaseModel):
    keyword: str = Field(description="搜索关键词", default="")
    brand: str | None = Field(description="品牌名称", default=None)
    category: str | None = Field(description="商品分类名称", default=None)

class OrderQueryInput(BaseModel):
    member_id: int = Field(description="用户ID")
    status: int | None = Field(description="订单状态 0待付款/1待发货/2已发货/3已完成", default=None)

class RecommendInput(BaseModel):
    rec_type: str = Field(description="推荐类型 hot/new/promotion", default="hot")


# ======================== 工具实现 ========================

class ProductDetailTool(BaseTool):
    name: str = "product_detail"
    description: str = "根据商品ID查询商品详细信息，包括价格、库存、描述、品牌等"
    args_schema: Type[BaseModel] = ProductDetailInput

    def _run(self, product_id: int) -> str:
        return _get("/api/ai/agent/product", params={"productId": product_id})


class SearchProductTool(BaseTool):
    name: str = "search_product"
    description: str = "搜索商品，可按关键词、品牌、分类筛选"
    args_schema: Type[BaseModel] = SearchProductInput

    def _run(self, keyword: str = "", brand: str | None = None, category: str | None = None) -> str:
        params = {"keyword": keyword}
        if brand:
            params["brandName"] = brand
        if category:
            params["categoryName"] = category
        return _get("/api/product/search", params=params)


class QueryOrderTool(BaseTool):
    name: str = "query_order"
    description: str = "查询用户的订单状态，可指定订单状态筛选"
    args_schema: Type[BaseModel] = OrderQueryInput

    def _run(self, member_id: int, status: int | None = None) -> str:
        params = {"memberId": member_id}
        if status is not None:
            params["status"] = status
        return _get("/api/ai/agent/order", params=params)


class RecommendTool(BaseTool):
    name: str = "recommend_products"
    description: str = "智能推荐商品，支持 hot(热销)/new(新品)/promotion(促销)"
    args_schema: Type[BaseModel] = RecommendInput

    def _run(self, rec_type: str = "hot") -> str:
        if rec_type == "new":
            return _get("/api/ai/agent/recommend", params={"type": "new"})
        elif rec_type == "promotion":
            return _get("/api/ai/agent/recommend", params={"type": "promotion"})
        return _get("/api/ai/agent/recommend", params={"type": "hot"})


class QueryStockTool(BaseTool):
    name: str = "query_stock"
    description: str = "查询指定商品的实时SKU库存信息，包括SKU规格、价格和库存数量"
    args_schema: Type[BaseModel] = ProductDetailInput

    def _run(self, product_id: int) -> str:
        # 调 Java 侧的 /ai/data/stock/{productId}（由 AiDataController 提供）
        return _get("/api/ai/agent/stock", params={"productId": product_id})


class ListCategoriesTool(BaseTool):
    name: str = "list_categories"
    description: str = "查询商品分类树，parentId=0查一级分类"
    args_schema: Type[BaseModel] = None

    def _run(self) -> str:
        return _get("/api/ai/agent/categories")


def get_all_tools() -> list[BaseTool]:
    return [
        ProductDetailTool(),
        SearchProductTool(),
        QueryOrderTool(),
        RecommendTool(),
        QueryStockTool(),
        ListCategoriesTool(),
    ]

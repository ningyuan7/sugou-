"""LangChain Agent 工具：异步调用 Java 业务接口"""
import httpx
from langchain.tools import tool

from config import JAVA_API_BASE
from agent import current_auth_header


def _auth_headers() -> dict:
    auth = current_auth_header.get()
    return {"Authorization": auth} if auth else {}


async def _handle(resp: httpx.Response, fallback: str) -> str:
    body = resp.json() if resp.text else {}
    if resp.status_code == 401:
        return "未登录，请先登录后再查询"
    if resp.status_code == 200:
        return body.get("data", fallback) or fallback
    msg = body.get("message", "") if isinstance(body, dict) else str(body)
    return msg or f"查询失败: HTTP {resp.status_code}"


@tool
async def search_products(query: str) -> str:
    """
    搜索商品。用于查找、推荐、对比商品。
    参数 query 为搜索关键词，如：手机、羽绒服。
    """
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            resp = await c.get(f"{JAVA_API_BASE}/ai/product/nlp/search", params={"query": query})
            return await _handle(resp, "未找到相关商品")
    except httpx.TimeoutException:
        return "商品搜索超时，请重试"
    except Exception as e:
        return f"商品搜索异常: {str(e)}"


@tool
async def query_order_status(status: int = None) -> str:
    """
    查询当前登录用户的订单。
    参数 status 可选：0=待付款 1=待发货 2=已发货 3=已完成 4=已关闭。不传则查全部。
    """
    try:
        params = {}
        if status is not None:
            params["status"] = status
        async with httpx.AsyncClient(timeout=10) as c:
            resp = await c.get(
                f"{JAVA_API_BASE}/ai/agent/order",
                params=params,
                headers=_auth_headers(),
            )
            return await _handle(resp, "暂无订单")
    except httpx.TimeoutException:
        return "订单查询超时，请重试"
    except Exception as e:
        return f"订单查询异常: {str(e)}"


@tool
async def query_stock(product_id: int) -> str:
    """
    查询商品库存。参数 product_id 为商品ID（整数）。
    """
    try:
        async with httpx.AsyncClient(timeout=10) as c:
            resp = await c.get(
                f"{JAVA_API_BASE}/ai/agent/stock",
                params={"productId": product_id},
                headers=_auth_headers(),
            )
            return await _handle(resp, "暂无库存信息")
    except httpx.TimeoutException:
        return "库存查询超时，请重试"
    except Exception as e:
        return f"库存查询异常: {str(e)}"


AGENT_TOOLS = [search_products, query_order_status, query_stock]

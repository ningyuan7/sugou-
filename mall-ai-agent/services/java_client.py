"""
Java 后端 API 调用客户端
封装所有对 mall-portal 的 HTTP 请求，统一处理 CommonResult 响应格式
Java 返回格式: {"code": 200, "message": "操作成功", "data": "..."}
"""
import httpx
from typing import Optional
from config import JAVA_API_BASE


class JavaApiClient:
    """Java 后端 HTTP 客户端"""

    def __init__(self, base_url: str = JAVA_API_BASE, timeout: int = 15):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    # ==================== 商品搜索 ====================

    def search_products_by_keyword(self, query: str) -> str:
        """
        GET /ai/product/nlp/search?query=手机
        自然语言商品搜索，Java 侧自动提取关键词并返回格式化文本
        """
        return self._get("/ai/product/nlp/search", params={"query": query})

    def search_products_structured(
        self,
        keyword: str,
        brand_name: Optional[str] = None,
        category_name: Optional[str] = None,
        price_min: Optional[float] = None,
        price_max: Optional[float] = None,
        sort: int = 2,
    ) -> str:
        """
        POST /ai/product/search
        结构化商品搜索，支持品牌/分类/价格区间过滤
        sort: 1=新品 2=销量 3=价格升序 4=价格降序
        """
        body = {
            "keyword": keyword,
            "sort": sort,
        }
        if brand_name:
            body["brandName"] = brand_name
        if category_name:
            body["categoryName"] = category_name
        if price_min is not None:
            body["priceMin"] = price_min
        if price_max is not None:
            body["priceMax"] = price_max

        return self._post("/ai/product/search", json_data=body)

    # ==================== 库存查询 ====================

    def query_stock(self, product_id: int) -> str:
        """
        GET /ai/agent/stock?productId=123
        查询指定商品库存
        """
        return self._get("/ai/agent/stock", params={"productId": product_id})

    # ==================== 订单查询 ====================

    def query_order(self, member_id: int = 0, status: Optional[int] = None) -> str:
        """
        GET /ai/agent/order?memberId=0&status=1
        查询用户订单
        member_id: 用户ID（0=匿名）
        status: 0待付款 1待发货 2已发货 3已完成 4已关闭
        """
        params = {"memberId": member_id}
        if status is not None:
            params["status"] = status
        return self._get("/ai/agent/order", params=params)

    # ==================== 底层 HTTP 方法 ====================

    def _get(self, path: str, params: dict = None) -> str:
        """GET 请求，自动解析 CommonResult"""
        url = f"{self.base_url}{path}"
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.get(url, params=params or {})
                return self._parse_response(resp)
        except httpx.ConnectError:
            return "[错误] 无法连接 Java 后端，请确认 mall-portal 已启动 (端口8085)"
        except httpx.TimeoutException:
            return "[错误] Java 后端响应超时"
        except Exception as e:
            return f"[错误] 请求异常: {str(e)}"

    def _post(self, path: str, json_data: dict = None) -> str:
        """POST 请求，自动解析 CommonResult"""
        url = f"{self.base_url}{path}"
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.post(url, json=json_data or {})
                return self._parse_response(resp)
        except httpx.ConnectError:
            return "[错误] 无法连接 Java 后端，请确认 mall-portal 已启动"
        except httpx.TimeoutException:
            return "[错误] Java 后端响应超时"
        except Exception as e:
            return f"[错误] 请求异常: {str(e)}"

    @staticmethod
    def _parse_response(resp: httpx.Response) -> str:
        """
        解析 Java CommonResult 响应:
        {"code": 200, "message": "操作成功", "data": "商品文本..."}
        """
        if resp.status_code != 200:
            return f"[错误] HTTP {resp.status_code}: {resp.text[:200]}"

        try:
            body = resp.json()
        except Exception:
            return resp.text

        code = body.get("code", -1)
        if code == 200:
            data = body.get("data", "")
            return data if data else "暂无数据"
        else:
            msg = body.get("message", "未知错误")
            return f"[错误] code={code}: {msg}"

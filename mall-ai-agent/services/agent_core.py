"""
LangChain Agent 核心：LLM 初始化 + 工具注册 + Agent 执行
"""
import logging
from langchain_openai import ChatOpenAI
from langchain.agents import AgentExecutor, create_openai_tools_agent
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import tool
from langchain_core.messages import HumanMessage, AIMessage

from config import DASHSCOPE_API_KEY, DASHSCOPE_BASE_URL, LLM_MODEL
from services.java_client import JavaApiClient
from services.prompt_templates import SHOPPING_SYSTEM_PROMPT

logger = logging.getLogger(__name__)

# Java API 客户端
java = JavaApiClient()


# ==================== LangChain Tools ====================

@tool
def search_products(query: str) -> str:
    """
    搜索商品。当用户想查找、推荐、对比商品时调用。
    参数 query: 搜索关键词，如"手机"、"2000元 羽绒服"、"华为耳机"。
    返回商品列表文本。
    """
    return java.search_products_by_keyword(query)


@tool
def query_stock(product_id: int) -> str:
    """
    查询商品库存。当用户询问某商品是否有货、库存多少时调用。
    参数 product_id: 商品ID（数字）。
    """
    return java.query_stock(product_id)


@tool
def query_order(status: int = None, member_id: int = 0) -> str:
    """
    查询用户订单。当用户询问"我的订单"、"订单到哪了"、"待发货订单"时调用。
    参数 status: 订单状态（可选，0=待付款 1=待发货 2=已发货 3=已完成 4=已关闭）。
    参数 member_id: 用户ID（默认0）。
    """
    return java.query_order(member_id=member_id, status=status)


AGENT_TOOLS = [search_products, query_stock, query_order]


# ==================== Agent 工厂 ====================

def create_llm() -> ChatOpenAI:
    """创建通义千问 LLM"""
    return ChatOpenAI(
        model=LLM_MODEL,
        api_key=DASHSCOPE_API_KEY,
        base_url=DASHSCOPE_BASE_URL,
        temperature=0.7,
        streaming=True,
    )


def create_agent() -> AgentExecutor:
    """创建带工具的 Agent"""
    llm = create_llm()

    prompt = ChatPromptTemplate.from_messages([
        ("system", SHOPPING_SYSTEM_PROMPT),
        MessagesPlaceholder(variable_name="chat_history", optional=True),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_openai_tools_agent(llm, AGENT_TOOLS, prompt)

    return AgentExecutor(
        agent=agent,
        tools=AGENT_TOOLS,
        verbose=True,
        handle_parsing_errors=True,
        max_iterations=5,
    )


def format_history_for_langchain(history: list[dict]) -> list:
    """将 Redis 历史消息转为 LangChain 消息格式"""
    messages = []
    for m in (history or []):
        role = m.get("role", "user")
        content = m.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role in ("assistant", "ai"):
            messages.append(AIMessage(content=content))
    return messages

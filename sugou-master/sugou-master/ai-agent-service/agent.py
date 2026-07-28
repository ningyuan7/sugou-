"""
LangChain Agent 逻辑
"""
from __future__ import annotations

from langchain_openai import ChatOpenAI
from langchain_ollama import ChatOllama
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain.memory import ChatMessageHistory

from config import (
    LLM_PROVIDER, OPENAI_API_KEY, OPENAI_API_BASE, LLM_MODEL, LLM_TEMPERATURE,
    OLLAMA_BASE_URL, OLLAMA_MODEL,
)
from tools import get_all_tools


def _build_llm():
    """根据配置选择 LLM 后端"""
    if LLM_PROVIDER == "ollama":
        return ChatOllama(
            base_url=OLLAMA_BASE_URL,
            model=OLLAMA_MODEL,
            temperature=LLM_TEMPERATURE,
        )
    return ChatOpenAI(
        model=LLM_MODEL,
        temperature=LLM_TEMPERATURE,
        api_key=OPENAI_API_KEY,
        base_url=OPENAI_API_BASE,
    )


# 共享的 Agent 实例（无状态，每次对话构建新的 memory）
_llm = _build_llm()
_tools = get_all_tools()

# 系统提示语
SYSTEM_PROMPT = """你是 mall 商城的 AI 导购助手，中文名"小M"。
你可以查询商品信息、搜索商品、推荐商品、查询库存、查看商品分类和查询用户订单。

使用规则：
1. 先用自然语言回答用户的问题
2. 需要查数据时，调用相应的工具
3. 工具返回的数据要整理成易读的格式呈现给用户
4. 对于商品推荐，可以根据用户偏好提供个性化建议
5. 如果用户询问订单信息，需要先确认用户身份【memberId】
"""


def create_agent() -> AgentExecutor:
    """创建 LangChain Agent 执行器"""
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        MessagesPlaceholder(variable_name="chat_history", optional=True),
        ("human", "{input}"),
        MessagesPlaceholder(variable_name="agent_scratchpad"),
    ])

    agent = create_tool_calling_agent(_llm, _tools, prompt)

    return AgentExecutor(
        agent=agent,
        tools=_tools,
        verbose=True,
        handle_parsing_errors=True,
        max_iterations=5,
    )


async def chat(message: str, session_id: str | None = None) -> tuple[str, str | None]:
    """执行一次对话，返回 (回复内容, session_id)"""
    executor = create_agent()
    result = await executor.ainvoke({"input": message})
    return result["output"], session_id

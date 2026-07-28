"""LangGraph Agent 核心"""
from langchain_openai import ChatOpenAI
from langgraph.prebuilt import create_react_agent
from langchain_core.messages import HumanMessage, AIMessage

from config import DASHSCOPE_API_KEY, DASHSCOPE_BASE_URL, LLM_MODEL
from agent.tools.business_tools import AGENT_TOOLS
from agent.prompts.shopping_prompts import SHOPPING_SYSTEM_PROMPT


def create_llm() -> ChatOpenAI:
    return ChatOpenAI(
        model=LLM_MODEL,
        api_key=DASHSCOPE_API_KEY,
        base_url=DASHSCOPE_BASE_URL,
        temperature=0.7,
        streaming=True,
    )


def create_agent():
    return create_react_agent(
        model=create_llm(),
        tools=AGENT_TOOLS,
        state_modifier=SHOPPING_SYSTEM_PROMPT,
    )


def format_history(history: list[dict]) -> list:
    messages = []
    for m in (history or []):
        role = m.get("role", "user")
        content = m.get("content", "")
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role in ("assistant", "ai"):
            messages.append(AIMessage(content=content))
    return messages

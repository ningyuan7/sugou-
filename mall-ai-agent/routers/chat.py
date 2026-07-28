"""
FastAPI 路由：AI 导购对话接口
GET  /api/chat/stream  - SSE 流式对话（核心，前端调用）
POST /api/chat/sync   - 非流式对话（调试/测试用）
POST /api/session/create - 创建会话
"""
import uuid
import logging
from fastapi import APIRouter, Query, Body
from fastapi.responses import JSONResponse
from sse_starlette.sse import EventSourceResponse
from pydantic import BaseModel

from services.agent_core import create_agent, format_history_for_langchain
from services.session_manager import SessionManager

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api", tags=["AI Chat"])
session_mgr = SessionManager()


class ChatRequest(BaseModel):
    session_id: str
    message: str


# ==================== 会话管理 ====================

@router.post("/session/create")
async def create_session():
    """创建新会话，返回 sessionId"""
    sid = uuid.uuid4().hex
    return JSONResponse({"code": 200, "data": sid})


# ==================== 非流式对话（调试用） ====================

@router.post("/chat/sync")
async def chat_sync(req: ChatRequest):
    """非流式对话，返回完整回答"""
    try:
        session_id = req.session_id
        message = req.message

        # 1. 记录用户消息
        session_mgr.add_message(session_id, "user", message)

        # 2. 检查高频缓存
        if not session_mgr.is_contextual_question(message):
            cached = session_mgr.get_cached_answer(message)
            if cached:
                session_mgr.add_message(session_id, "assistant", cached)
                return JSONResponse({"code": 200, "data": cached})

        # 3. 获取历史
        history = session_mgr.get_history(session_id)
        formatted = format_history_for_langchain(history)

        # 4. 执行 Agent
        agent = create_agent()
        result = agent.invoke({
            "input": message,
            "chat_history": formatted,
        })

        answer = result.get("output", "抱歉，我暂时无法回答。")

        # 5. 缓存 & 记录
        if len(answer) > 20 and not session_mgr.is_contextual_question(message):
            session_mgr.cache_answer(message, answer)
        session_mgr.add_message(session_id, "assistant", answer)

        return JSONResponse({"code": 200, "data": answer})

    except Exception as e:
        logger.error(f"对话异常: {e}", exc_info=True)
        return JSONResponse({"code": 500, "message": str(e)}, status_code=500)


# ==================== SSE 流式对话（核心接口） ====================

@router.get("/chat/stream")
async def chat_stream(
    session_id: str = Query(..., description="会话ID"),
    message: str = Query(..., description="用户消息"),
):
    """SSE 流式对话，实时推送 AI 回答"""

    async def event_generator():
        try:
            # 1. 记录用户消息
            session_mgr.add_message(session_id, "user", message)
            yield {"event": "status", "data": "正在分析您的需求..."}

            # 2. 检查高频缓存（上下文相关问题不走缓存）
            if not session_mgr.is_contextual_question(message):
                cached = session_mgr.get_cached_answer(message)
                if cached:
                    yield {"event": "content", "data": cached}
                    yield {"event": "done", "data": "[缓存命中]"}
                    session_mgr.add_message(session_id, "assistant", cached)
                    return

            # 3. 获取历史
            history = session_mgr.get_history(session_id)
            formatted = format_history_for_langchain(history)

            # 4. 创建 Agent 并流式执行
            agent = create_agent()
            full_response = ""

            async for chunk in agent.astream_events(
                {"input": message, "chat_history": formatted},
                version="v2",
            ):
                kind = chunk.get("event", "")

                # Agent 正在调用工具
                if kind == "on_tool_start":
                    tool_name = chunk.get("name", "")
                    yield {"event": "status", "data": f"正在查询: {tool_name}..."}

                # 工具调用完成
                elif kind == "on_tool_end":
                    yield {"event": "status", "data": "正在整理回答..."}

                # LLM 输出 token（流式文字）
                elif kind == "on_chat_model_stream":
                    content = chunk.get("data", {}).get("chunk", {})
                    if hasattr(content, "content") and content.content:
                        text = content.content
                        full_response += text
                        yield {"event": "content", "data": text}

            # 5. 完成
            if not full_response.strip():
                full_response = "抱歉，我暂时无法回答，请稍后再试。"

            if len(full_response) > 20 and not session_mgr.is_contextual_question(message):
                session_mgr.cache_answer(message, full_response)

            session_mgr.add_message(session_id, "assistant", full_response)
            yield {"event": "done", "data": ""}

        except Exception as e:
            logger.error(f"流式对话异常: {e}", exc_info=True)
            yield {"event": "error", "data": f"服务异常: {str(e)}"}

    return EventSourceResponse(event_generator())

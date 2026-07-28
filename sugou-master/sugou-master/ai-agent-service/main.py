"""
AI Agent 导购服务 - FastAPI 入口
"""
from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

from agent import chat
from config import SERVICE_PORT


class ChatRequest(BaseModel):
    message: str
    session_id: str | None = None


class ChatResponse(BaseModel):
    session_id: str
    reply: str


@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"AI Agent 导购服务启动，端口: {SERVICE_PORT}")
    yield
    print("服务关闭")


app = FastAPI(
    title="mall AI Agent 导购服务",
    description="Python LangChain Agent，通过 Gateway 调用 sugou-portal 业务 API",
    version="1.0.0",
    lifespan=lifespan,
)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "ai-agent"}


@app.post("/ai/chat", response_model=ChatResponse)
async def chat_endpoint(req: ChatRequest):
    """对话接口：用户消息 → LangChain Agent → 回复"""
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="消息不能为空")

    session_id = req.session_id or uuid.uuid4().hex[:12]
    reply, _ = await chat(req.message, session_id)

    return ChatResponse(session_id=session_id, reply=reply)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=SERVICE_PORT, reload=True)


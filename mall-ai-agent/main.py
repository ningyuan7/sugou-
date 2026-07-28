"""
Mall AI Agent — FastAPI 入口
电商智能导购 Agent 服务（LangChain + 通义千问 + Java 数据层）
"""
import uvicorn
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers.chat import router as chat_router

# 日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Mall AI Agent",
    description="电商智能导购 Agent 服务\n\n架构: Python FastAPI (Agent) → Java Spring Boot (数据层) → MySQL",
    version="1.0.0",
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router)


@app.get("/health")
async def health():
    """健康检查"""
    return {"status": "ok", "service": "mall-ai-agent"}


if __name__ == "__main__":
    logger.info("Mall AI Agent 启动中...")
    logger.info("API 文档: http://localhost:8000/docs")
    logger.info("依赖 Java 后端: http://localhost:8085")
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)

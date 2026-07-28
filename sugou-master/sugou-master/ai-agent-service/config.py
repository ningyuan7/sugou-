"""
AI Agent 导购服务配置
"""
from __future__ import annotations

import os

# 网关地址（Java Spring Cloud Gateway）
GATEWAY_BASE_URL = os.getenv("GATEWAY_BASE_URL", "http://localhost:8088")

# LLM 配置
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "openai")  # openai / deepseek / ollama
OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_API_BASE = os.getenv("OPENAI_API_BASE", "https://api.openai.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "gpt-4o-mini")
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.7"))

# 可选：Ollama 本地模型
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "qwen2.5:7b")

# 服务端口
SERVICE_PORT = int(os.getenv("SERVICE_PORT", "8000"))

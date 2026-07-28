"""
Redis 会话管理 + 高频问题缓存
与 Java 侧共用同一 Redis，键前缀保持一致
"""
import json
from typing import Optional
import redis
from config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DB

# Redis Key 前缀（与 Java AiChatSessionService 保持一致）
SESSION_PREFIX = "ai:session:"
SESSION_TTL = 3600        # 会话1小时过期
FAQ_CACHE_PREFIX = "ai:faq:"
FAQ_CACHE_TTL = 86400     # 高频问答24小时过期
MAX_HISTORY_ROUNDS = 5    # 最多保留5轮历史


class SessionManager:
    """Redis 会话管理器"""

    def __init__(self):
        self.redis = redis.Redis(
            host=REDIS_HOST,
            port=REDIS_PORT,
            password=REDIS_PASSWORD or None,
            db=REDIS_DB,
            decode_responses=True,
        )

    # ==================== 会话消息 ====================

    def get_history(self, session_id: str) -> list[dict]:
        """
        获取最近 N 轮对话历史
        返回格式: [{"role": "user", "content": "..."}, ...]
        """
        key = f"{SESSION_PREFIX}{session_id}"
        raw = self.redis.lrange(key, -MAX_HISTORY_ROUNDS * 2, -1)
        return [json.loads(m) for m in raw] if raw else []

    def add_message(self, session_id: str, role: str, content: str):
        """添加消息到会话"""
        key = f"{SESSION_PREFIX}{session_id}"
        msg = json.dumps({"role": role, "content": content}, ensure_ascii=False)
        self.redis.rpush(key, msg)
        self.redis.expire(key, SESSION_TTL)

    def clear_session(self, session_id: str):
        """清空会话"""
        self.redis.delete(f"{SESSION_PREFIX}{session_id}")

    # ==================== 高频问题缓存 ====================

    def get_cached_answer(self, question: str) -> Optional[str]:
        """获取缓存的高频问题回答"""
        key = f"{FAQ_CACHE_PREFIX}{abs(hash(question.strip()))}"
        return self.redis.get(key)

    def cache_answer(self, question: str, answer: str):
        """缓存回答"""
        key = f"{FAQ_CACHE_PREFIX}{abs(hash(question.strip()))}"
        self.redis.setex(key, FAQ_CACHE_TTL, answer)

    # ==================== 工具方法 ====================

    def is_contextual_question(self, message: str) -> bool:
        """
        判断是否为依赖上下文的问题（不走缓存）
        如: "那个"、"刚才的"、"还有别的吗"
        """
        keywords = [
            "那个", "刚才", "前面", "这个", "上面", "之前",
            "还有别的", "再推荐", "类似的", "同款", "第一款",
            "第二款", "第三款", "第一个", "第二个", "刚才那"
        ]
        return any(k in message for k in keywords)

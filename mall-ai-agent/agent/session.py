"""Redis 会话管理（懒连接，无 Redis 时自动降级）"""
import hashlib
import json
import logging
from typing import Optional
import redis
from config import REDIS_HOST, REDIS_PORT, REDIS_PASSWORD, REDIS_DB

logger = logging.getLogger("mall.session")

SESSION_PREFIX = "ai:session:"
SESSION_TTL = 3600
FAQ_PREFIX = "ai:faq:"
FAQ_TTL = 86400


class SessionManager:
    def __init__(self):
        self._redis: Optional[redis.Redis] = None

    def check_redis(self) -> Optional[redis.Redis]:
        if self._redis is None:
            try:
                self._redis = redis.Redis(
                    host=REDIS_HOST, port=REDIS_PORT,
                    password=REDIS_PASSWORD or None, db=REDIS_DB,
                    decode_responses=True,
                    socket_connect_timeout=3, socket_timeout=3,
                )
                self._redis.ping()
                logger.info("Redis 就绪")
            except redis.RedisError as e:
                logger.warning("Redis 不可用: %s", e)
                self._redis = None
        return self._redis

    # ---- 会话历史 ----
    def get_history(self, session_id: str, max_rounds: int = 5) -> list[dict]:
        r = self.check_redis()
        if not r:
            return []
        try:
            key = f"{SESSION_PREFIX}{session_id}"
            raw = r.lrange(key, -max_rounds * 2, -1)
            return [json.loads(m) for m in raw] if raw else []
        except redis.RedisError:
            return []

    def add_message(self, session_id: str, role: str, content: str):
        r = self.check_redis()
        if not r:
            return
        try:
            key = f"{SESSION_PREFIX}{session_id}"
            r.rpush(key, json.dumps({"role": role, "content": content}, ensure_ascii=False))
            r.expire(key, SESSION_TTL)
        except redis.RedisError:
            pass

    # ---- FAQ 缓存（MD5 跨进程一致） ----
    def get_cached_answer(self, question: str) -> Optional[str]:
        r = self.check_redis()
        if not r:
            return None
        try:
            return r.get(f"{FAQ_PREFIX}{hashlib.md5(question.strip().encode()).hexdigest()}")
        except redis.RedisError:
            return None

    def cache_answer(self, question: str, answer: str):
        r = self.check_redis()
        if not r:
            return
        try:
            r.setex(f"{FAQ_PREFIX}{hashlib.md5(question.strip().encode()).hexdigest()}", FAQ_TTL, answer)
        except redis.RedisError:
            pass


session_manager = SessionManager()

"""
向量迁移脚本：将 medqa.json 迁移到 pgvector
"""
import json
import psycopg2
from psycopg2.extras import execute_values
import numpy as np

# 配置
MEDQA_JSON = "E:/Github_project/LangChain4j-KGQA/backend/resource/embeddings/medqa.json"
PG_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "database": "kgqa",
    "user": "postgres",
    "password": "123456"
}

def load_vectors():
    """加载 medqa.json"""
    print(f"加载向量文件: {MEDQA_JSON}")
    with open(MEDQA_JSON, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return data.get("entries", [])

def migrate_to_pgvector(entries):
    """迁移向量到 pgvector"""
    print(f"连接 PostgreSQL...")
    conn = psycopg2.connect(**PG_CONFIG)
    cur = conn.cursor()

    # 确保 embeddings 表存在
    cur.execute("""
        CREATE TABLE IF NOT EXISTS embeddings (
            id BIGSERIAL PRIMARY KEY,
            content TEXT,
            embedding vector(1024)
        )
    """)

    # 检查是否已有数据
    cur.execute("SELECT COUNT(*) FROM embeddings")
    count = cur.fetchone()[0]
    if count > 0:
        print(f"embeddings 表已有 {count} 条数据，清空后重新导入？")
        response = input("输入 'yes' 确认清空: ")
        if response == 'yes':
            cur.execute("TRUNCATE embeddings RESTART IDENTITY")
            conn.commit()
        else:
            print("取消迁移")
            return

    print(f"开始迁移 {len(entries)} 条向量...")

    batch_size = 100
    total = len(entries)

    for i in range(0, total, batch_size):
        batch = entries[i:i+batch_size]
        values = []

        for entry in batch:
            try:
                # 解析向量
                vector = entry.get("embedding", {}).get("vector", [])
                if len(vector) != 1024:
                    continue

                # 解析文本
                text = ""
                text_segment = entry.get("textSegment", {})
                if text_segment:
                    text = text_segment.get("text", "")

                values.append((text, vector))

            except Exception as e:
                print(f"解析条目失败: {e}")
                continue

        if values:
            # 批量插入
            execute_values(
                cur,
                "INSERT INTO embeddings (content, embedding) VALUES %s",
                values,
                template="(%s, %s::vector)"
            )
            conn.commit()

        progress = min(i + batch_size, total)
        print(f"进度: {progress}/{total} ({100*progress/total:.1f}%)")

    # 创建索引
    print("创建向量索引...")
    cur.execute("CREATE INDEX IF NOT EXISTS idx_embeddings_vector ON embeddings USING ivfflat (embedding cosine_ops)")

    conn.commit()
    cur.close()
    conn.close()

    print(f"迁移完成！共 {total} 条向量")

def main():
    entries = load_vectors()
    migrate_to_pgvector(entries)

if __name__ == "__main__":
    main()

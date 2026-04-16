#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Wikidata 医疗知识图谱批量抽取脚本
节点：Disease / Symptom / Drug / BodyPart / Specialty / DiagnosticProcedure / Treatment
关系：8类核心关系（见 RELATION_MAP）
输出：ttl_output/ 目录下各节点TTL + 合并后 medical_knowledge_full.ttl

依赖：pip install requests rdflib
"""

import time
import re
import requests
from pathlib import Path

# ─── 配置区 ────────────────────────────────────────────────────────────────────

SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
OUTPUT_DIR = Path("./ttl_output")
OUTPUT_DIR.mkdir(exist_ok=True)

BATCH_SIZE   = 1000   # 每批条数（Wikidata 限流时可调小到500）
SLEEP_OK     = 5      # 正常请求间隔(秒)
SLEEP_RETRY  = 60     # 限流后等待(秒)
MAX_RETRIES  = 5      # 单批最大重试次数
MAX_BATCHES  = 50     # 单节点最多抽取批次(防止死循环)

HEADERS = {
    "User-Agent": "MedicalKGExtractor/2.0 (student-research-project)",
    "Accept":     "text/turtle",
}

# ─── Namespace 声明（写入TTL头部）────────────────────────────────────────────

PREFIXES = """\
@prefix wd:     <http://www.wikidata.org/entity/> .
@prefix wdt:    <http://www.wikidata.org/prop/direct/> .
@prefix rdfs:   <http://www.w3.org/2000/01/rdf-schema#> .
@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix schema: <http://schema.org/> .
@prefix med:    <http://example.org/medical#> .
@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .

"""

# ─── 节点类型定义 ──────────────────────────────────────────────────────────────
# wikidata_class : Wikidata QID
# rdf_type       : 写入TTL的类型URI
# use_subclass   : 是否使用 wdt:P31/wdt:P279* (True) 或仅 wdt:P31 (False)

NODE_TYPES = {
    "disease": {
        "wikidata_class": "wd:Q12136",
        "rdf_type":       "med:Disease",
        "use_subclass":   True,
    },
    "symptom": {
        "wikidata_class": "wd:Q169872",
        "rdf_type":       "med:Symptom",
        "use_subclass":   True,
    },
    "drug": {
        "wikidata_class": "wd:Q12140",
        "rdf_type":       "med:Drug",
        "use_subclass":   True,
    },
    "body_part": {
        "wikidata_class": "wd:Q4936952",
        "rdf_type":       "med:BodyPart",
        "use_subclass":   True,
    },
    "specialty": {
        "wikidata_class": "wd:Q930752",
        "rdf_type":       "med:Specialty",
        "use_subclass":   False,   # 专科数量少，不需要递归
    },
    "diagnostic_procedure": {
        "wikidata_class": "wd:Q175111",
        "rdf_type":       "med:DiagnosticProcedure",
        "use_subclass":   True,
    },
    "treatment": {
        "wikidata_class": "wd:Q179661",
        "rdf_type":       "med:Treatment",
        "use_subclass":   True,
    },
}

# ─── 关系定义（仅Disease节点携带完整关系） ────────────────────────────────────
# property_var : SPARQL变量名（必须唯一）
# wdt_prop     : Wikidata属性
# schema_prop  : 写入TTL的谓词URI
# label_lang   : 关联节点label语言过滤

DISEASE_RELATIONS = [
    # (property_var,    wdt_prop,    schema_prop,                      )
    ("symptom",        "wdt:P780",  "med:hasSymptom"                  ),
    ("drug",           "wdt:P2176", "med:treatedBy"                   ),
    ("bodypart",       "wdt:P927",  "med:locatedIn"                   ),
    ("specialty",      "wdt:P1995", "med:specialty"                   ),
    ("cause_has",      "wdt:P1542", "med:hasCause"                    ),  # P1542: has cause
    ("cause_of",       "wdt:P828",  "med:causedBy"                    ),  # P828:  has cause (alt)
    ("diagmethod",     "wdt:P923",  "med:diagnosedBy"                 ),  # P923:  medical examination
    ("treatment",      "wdt:P924",  "med:hasTreatment"                ),  # P924:  has treatment
    ("riskfactor",     "wdt:P5642", "med:riskFactor"                  ),  # P5642: risk factor
]

# ─── SELECT查询：抽取实体URI + 标签 ─────────────────────────────────────────

def build_node_select_query(wikidata_class: str, use_subclass: bool,
                             limit: int, offset: int) -> str:
    """
    SELECT查询：返回实体QID和中英文标签
    使用SELECT而非CONSTRUCT，避免Wikidata CONSTRUCT超时问题
    """
    if use_subclass:
        class_pattern = f"?entity wdt:P31/wdt:P279* {wikidata_class} ."
    else:
        class_pattern = f"?entity wdt:P31 {wikidata_class} ."

    return f"""
SELECT DISTINCT ?entity ?labelZh ?labelEn
WHERE {{
  {class_pattern}

  OPTIONAL {{
    ?entity rdfs:label ?labelZh .
    FILTER(LANG(?labelZh) = "zh")
  }}
  OPTIONAL {{
    ?entity rdfs:label ?labelEn .
    FILTER(LANG(?labelEn) = "en")
  }}

  # 至少有一种语言的标签
  FILTER(BOUND(?labelZh) || BOUND(?labelEn))
}}
LIMIT {limit}
OFFSET {offset}
"""

def build_disease_relation_select_query(disease_uris: list[str],
                                         rel_var: str, wdt_prop: str) -> str:
    """
    SELECT查询：对一批disease URI抽取单条关系
    分属性查询，避免OPTIONAL爆炸导致超时
    """
    values_clause = " ".join(f"wd:{qid}" for qid in disease_uris)
    return f"""
SELECT ?disease ?target ?labelZh ?labelEn
WHERE {{
  VALUES ?disease {{ {values_clause} }}
  ?disease {wdt_prop} ?target .

  OPTIONAL {{
    ?target rdfs:label ?labelZh .
    FILTER(LANG(?labelZh) = "zh")
  }}
  OPTIONAL {{
    ?target rdfs:label ?labelEn .
    FILTER(LANG(?labelEn) = "en")
  }}
}}
"""

# ─── HTTP请求封装 ─────────────────────────────────────────────────────────────

def sparql_select(query: str, retries: int = MAX_RETRIES) -> list[dict] | None:
    """执行SELECT查询，返回bindings列表，失败返回None"""
    for attempt in range(retries):
        try:
            resp = requests.get(
                SPARQL_ENDPOINT,
                params={"query": query, "format": "application/sparql-results+json"},
                headers={**HEADERS, "Accept": "application/sparql-results+json"},
                timeout=90,
            )
            if resp.status_code == 200:
                data = resp.json()
                return data.get("results", {}).get("bindings", [])
            elif resp.status_code == 429:
                wait = SLEEP_RETRY * (attempt + 1)
                print(f"\n    ⚠ 429限流，等待 {wait}s (尝试 {attempt+1}/{retries})")
                time.sleep(wait)
            elif resp.status_code == 500:
                print(f"\n    ✗ 500服务器错误，可能查询超时，重试 {attempt+1}/{retries}")
                time.sleep(30)
            else:
                print(f"\n    ✗ HTTP {resp.status_code}")
                return None
        except requests.exceptions.Timeout:
            print(f"\n    ✗ 请求超时，重试 {attempt+1}/{retries}")
            time.sleep(20)
        except Exception as e:
            print(f"\n    ✗ 异常: {e}")
            time.sleep(10)
    return None

# ─── 解析QID ──────────────────────────────────────────────────────────────────

def uri_to_qid(uri: str) -> str:
    """http://www.wikidata.org/entity/Q123 → Q123"""
    return uri.split("/")[-1]

def binding_value(row: dict, key: str) -> str | None:
    return row[key]["value"] if key in row else None

# ─── TTL序列化 ────────────────────────────────────────────────────────────────

def escape_ttl_string(s: str) -> str:
    """转义TTL字面量中的特殊字符"""
    s = s.replace("\\", "\\\\")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "\\r")
    return s

def entity_to_ttl_block(qid: str, rdf_type: str,
                          label_zh: str | None, label_en: str | None) -> str:
    """将单个实体序列化为TTL片段"""
    lines = [f"wd:{qid}"]
    lines.append(f"    rdf:type {rdf_type} ;")
    if label_zh:
        lines.append(f'    rdfs:label "{escape_ttl_string(label_zh)}"@zh ;')
    if label_en:
        lines.append(f'    rdfs:label "{escape_ttl_string(label_en)}"@en ;')
    # 去掉最后一个 ; 换成 .
    last = lines[-1].rstrip(" ;") + " ."
    lines[-1] = last
    return "\n".join(lines) + "\n"

def relation_to_ttl(subject_qid: str, predicate_uri: str,
                     object_qid: str) -> str:
    return f"wd:{subject_qid} {predicate_uri} wd:{object_qid} .\n"

# ─── 主抽取逻辑 ──────────────────────────────────────────────────────────────

def extract_node_entities(node_name: str, config: dict) -> list[dict]:
    """
    分批SELECT抽取某节点类型的所有实体
    返回 [{"qid": "Q123", "label_zh": ..., "label_en": ...}, ...]
    """
    print(f"\n{'='*55}")
    print(f"▶ 抽取节点: {node_name} (类={config['wikidata_class']})")

    all_entities = []
    offset = 0

    for batch_num in range(1, MAX_BATCHES + 1):
        print(f"  批次 {batch_num:3d} offset={offset:6d} ...", end=" ", flush=True)

        query = build_node_select_query(
            config["wikidata_class"],
            config["use_subclass"],
            BATCH_SIZE, offset
        )
        rows = sparql_select(query)

        if rows is None:
            print("请求失败，停止此节点抽取")
            break

        if len(rows) == 0:
            print("无更多数据，抽取完成 ✓")
            break

        for row in rows:
            qid = uri_to_qid(binding_value(row, "entity") or "")
            if not qid.startswith("Q"):
                continue
            all_entities.append({
                "qid":      qid,
                "label_zh": binding_value(row, "labelZh"),
                "label_en": binding_value(row, "labelEn"),
            })

        print(f"获得 {len(rows):4d} 条，累计 {len(all_entities):6d}")

        if len(rows) < BATCH_SIZE:
            print(f"  最后一批，抽取完成 ✓")
            break

        offset += BATCH_SIZE
        time.sleep(SLEEP_OK)

    return all_entities


def extract_disease_relations(disease_entities: list[dict]) -> dict[str, list[tuple]]:
    """
    对disease实体，逐属性分批抽取8类关系
    返回 {rel_var: [(disease_qid, target_qid, label_zh, label_en), ...]}
    """
    print(f"\n{'='*55}")
    print(f"▶ 抽取Disease关系（共{len(disease_entities)}个disease实体）")

    # 所有disease QID列表
    all_qids = [e["qid"] for e in disease_entities]

    relation_data = {rel[0]: [] for rel in DISEASE_RELATIONS}

    # 每次提交一批disease QID（避免VALUES子句太长）
    VALUES_BATCH = 200

    for rel_var, wdt_prop, schema_prop in DISEASE_RELATIONS:
        print(f"\n  关系: {rel_var} ({wdt_prop})")
        total = 0

        for i in range(0, len(all_qids), VALUES_BATCH):
            batch_qids = all_qids[i: i + VALUES_BATCH]
            batch_no   = i // VALUES_BATCH + 1
            total_batches = (len(all_qids) + VALUES_BATCH - 1) // VALUES_BATCH
            print(f"    batch {batch_no:3d}/{total_batches} ...", end=" ", flush=True)

            query = build_disease_relation_select_query(batch_qids, rel_var, wdt_prop)
            rows  = sparql_select(query)

            if rows is None:
                print("失败，跳过")
                time.sleep(SLEEP_OK)
                continue

            for row in rows:
                d_qid  = uri_to_qid(binding_value(row, "disease") or "")
                t_qid  = uri_to_qid(binding_value(row, "target")  or "")
                if not (d_qid.startswith("Q") and t_qid.startswith("Q")):
                    continue
                relation_data[rel_var].append((
                    d_qid,
                    t_qid,
                    binding_value(row, "labelZh"),
                    binding_value(row, "labelEn"),
                ))

            total += len(rows)
            print(f"{len(rows):4d} 条，累计 {total:6d}")
            time.sleep(SLEEP_OK)

    return relation_data


def write_node_ttl(node_name: str, rdf_type: str, entities: list[dict]) -> Path:
    """将节点实体写入TTL文件"""
    out_path = OUTPUT_DIR / f"{node_name}.ttl"
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(PREFIXES)
        for e in entities:
            block = entity_to_ttl_block(
                e["qid"], rdf_type, e.get("label_zh"), e.get("label_en")
            )
            f.write(block + "\n")
    print(f"  💾 {out_path}  ({len(entities)} 个实体)")
    return out_path


def write_relation_ttl(relation_data: dict[str, list[tuple]]) -> Path:
    """将关系三元组写入TTL文件"""
    out_path = OUTPUT_DIR / "disease_relations.ttl"
    # rel_var → schema_prop 映射
    prop_map = {r[0]: r[2] for r in DISEASE_RELATIONS}

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(PREFIXES)
        total = 0
        for rel_var, triples in relation_data.items():
            schema_prop = prop_map[rel_var]
            f.write(f"\n# ── {rel_var} ({schema_prop}) ──\n")
            for (d_qid, t_qid, lz, le) in triples:
                f.write(relation_to_ttl(d_qid, schema_prop, t_qid))
                # 顺便写target节点的label（如果有）
                if lz:
                    f.write(f'wd:{t_qid} rdfs:label "{escape_ttl_string(lz)}"@zh .\n')
                if le:
                    f.write(f'wd:{t_qid} rdfs:label "{escape_ttl_string(le)}"@en .\n')
                total += 1

    print(f"  💾 {out_path}  ({total} 条关系三元组)")
    return out_path


def merge_ttl_files():
    """合并所有TTL文件 → medical_knowledge_full.ttl"""
    print(f"\n{'='*55}")
    print("▶ 合并所有TTL文件...")
    merged_path = OUTPUT_DIR / "medical_knowledge_full.ttl"

    seen_triples = set()
    data_lines   = []

    for ttl_file in sorted(OUTPUT_DIR.glob("*.ttl")):
        if ttl_file.name == "medical_knowledge_full.ttl":
            continue
        with open(ttl_file, "r", encoding="utf-8") as f:
            for line in f:
                stripped = line.strip()
                # 跳过前缀行和空行和注释（合并文件统一加前缀）
                if (stripped.startswith("@prefix") or
                        stripped == "" or
                        stripped.startswith("#")):
                    continue
                if stripped not in seen_triples:
                    seen_triples.add(stripped)
                    data_lines.append(line)

    with open(merged_path, "w", encoding="utf-8") as f:
        f.write(PREFIXES)
        f.writelines(data_lines)

    print(f"  ✓ 合并完成: {merged_path}")
    print(f"  📊 去重后三元组行数: {len(data_lines):,}")
    return merged_path


def print_stats(node_results: dict, relation_data: dict):
    """打印抽取结果统计"""
    print(f"\n{'='*55}")
    print("📊 抽取结果统计")
    print(f"{'─'*55}")
    print(f"{'节点类型':<25} {'实体数':>10}")
    print(f"{'─'*55}")
    total_entities = 0
    for name, entities in node_results.items():
        print(f"  {name:<23} {len(entities):>10,}")
        total_entities += len(entities)
    print(f"{'─'*55}")
    print(f"  {'合计':<23} {total_entities:>10,}")

    print(f"\n{'─'*55}")
    print(f"{'关系类型':<25} {'三元组数':>10}")
    print(f"{'─'*55}")
    total_relations = 0
    prop_map = {r[0]: r[2] for r in DISEASE_RELATIONS}
    for rel_var, triples in relation_data.items():
        print(f"  {prop_map[rel_var]:<23} {len(triples):>10,}")
        total_relations += len(triples)
    print(f"{'─'*55}")
    print(f"  {'合计':<23} {total_relations:>10,}")
    print(f"{'='*55}\n")


# ─── 入口 ─────────────────────────────────────────────────────────────────────

def main():
    print("🚀 Wikidata医疗知识图谱抽取开始")
    print(f"   输出目录: {OUTPUT_DIR.resolve()}")
    print(f"   每批大小: {BATCH_SIZE}")

    node_results = {}

    # 1. 抽取7类节点
    for node_name, config in NODE_TYPES.items():
        entities = extract_node_entities(node_name, config)
        node_results[node_name] = entities
        write_node_ttl(node_name, config["rdf_type"], entities)
        time.sleep(SLEEP_OK)

    # 2. 抽取Disease关系（8类）
    disease_entities = node_results.get("disease", [])
    if not disease_entities:
        print("⚠ 未抽取到disease实体，跳过关系抽取")
        relation_data = {}
    else:
        relation_data = extract_disease_relations(disease_entities)
        write_relation_ttl(relation_data)

    # 3. 合并
    merged = merge_ttl_files()

    # 4. 统计
    print_stats(node_results, relation_data)

    print(f"✅ 全部完成！最终文件: {merged.resolve()}")
    print("""
下一步：
  1. 验证TTL：  riot --validate ttl_output/medical_knowledge_full.ttl
  2. 导入Neo4j：使用 neosemantics(n10s) 插件
  3. 向量化RAG：用Apache Jena读取三元组 → 转自然语言 → embedding
""")


if __name__ == "__main__":
    main()
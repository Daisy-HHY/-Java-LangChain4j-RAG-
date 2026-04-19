#!/usr/bin/env python3
"""
将 relation_*.ttl 文件的本体从 http://example.org/medical-ontology/
转换为 http://kgqa.com/medical#
"""

import os
import re

# 源本体前缀
SOURCE_PREFIX = "http://example.org/medical-ontology/"
# 目标本体前缀
TARGET_PREFIX = "http://kgqa.com/medical#"

# 关系映射 (source -> target)
RELATION_MAP = {
    "hasCause": "causedBy",
    "hasSymptom": "hasSymptom",
    "hasComplication": "complicationOf",
    "hasDepartment": "belongsToDept",
    "hasDrug": "treatedBy",  # relation_drug_disease
}

# 实体类型映射
ENTITY_MAP = {
    "med:Disease": "kg:Disease",
    "med:Symptom": "kg:Symptom",
    "med:Drug": "kg:Drug",
    "med:BodyPart": "kg:BodyPart",
    "med:Department": "kg:Department",
}

# 替换前缀
PREFIX_MAP = {
    "med:": "kg:",
}

def convert_line(line):
    """转换单行"""
    result = line

    # 1. 替换前缀声明 (med: -> kg:) 和 URL
    if "@prefix med:" in result:
        result = result.replace("@prefix med:", "@prefix kg:")
        result = result.replace(SOURCE_PREFIX, TARGET_PREFIX)

    # 2. 替换本体前缀 URIs (http://example.org/medical-ontology/ -> http://kgqa.com/medical#)
    result = result.replace(SOURCE_PREFIX, TARGET_PREFIX)

    # 3. 替换 med: -> kg: (用于属性和类型)
    result = result.replace("med:", "kg:")

    # 4. 替换实体类型 (kg:Disease -> kg:Disease) - 保持不变
    # 5. 替换关系名称 (hasCause -> causedBy 等)
    for old_name, new_name in RELATION_MAP.items():
        result = result.replace(old_name, new_name)

    return result

def convert_file(input_path, output_path):
    """转换单个文件"""
    print(f"转换: {input_path} -> {output_path}")

    with open(input_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 转换
    lines = content.split('\n')
    converted_lines = []
    for line in lines:
        converted_lines.append(convert_line(line))

    # 写入
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(converted_lines))

    print(f"  完成: {len(converted_lines)} 行")

def main():
    # 获取项目根目录（util 的父目录）
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    input_dir = os.path.join(base_dir, "wikidata", "output")
    output_dir = os.path.join(base_dir, "wikidata", "output_converted")

    # 创建输出目录
    os.makedirs(output_dir, exist_ok=True)

    # 需要转换的文件
    files_to_convert = [
        "relation_disease_symptom.ttl",
        "relation_disease_cause.ttl",
        "relation_disease_complication.ttl",
        "relation_disease_department.ttl",
        "relation_drug_disease.ttl",
    ]

    # 同时复制 entity 文件（只需改前缀）
    entity_files = [
        "entity_disease.ttl",
        "entity_symptom.ttl",
        "entity_drug.ttl",
        "entity_bodypart.ttl",
        "entity_department.ttl",
    ]

    print("=" * 50)
    print("转换 relation 文件")
    print("=" * 50)

    for fname in files_to_convert:
        input_path = os.path.join(input_dir, fname)
        output_path = os.path.join(output_dir, fname)
        if os.path.exists(input_path):
            convert_file(input_path, output_path)
        else:
            print(f"  跳过（不存在）: {fname}")

    print("\n" + "=" * 50)
    print("复制 entity 文件")
    print("=" * 50)

    for fname in entity_files:
        input_path = os.path.join(input_dir, fname)
        output_path = os.path.join(output_dir, fname)
        if os.path.exists(input_path):
            with open(input_path, 'r', encoding='utf-8') as f:
                content = f.read()
            # 替换 med: 前缀为 kg:
            content = content.replace("@prefix med:", "@prefix kg:")
            content = content.replace("med:", "kg:")
            content = content.replace(SOURCE_PREFIX, TARGET_PREFIX)
            # 替换实体类型
            for old, new in ENTITY_MAP.items():
                content = content.replace(old, new)
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"复制并转换: {fname}")
        else:
            print(f"  跳过（不存在）: {fname}")

    # 复制统计文件
    stats_path = os.path.join(input_dir, "export_stats.json")
    if os.path.exists(stats_path):
        import shutil
        shutil.copy(stats_path, os.path.join(output_dir, "export_stats.json"))

    print("\n" + "=" * 50)
    print(f"转换完成！输出目录: {output_dir}")
    print("=" * 50)

if __name__ == "__main__":
    main()

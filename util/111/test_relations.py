#!/usr/bin/env python3
"""测试 Wikidata 关系查询"""
 
import requests
import json
 
ENDPOINT = "https://qlever.cs.uni-freiburg.de/api/wikidata"
 
def test_query(name, query):
    """测试单个查询"""
    print(f"\n{'='*60}")
    print(f"测试: {name}")
    print(f"{'='*60}")
    
    try:
        response = requests.get(
            ENDPOINT,
            params={'query': query, 'format': 'json'},
            headers={'User-Agent': 'Test/1.0'},
            timeout=60
        )
        
        if response.ok:
            result = response.json()
            bindings = result.get('results', {}).get('bindings', [])
            count = len(bindings)
            
            if count > 0:
                print(f"✓ 找到 {count} 条结果")
                # 显示前 3 个示例
                for i, b in enumerate(bindings[:3]):
                    print(f"\n示例 {i+1}:")
                    for key, value in b.items():
                        print(f"  {key}: {value.get('value', 'N/A')}")
            else:
                print(f"✗ 没有找到结果")
                
            return count
        else:
            print(f"✗ 查询失败: {response.status_code}")
            print(response.text[:500])
            return 0
            
    except Exception as e:
        print(f"✗ 错误: {e}")
        return 0
 
 
# ==================== 测试查询 ====================
 
# 1. 测试疾病-部位关系
query_disease_bodypart = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?disease ?diseaseLabel ?bodypart ?bodypartLabel WHERE {
  ?disease wdt:P31/wdt:P279* wd:Q12136 .
  ?disease wdt:P4272 ?bodypart .
  
  OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
  OPTIONAL { ?bodypart rdfs:label ?bodypartLabel . FILTER(LANG(?bodypartLabel) = "zh") }
}
LIMIT 10
"""
 
# 2. 测试药物-疾病关系 (P2176)
query_drug_disease_p2176 = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?drug ?drugLabel ?disease ?diseaseLabel WHERE {
  ?drug wdt:P31 wd:Q8386 .
  ?drug wdt:P2176 ?disease .
  
  OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
  OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
}
LIMIT 10
"""
 
# 3. 测试药物-疾病关系 (P2175 - 医学条件治疗)
query_drug_disease_p2175 = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?drug ?drugLabel ?disease ?diseaseLabel WHERE {
  ?drug wdt:P31 wd:Q8386 .
  ?drug wdt:P2175 ?disease .
  
  OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
  OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
}
LIMIT 10
"""
 
# 4. 测试更宽松的药物查询
query_drug_any_relation = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?drug ?drugLabel (COUNT(DISTINCT ?p) as ?propCount) WHERE {
  ?drug wdt:P31 wd:Q8386 .
  ?drug ?p ?o .
  ?drug rdfs:label ?drugLabel .
  FILTER(LANG(?drugLabel) = "zh")
}
GROUP BY ?drug ?drugLabel
LIMIT 10
"""
 
# 5. 查看疾病有哪些可用属性
query_disease_properties = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
 
SELECT ?p (COUNT(?p) as ?count) WHERE {
  ?disease wdt:P31 wd:Q12136 .
  ?disease ?p ?o .
  FILTER(STRSTARTS(STR(?p), "http://www.wikidata.org/prop/direct/"))
}
GROUP BY ?p
ORDER BY DESC(?count)
LIMIT 20
"""
 
# 6. 查看药物有哪些可用属性
query_drug_properties = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
 
SELECT ?p (COUNT(?p) as ?count) WHERE {
  ?drug wdt:P31 wd:Q8386 .
  ?drug ?p ?o .
  FILTER(STRSTARTS(STR(?p), "http://www.wikidata.org/prop/direct/"))
}
GROUP BY ?p
ORDER BY DESC(?count)
LIMIT 20
"""
 
# 7. 测试疾病-科室 (使用 medical specialty)
query_disease_specialty = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?disease ?diseaseLabel ?specialty ?specialtyLabel WHERE {
  ?disease wdt:P31 wd:Q12136 .
  ?disease wdt:P1995 ?specialty .
  ?specialty wdt:P31/wdt:P279* wd:Q5257550 .
  
  OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
  OPTIONAL { ?specialty rdfs:label ?specialtyLabel . FILTER(LANG(?specialtyLabel) = "zh") }
}
LIMIT 10
"""
 
# 8. 简化的药物-疾病查询（去掉疾病类型限制）
query_drug_simple = """
PREFIX wd: <http://www.wikidata.org/entity/>
PREFIX wdt: <http://www.wikidata.org/prop/direct/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
 
SELECT ?drug ?drugLabel ?disease ?diseaseLabel WHERE {
  ?drug wdt:P31 wd:Q8386 .
  ?drug wdt:P2176 ?disease .
  
  OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
  OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
}
LIMIT 10
"""
 
 
if __name__ == "__main__":
    print("Wikidata 医学关系查询测试")
    print("="*60)
    
    # 运行测试
    results = {}
    
    results['disease_bodypart_p4272'] = test_query(
        "疾病-部位 (P4272)", 
        query_disease_bodypart
    )
    
    results['drug_disease_p2176'] = test_query(
        "药物-疾病 (P2176 - 用于治疗)", 
        query_drug_disease_p2176
    )
    
    results['drug_disease_p2175'] = test_query(
        "药物-疾病 (P2175 - 医学条件治疗)", 
        query_drug_disease_p2175
    )
    
    results['drug_simple'] = test_query(
        "药物-疾病 (简化查询)", 
        query_drug_simple
    )
    
    results['disease_specialty'] = test_query(
        "疾病-科室 (medical specialty)", 
        query_disease_specialty
    )
    
    # 查看可用属性
    test_query("疾病的可用属性 TOP 20", query_disease_properties)
    test_query("药物的可用属性 TOP 20", query_drug_properties)
    
    # 总结
    print("\n" + "="*60)
    print("测试总结")
    print("="*60)
    for name, count in results.items():
        status = "✓" if count > 0 else "✗"
        print(f"{status} {name}: {count} 条结果")
    
    print("\n建议:")
    if results.get('drug_disease_p2176', 0) > 0:
        print("✓ 药物-疾病关系可用 P2176 属性")
    if results.get('drug_disease_p2175', 0) > 0:
        print("✓ 药物-疾病关系可用 P2175 属性")
    if results.get('disease_bodypart_p4272', 0) > 0:
        print("✓ 疾病-部位关系可用 P4272 属性")
#!/usr/bin/env python3
"""诊断 Wikidata 医学实体的可用属性"""
 
import requests
 
ENDPOINT = "https://qlever.cs.uni-freiburg.de/api/wikidata"
 
# Wikidata 属性含义映射（常见的医学相关属性）
PROPERTY_MEANINGS = {
    'P31': 'instance of (实例)',
    'P279': 'subclass of (子类)',
    'P2892': 'UMLS CUI (医学概念唯一标识符)',
    'P780': 'symptoms (症状)',
    'P828': 'has cause (病因)',
    'P1995': 'health specialty (卫生专业)',
    'P2176': 'drug used for treatment (用于治疗的药物)',
    'P2175': 'medical condition treated (治疗的医学状况)',
    'P4272': 'anatomical location (解剖位置)',
    'P527': 'has part(s) (组成部分)',
    'P646': 'Freebase ID',
    'P486': 'MeSH descriptor ID',
    'P493': 'ICD-10',
    'P494': 'ICD-9-CM',
    'P715': 'Anatomical Therapeutic Chemical Classification System',
    'P652': 'UNII',
    'P231': 'CAS Registry Number',
    'P267': 'ATC code',
    'P683': 'ChEBI ID',
    'P1995': 'health specialty',
    'P2578': 'studies',
}
 
def get_property_details(prop_uri):
    """获取属性的详细信息"""
    prop_id = prop_uri.split('/')[-1]
    return PROPERTY_MEANINGS.get(prop_id, prop_id)
 
def query_properties(entity_type, qid, limit=30):
    """查询特定实体类型的所有属性"""
    
    query = f"""
    PREFIX wd: <http://www.wikidata.org/entity/>
    PREFIX wdt: <http://www.wikidata.org/prop/direct/>
    
    SELECT ?p (COUNT(?p) as ?count) WHERE {{
      ?entity wdt:P31 wd:{qid} .
      ?entity ?p ?o .
      FILTER(STRSTARTS(STR(?p), "http://www.wikidata.org/prop/direct/"))
    }}
    GROUP BY ?p
    ORDER BY DESC(?count)
    LIMIT {limit}
    """
    
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
            
            print(f"\n{'='*80}")
            print(f"{entity_type} (wd:{qid}) 的属性统计")
            print(f"{'='*80}")
            print(f"{'序号':<5} {'属性ID':<10} {'使用次数':<10} {'含义'}")
            print(f"{'-'*80}")
            
            for i, binding in enumerate(bindings, 1):
                prop_uri = binding.get('p', {}).get('value', '')
                count = binding.get('count', {}).get('value', '0')
                prop_id = prop_uri.split('/')[-1]
                meaning = get_property_details(prop_uri)
                
                print(f"{i:<5} {prop_id:<10} {count:<10} {meaning}")
            
            return bindings
        else:
            print(f"查询失败: {response.status_code}")
            return []
            
    except Exception as e:
        print(f"错误: {e}")
        return []
 
def test_specific_relation(name, query):
    """测试特定关系并显示详细结果"""
    print(f"\n{'='*80}")
    print(f"测试: {name}")
    print(f"{'='*80}")
    
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
            
            if bindings:
                print(f"✓ 找到 {len(bindings)} 条结果\n")
                for i, b in enumerate(bindings[:5], 1):
                    print(f"示例 {i}:")
                    for key, value in b.items():
                        print(f"  {key}: {value.get('value', 'N/A')}")
                    print()
            else:
                print(f"✗ 没有找到结果")
            
            return len(bindings)
        else:
            print(f"✗ 查询失败: {response.status_code}")
            return 0
            
    except Exception as e:
        print(f"✗ 错误: {e}")
        return 0
 
# 主测试
if __name__ == "__main__":
    print("Wikidata 医学实体属性深度诊断")
    print("="*80)
    
    # 1. 查看疾病的所有属性
    disease_props = query_properties("疾病", "Q12136", limit=30)
    
    # 2. 查看药物的所有属性
    drug_props = query_properties("药物 (medication)", "Q8386", limit=30)
    
    # 3. 测试替代的药物-疾病关系
    print("\n" + "="*80)
    print("测试替代的药物-疾病关系")
    print("="*80)
    
    # 尝试反向查询：疾病->药物
    test_specific_relation(
        "疾病->药物治疗 (P2176)",
        """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?disease ?diseaseLabel ?drug ?drugLabel WHERE {
          ?disease wdt:P31 wd:Q12136 .
          ?disease wdt:P2176 ?drug .
          
          OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
          OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
        }
        LIMIT 20
        """
    )
    
    # 尝试使用化学物质作为药物
    test_specific_relation(
        "化学物质->疾病治疗 (P2175)",
        """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?drug ?drugLabel ?disease ?diseaseLabel WHERE {
          {
            ?drug wdt:P31 wd:Q11173 .  # chemical compound
          } UNION {
            ?drug wdt:P31 wd:Q79529 .   # chemical substance
          }
          
          ?drug wdt:P2175 ?disease .
          
          OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
          OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
        }
        LIMIT 20
        """
    )
    
    # 测试药物的其他属性
    test_specific_relation(
        "药物的组成部分 (P527)",
        """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?drug ?drugLabel ?component ?componentLabel WHERE {
          ?drug wdt:P31 wd:Q8386 .
          ?drug wdt:P527 ?component .
          
          OPTIONAL { ?drug rdfs:label ?drugLabel . FILTER(LANG(?drugLabel) = "zh") }
          OPTIONAL { ?component rdfs:label ?componentLabel . FILTER(LANG(?componentLabel) = "zh") }
        }
        LIMIT 10
        """
    )
    
    # 疾病的相关科室
    test_specific_relation(
        "疾病的卫生专业 (P1995)",
        """
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        SELECT ?disease ?diseaseLabel ?specialty ?specialtyLabel WHERE {
          ?disease wdt:P31 wd:Q12136 .
          ?disease wdt:P1995 ?specialty .
          
          OPTIONAL { ?disease rdfs:label ?diseaseLabel . FILTER(LANG(?diseaseLabel) = "zh") }
          OPTIONAL { ?specialty rdfs:label ?specialtyLabel . FILTER(LANG(?specialtyLabel) = "zh") }
        }
        LIMIT 20
        """
    )
    
    print("\n" + "="*80)
    print("诊断完成")
    print("="*80)
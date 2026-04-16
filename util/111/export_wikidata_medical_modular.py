#!/usr/bin/env python3
"""
Wikidata 医学知识图谱模块化导出脚本
按实体和关系分别导出 TTL 文件，最后合并
作者: Zread AI Assistant
版本: 3.0
"""
 
import requests
import time
import json
import sys
import os
from typing import List, Dict, Optional, Set, Tuple
from rdflib import Graph, Namespace, Literal, RDF, URIRef
from rdflib.namespace import RDFS, XSD
from datetime import datetime
from pathlib import Path
import logging
from tqdm import tqdm
 
# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('wikidata_export_modular.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)
 
# 命名空间定义
WD = Namespace("http://www.wikidata.org/entity/")
WDT = Namespace("http://www.wikidata.org/prop/direct/")
SCHEMA = Namespace("http://schema.org/")
MED = Namespace("http://example.org/medical-ontology/")
 
USER_AGENT = "MedicalKGExporter/3.0 (Educational Research)"
 
 
class WikidataMedicalExporter:
    """Wikidata 医学知识图谱模块化导出器"""
    
    # 可用端点
    ENDPOINTS = [
        {
            'name': 'QLever',
            'url': 'https://qlever.cs.uni-freiburg.de/api/wikidata',
            'timeout': 600
        },
        {
            'name': 'Official',
            'url': 'https://query.wikidata.org/sparql',
            'timeout': 60
        }
    ]
    
    # Wikidata 实体类型映射
    ENTITY_TYPES = {
        'disease': {
            'qid': 'Q12136',
            'name': '疾病',
            'med_type': MED.Disease
        },
        'symptom': {
            'qid': 'Q169872',  # syndrome/symptom
            'name': '症状',
            'med_type': MED.Symptom
        },
        'drug': {
            'qid': 'Q8386',  # medication
            'name': '药物',
            'med_type': MED.Drug
        },
        'cause': {
            'qid': 'Q1931388',  # causative agent
            'name': '病因',
            'med_type': MED.Cause
        },
        'complication': {
            'qid': 'Q1931388',
            'name': '并发症',
            'med_type': MED.Complication
        },
        'department': {
            'qid': 'Q5257550',  # medical specialty
            'name': '科室',
            'med_type': MED.Department
        },
        'bodypart': {
            'qid': 'Q4936952',  # anatomical structure
            'name': '部位',
            'med_type': MED.BodyPart
        }
    }
    
    # Wikidata 属性映射
    RELATION_PROPERTIES = {
        'disease_symptom': {
            'property': 'P780',
            'name': '疾病-症状',
            'description': '疾病具有的症状'
        },
        'disease_cause': {
            'property': 'P828',
            'name': '疾病-病因',
            'description': '疾病的病因'
        },
        'disease_complication': {
            'property': 'P1995',
            'name': '疾病-并发症',
            'description': '疾病的并发症'
        },
        'disease_department': {
            'property': 'P1995',  # 注意：Wikidata 可能没有直接的科室属性
            'name': '疾病-科室',
            'description': '疾病所属科室'
        },
        'disease_bodypart': {
            'property': 'P4272',
            'name': '疾病-部位',
            'description': '疾病影响的身体部位'
        },
        'drug_disease': {
            'property': 'P2176',
            'name': '药物-疾病',
            'description': '药物治疗的疾病'
        }
    }
    
    def __init__(self, 
                 output_dir: str = './output',
                 batch_size: int = 3000,
                 proxy: Optional[str] = None,
                 endpoint_index: int = 0):
        """
        初始化导出器
        
        Args:
            output_dir: 输出目录
            batch_size: 每批处理数量
            proxy: 代理地址
            endpoint_index: 端点索引
        """
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        self.batch_size = batch_size
        self.proxy = proxy
        self.current_endpoint = self.ENDPOINTS[endpoint_index]
        
        # 代理配置
        self.proxies = None
        if proxy:
            self.proxies = {'http': proxy, 'https': proxy}
            logger.info(f"使用代理: {proxy}")
        
        # 统计信息
        self.stats = {
            'entities': {},
            'relations': {},
            'total_triples': 0,
            'start_time': datetime.now().isoformat()
        }
        
        logger.info(f"使用端点: {self.current_endpoint['name']}")
        logger.info(f"输出目录: {self.output_dir}")
    
    def _create_graph(self) -> Graph:
        """创建并配置新的 RDF 图"""
        graph = Graph()
        graph.bind("wd", WD)
        graph.bind("wdt", WDT)
        graph.bind("schema", SCHEMA)
        graph.bind("med", MED)
        graph.bind("rdfs", RDFS)
        return graph
    
    def execute_sparql(self, query: str, retry: int = 3, 
                      output_format: str = 'json') -> Optional[any]:
        """执行 SPARQL 查询"""
        headers = {
            'User-Agent': USER_AGENT,
            'Accept': 'application/sparql-results+json' if output_format == 'json' else 'text/turtle'
        }
        
        params = {
            'query': query,
            'format': 'json' if output_format == 'json' else 'text/turtle'
        }
        
        for attempt in range(retry):
            try:
                response = requests.get(
                    self.current_endpoint['url'], 
                    params=params, 
                    headers=headers,
                    proxies=self.proxies,
                    timeout=self.current_endpoint['timeout'],
                    verify=True
                )
                response.raise_for_status()
                
                if output_format == 'json':
                    return response.json()
                else:
                    return response.text
                
            except requests.exceptions.Timeout:
                logger.warning(f"请求超时，重试 {attempt + 1}/{retry}...")
                time.sleep(5 * (attempt + 1))
                
            except requests.exceptions.ConnectionError as e:
                logger.error(f"连接失败: {e}")
                if attempt < retry - 1:
                    time.sleep(10)
                    
            except requests.exceptions.HTTPError as e:
                if e.response.status_code == 429:
                    logger.warning("请求频率过高，等待 30 秒...")
                    time.sleep(30)
                else:
                    logger.error(f"HTTP 错误: {e}")
                    break
                    
            except Exception as e:
                logger.error(f"未知错误: {e}")
                break
        
        # 尝试备用端点
        for endpoint in self.ENDPOINTS:
            if endpoint['url'] == self.current_endpoint['url']:
                continue
            
            logger.info(f"尝试备用端点: {endpoint['name']}")
            try:
                response = requests.get(
                    endpoint['url'], 
                    params=params, 
                    headers=headers,
                    proxies=self.proxies,
                    timeout=endpoint['timeout']
                )
                response.raise_for_status()
                
                self.current_endpoint = endpoint
                logger.info(f"✓ 切换到端点: {endpoint['name']}")
                
                if output_format == 'json':
                    return response.json()
                else:
                    return response.text
                    
            except Exception as e:
                logger.warning(f"✗ 端点 {endpoint['name']} 失败: {e}")
                continue
        
        return None
    
    # ==================== 实体导出方法 ====================
    
    def export_disease_entities(self, max_items: Optional[int] = None) -> str:
        """导出疾病实体"""
        logger.info("=" * 60)
        logger.info("导出疾病实体")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "entity_disease.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病实体", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX schema: <http://schema.org/>
                
                SELECT DISTINCT ?entity ?label_zh ?label_en ?desc_zh ?desc_en WHERE {{
                  ?entity wdt:P31/wdt:P279* wd:Q12136 .
                  
                  OPTIONAL {{ ?entity rdfs:label ?label_zh . FILTER(LANG(?label_zh) = "zh") }}
                  OPTIONAL {{ ?entity rdfs:label ?label_en . FILTER(LANG(?label_en) = "en") }}
                  OPTIONAL {{ ?entity schema:description ?desc_zh . FILTER(LANG(?desc_zh) = "zh") }}
                  OPTIONAL {{ ?entity schema:description ?desc_en . FILTER(LANG(?desc_en) = "en") }}
                  
                  FILTER(EXISTS {{
                    ?entity rdfs:label ?anyLabel .
                    FILTER(LANG(?anyLabel) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    entity_uri = binding.get('entity', {}).get('value')
                    if not entity_uri:
                        continue
                    
                    subject = URIRef(entity_uri)
                    graph.add((subject, RDF.type, MED.Disease))
                    
                    if 'label_zh' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_zh']['value'], lang='zh')))
                    if 'label_en' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_en']['value'], lang='en')))
                    if 'desc_zh' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_zh']['value'], lang='zh')))
                    if 'desc_en' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_en']['value'], lang='en')))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        # 保存
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病实体导出完成: {total} 个，{len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['entities']['disease'] = {'count': total, 'triples': len(graph)}
        return str(output_file)
    
    def export_symptom_entities(self, max_items: Optional[int] = None) -> str:
        """导出症状实体"""
        logger.info("=" * 60)
        logger.info("导出症状实体")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "entity_symptom.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="症状实体", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX schema: <http://schema.org/>
                
                SELECT DISTINCT ?entity ?label_zh ?label_en ?desc_zh ?desc_en WHERE {{
                  {{
                    ?entity wdt:P31/wdt:P279* wd:Q169872 .  # syndrome
                  }} UNION {{
                    ?entity wdt:P31 wd:Q1441305 .  # medical sign
                  }}
                  
                  OPTIONAL {{ ?entity rdfs:label ?label_zh . FILTER(LANG(?label_zh) = "zh") }}
                  OPTIONAL {{ ?entity rdfs:label ?label_en . FILTER(LANG(?label_en) = "en") }}
                  OPTIONAL {{ ?entity schema:description ?desc_zh . FILTER(LANG(?desc_zh) = "zh") }}
                  OPTIONAL {{ ?entity schema:description ?desc_en . FILTER(LANG(?desc_en) = "en") }}
                  
                  FILTER(EXISTS {{
                    ?entity rdfs:label ?anyLabel .
                    FILTER(LANG(?anyLabel) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    entity_uri = binding.get('entity', {}).get('value')
                    if not entity_uri:
                        continue
                    
                    subject = URIRef(entity_uri)
                    graph.add((subject, RDF.type, MED.Symptom))
                    
                    if 'label_zh' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_zh']['value'], lang='zh')))
                    if 'label_en' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_en']['value'], lang='en')))
                    if 'desc_zh' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_zh']['value'], lang='zh')))
                    if 'desc_en' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_en']['value'], lang='en')))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 症状实体导出完成: {total} 个，{len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['entities']['symptom'] = {'count': total, 'triples': len(graph)}
        return str(output_file)
    
    def export_drug_entities(self, max_items: Optional[int] = None) -> str:
        """导出药物实体"""
        logger.info("=" * 60)
        logger.info("导出药物实体")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "entity_drug.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="药物实体", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX schema: <http://schema.org/>
                
                SELECT DISTINCT ?entity ?label_zh ?label_en ?desc_zh ?desc_en ?atc WHERE {{
                  {{
                    ?entity wdt:P31 wd:Q8386 .           # medication
                  }} UNION {{
                    ?entity wdt:P279 wd:Q8386 .
                  }} UNION {{
                    ?entity wdt:P31 wd:Q12140 .          # pharmaceutical product
                  }}
                  
                  OPTIONAL {{ ?entity rdfs:label ?label_zh . FILTER(LANG(?label_zh) = "zh") }}
                  OPTIONAL {{ ?entity rdfs:label ?label_en . FILTER(LANG(?label_en) = "en") }}
                  OPTIONAL {{ ?entity schema:description ?desc_zh . FILTER(LANG(?desc_zh) = "zh") }}
                  OPTIONAL {{ ?entity schema:description ?desc_en . FILTER(LANG(?desc_en) = "en") }}
                  OPTIONAL {{ ?entity wdt:P267 ?atc . }}
                  
                  FILTER(EXISTS {{
                    ?entity rdfs:label ?anyLabel .
                    FILTER(LANG(?anyLabel) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    entity_uri = binding.get('entity', {}).get('value')
                    if not entity_uri:
                        continue
                    
                    subject = URIRef(entity_uri)
                    graph.add((subject, RDF.type, MED.Drug))
                    
                    if 'label_zh' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_zh']['value'], lang='zh')))
                    if 'label_en' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_en']['value'], lang='en')))
                    if 'desc_zh' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_zh']['value'], lang='zh')))
                    if 'desc_en' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_en']['value'], lang='en')))
                    if 'atc' in binding:
                        graph.add((subject, WDT.P267, Literal(binding['atc']['value'])))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 药物实体导出完成: {total} 个，{len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['entities']['drug'] = {'count': total, 'triples': len(graph)}
        return str(output_file)
    
    def export_bodypart_entities(self, max_items: Optional[int] = None) -> str:
        """导出身体部位实体"""
        logger.info("=" * 60)
        logger.info("导出身体部位实体")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "entity_bodypart.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="部位实体", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX schema: <http://schema.org/>
                
                SELECT DISTINCT ?entity ?label_zh ?label_en ?desc_zh ?desc_en WHERE {{
                  ?entity wdt:P31/wdt:P279* wd:Q4936952 .  # anatomical structure
                  
                  OPTIONAL {{ ?entity rdfs:label ?label_zh . FILTER(LANG(?label_zh) = "zh") }}
                  OPTIONAL {{ ?entity rdfs:label ?label_en . FILTER(LANG(?label_en) = "en") }}
                  OPTIONAL {{ ?entity schema:description ?desc_zh . FILTER(LANG(?desc_zh) = "zh") }}
                  OPTIONAL {{ ?entity schema:description ?desc_en . FILTER(LANG(?desc_en) = "en") }}
                  
                  FILTER(EXISTS {{
                    ?entity rdfs:label ?anyLabel .
                    FILTER(LANG(?anyLabel) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    entity_uri = binding.get('entity', {}).get('value')
                    if not entity_uri:
                        continue
                    
                    subject = URIRef(entity_uri)
                    graph.add((subject, RDF.type, MED.BodyPart))
                    
                    if 'label_zh' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_zh']['value'], lang='zh')))
                    if 'label_en' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_en']['value'], lang='en')))
                    if 'desc_zh' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_zh']['value'], lang='zh')))
                    if 'desc_en' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_en']['value'], lang='en')))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 部位实体导出完成: {total} 个，{len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['entities']['bodypart'] = {'count': total, 'triples': len(graph)}
        return str(output_file)
    
    def export_department_entities(self, max_items: Optional[int] = None) -> str:
        """导出科室实体"""
        logger.info("=" * 60)
        logger.info("导出科室实体")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "entity_department.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="科室实体", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX schema: <http://schema.org/>
                
                SELECT DISTINCT ?entity ?label_zh ?label_en ?desc_zh ?desc_en WHERE {{
                  {{
                    ?entity wdt:P31/wdt:P279* wd:Q5257550 .  # medical specialty
                  }} UNION {{
                    ?entity wdt:P31 wd:Q1047113 .            # specialty
                  }}
                  
                  OPTIONAL {{ ?entity rdfs:label ?label_zh . FILTER(LANG(?label_zh) = "zh") }}
                  OPTIONAL {{ ?entity rdfs:label ?label_en . FILTER(LANG(?label_en) = "en") }}
                  OPTIONAL {{ ?entity schema:description ?desc_zh . FILTER(LANG(?desc_zh) = "zh") }}
                  OPTIONAL {{ ?entity schema:description ?desc_en . FILTER(LANG(?desc_en) = "en") }}
                  
                  FILTER(EXISTS {{
                    ?entity rdfs:label ?anyLabel .
                    FILTER(LANG(?anyLabel) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    entity_uri = binding.get('entity', {}).get('value')
                    if not entity_uri:
                        continue
                    
                    subject = URIRef(entity_uri)
                    graph.add((subject, RDF.type, MED.Department))
                    
                    if 'label_zh' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_zh']['value'], lang='zh')))
                    if 'label_en' in binding:
                        graph.add((subject, RDFS.label, Literal(binding['label_en']['value'], lang='en')))
                    if 'desc_zh' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_zh']['value'], lang='zh')))
                    if 'desc_en' in binding:
                        graph.add((subject, SCHEMA.description, Literal(binding['desc_en']['value'], lang='en')))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 科室实体导出完成: {total} 个，{len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['entities']['department'] = {'count': total, 'triples': len(graph)}
        return str(output_file)
    
    # ==================== 关系导出方法 ====================
    
    def export_disease_symptom_relation(self, max_items: Optional[int] = None) -> str:
        """导出疾病-症状关系"""
        logger.info("=" * 60)
        logger.info("导出疾病-症状关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_disease_symptom.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病-症状", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                CONSTRUCT {{
                  ?disease <http://example.org/medical-ontology/hasSymptom> ?symptom .
                }}
                WHERE {{
                  ?disease wdt:P31/wdt:P279* wd:Q12136 .
                  ?disease wdt:P780 ?symptom .
                  
                  FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                turtle_data = self.execute_sparql(query, output_format='turtle')
                if not turtle_data:
                    break
                
                try:
                    temp_graph = Graph()
                    temp_graph.parse(data=turtle_data, format='turtle')
                    
                    if len(temp_graph) == 0:
                        break
                    
                    for triple in temp_graph:
                        graph.add(triple)
                    
                    count = len(temp_graph)
                    total += count
                    offset += self.batch_size
                    
                    pbar.update(1)
                    pbar.set_postfix({'total': total, 'triples': len(graph)})
                    time.sleep(1)
                    
                except Exception as e:
                    logger.error(f"解析失败: {e}")
                    break
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病-症状关系导出完成: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['disease_symptom'] = {'triples': len(graph)}
        return str(output_file)
    
    def export_disease_cause_relation(self, max_items: Optional[int] = None) -> str:
        """导出疾病-病因关系"""
        logger.info("=" * 60)
        logger.info("导出疾病-病因关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_disease_cause.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病-病因", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                CONSTRUCT {{
                  ?disease <http://example.org/medical-ontology/hasCause> ?cause .
                }}
                WHERE {{
                  ?disease wdt:P31/wdt:P279* wd:Q12136 .
                  ?disease wdt:P828 ?cause .
                  
                  FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                turtle_data = self.execute_sparql(query, output_format='turtle')
                if not turtle_data:
                    break
                
                try:
                    temp_graph = Graph()
                    temp_graph.parse(data=turtle_data, format='turtle')
                    
                    if len(temp_graph) == 0:
                        break
                    
                    for triple in temp_graph:
                        graph.add(triple)
                    
                    count = len(temp_graph)
                    total += count
                    offset += self.batch_size
                    
                    pbar.update(1)
                    pbar.set_postfix({'total': total, 'triples': len(graph)})
                    time.sleep(1)
                    
                except Exception as e:
                    logger.error(f"解析失败: {e}")
                    break
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病-病因关系导出完成: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['disease_cause'] = {'triples': len(graph)}
        return str(output_file)
    
    def export_disease_complication_relation(self, max_items: Optional[int] = None) -> str:
        """导出疾病-并发症关系"""
        logger.info("=" * 60)
        logger.info("导出疾病-并发症关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_disease_complication.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病-并发症", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                CONSTRUCT {{
                  ?disease <http://example.org/medical-ontology/hasComplication> ?complication .
                }}
                WHERE {{
                  ?disease wdt:P31/wdt:P279* wd:Q12136 .
                  ?disease wdt:P1995 ?complication .
                  
                  FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                  }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                turtle_data = self.execute_sparql(query, output_format='turtle')
                if not turtle_data:
                    break
                
                try:
                    temp_graph = Graph()
                    temp_graph.parse(data=turtle_data, format='turtle')
                    
                    if len(temp_graph) == 0:
                        break
                    
                    for triple in temp_graph:
                        graph.add(triple)
                    
                    count = len(temp_graph)
                    total += count
                    offset += self.batch_size
                    
                    pbar.update(1)
                    pbar.set_postfix({'total': total, 'triples': len(graph)})
                    time.sleep(1)
                    
                except Exception as e:
                    logger.error(f"解析失败: {e}")
                    break
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病-并发症关系导出完成: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['disease_complication'] = {'triples': len(graph)}
        return str(output_file)
    
    def export_disease_bodypart_relation(self, max_items: Optional[int] = None) -> str:
        """导出疾病-部位关系（完全修复版）"""
        logger.info("=" * 60)
        logger.info("导出疾病-部位关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_disease_bodypart.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病-部位", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                # 使用 P4272 (anatomical location) 属性
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?disease ?bodypart WHERE {{
                ?disease wdt:P31 wd:Q12136 .
                ?disease wdt:P4272 ?bodypart .
                
                FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    disease_uri = binding.get('disease', {}).get('value')
                    bodypart_uri = binding.get('bodypart', {}).get('value')
                    
                    if disease_uri and bodypart_uri:
                        graph.add((
                            URIRef(disease_uri),
                            MED.affectsBodyPart,
                            URIRef(bodypart_uri)
                        ))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病-部位关系导出完成: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['disease_bodypart'] = {'triples': len(graph)}
        return str(output_file)
    
    def export_disease_department_relation(self, max_items: Optional[int] = None) -> str:
        """导出疾病-科室关系（完全修复版）"""
        logger.info("=" * 60)
        logger.info("导出疾病-科室关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_disease_department.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="疾病-科室", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                # 使用 P1995 (health specialty) 属性
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?disease ?specialty WHERE {{
                ?disease wdt:P31 wd:Q12136 .
                ?disease wdt:P1995 ?specialty .
                
                FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    disease_uri = binding.get('disease', {}).get('value')
                    specialty_uri = binding.get('specialty', {}).get('value')
                    
                    if disease_uri and specialty_uri:
                        graph.add((
                            URIRef(disease_uri),
                            MED.belongsToDepartment,
                            URIRef(specialty_uri)
                        ))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 疾病-科室关系导出完成: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['disease_department'] = {'triples': len(graph)}
        return str(output_file)
    
    def export_drug_disease_relation(self, max_items: Optional[int] = None) -> str:
        """导出药物-疾病关系（完全修复版）"""
        logger.info("=" * 60)
        logger.info("导出药物-疾病关系")
        logger.info("=" * 60)
        
        graph = self._create_graph()
        output_file = self.output_dir / "relation_drug_disease.ttl"
        
        offset = 0
        total = 0
        
        with tqdm(desc="药物-疾病", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    break
                
                # 策略1: 疾病 -> 药物 (P2176)，然后反转为 药物 treats 疾病
                query = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?disease ?drug WHERE {{
                ?disease wdt:P31 wd:Q12136 .
                ?disease wdt:P2176 ?drug .
                
                FILTER(EXISTS {{
                    ?disease rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset}
                """
                
                results = self.execute_sparql(query)
                if not results:
                    break
                
                bindings = results.get('results', {}).get('bindings', [])
                if not bindings:
                    break
                
                for binding in bindings:
                    disease_uri = binding.get('disease', {}).get('value')
                    drug_uri = binding.get('drug', {}).get('value')
                    
                    if disease_uri and drug_uri:
                        # 反转：药物 treats 疾病
                        graph.add((URIRef(drug_uri), MED.treats, URIRef(disease_uri)))
                
                count = len(bindings)
                total += count
                offset += self.batch_size
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(graph)})
                time.sleep(1)
        
        # 策略2: 化学物质 -> 疾病 (P2175)
        logger.info("补充化学物质治疗关系...")
        offset2 = 0
        total2 = 0
    
        with tqdm(desc="化学物质-疾病", unit="batch") as pbar2:
            while True:
                if max_items and total2 >= max_items:
                    break
                
                query2 = f"""
                PREFIX wd: <http://www.wikidata.org/entity/>
                PREFIX wdt: <http://www.wikidata.org/prop/direct/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?chem ?disease WHERE {{
                {{
                    ?chem wdt:P31 wd:Q11173 .  # chemical compound
                }} UNION {{
                    ?chem wdt:P31 wd:Q79529 .  # chemical substance
                }}
                
                ?chem wdt:P2175 ?disease .
                
                FILTER(EXISTS {{
                    ?chem rdfs:label ?label .
                    FILTER(LANG(?label) IN ("zh", "en"))
                }})
                }}
                LIMIT {self.batch_size}
                OFFSET {offset2}
                """
                
                results2 = self.execute_sparql(query2)
                if not results2:
                    break
                
                bindings2 = results2.get('results', {}).get('bindings', [])
                if not bindings2:
                    break
                
                for binding in bindings2:
                    chem_uri = binding.get('chem', {}).get('value')
                    disease_uri = binding.get('disease', {}).get('value')
                    
                    if chem_uri and disease_uri:
                        graph.add((URIRef(chem_uri), MED.treats, URIRef(disease_uri)))
                
                count2 = len(bindings2)
                total2 += count2
                offset2 += self.batch_size
                
                pbar2.update(1)
                pbar2.set_postfix({'total': total2, 'triples': len(graph)})
                time.sleep(1)
    
        graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        logger.info(f"✓ 药物-疾病关系导出完成")
        logger.info(f"  疾病->药物(反转): {total} 条")
        logger.info(f"  化学物质->疾病: {total2} 条")
        logger.info(f"  总计: {len(graph)} 条三元组")
        logger.info(f"✓ 文件: {output_file}")
        
        self.stats['relations']['drug_disease'] = {'triples': len(graph)}
        return str(output_file)

    
    # ==================== 合并方法 ====================
    
    def merge_all_files(self, file_list: List[str]) -> str:
        """合并所有 TTL 文件"""
        logger.info("=" * 60)
        logger.info("合并所有 TTL 文件")
        logger.info("=" * 60)
        
        merged_graph = self._create_graph()
        output_file = self.output_dir / "all_medical_knowledge.ttl"
        
        for file_path in file_list:
            if not os.path.exists(file_path):
                logger.warning(f"文件不存在: {file_path}")
                continue
            
            logger.info(f"加载: {file_path}")
            try:
                merged_graph.parse(file_path, format='turtle')
            except Exception as e:
                logger.error(f"加载失败: {file_path}, 错误: {e}")
        
        # 添加元数据
        metadata_uri = URIRef("http://example.org/wikidata-medical-export")
        merged_graph.add((metadata_uri, RDF.type, SCHEMA.Dataset))
        merged_graph.add((metadata_uri, SCHEMA.name, Literal("医学知识图谱（Wikidata）", lang='zh')))
        merged_graph.add((metadata_uri, SCHEMA.name, Literal("Medical Knowledge Graph (Wikidata)", lang='en')))
        merged_graph.add((metadata_uri, SCHEMA.dateCreated, Literal(
            datetime.now().isoformat(), datatype=XSD.dateTime
        )))
        
        # 保存
        merged_graph.serialize(destination=str(output_file), format='turtle', encoding='utf-8')
        
        file_size = os.path.getsize(output_file) / 1024 / 1024
        logger.info(f"✓ 合并完成")
        logger.info(f"✓ 总三元组数: {len(merged_graph)}")
        logger.info(f"✓ 文件大小: {file_size:.2f} MB")
        logger.info(f"✓ 文件: {output_file}")
        
        return str(output_file)
    
    # ==================== 主导出流程 ====================
    
    def export_all(self, max_items: Optional[int] = None):
        """导出所有实体和关系"""
        logger.info("=" * 60)
        logger.info("开始导出 Wikidata 医学知识图谱")
        logger.info("=" * 60)
        
        start_time = time.time()
        all_files = []
        
        try:
            # 导出实体
            logger.info("\n【第一阶段：导出实体】")
            all_files.append(self.export_disease_entities(max_items))
            all_files.append(self.export_symptom_entities(max_items))
            all_files.append(self.export_drug_entities(max_items))
            all_files.append(self.export_bodypart_entities(max_items))
            all_files.append(self.export_department_entities(max_items))
            
            # 导出关系
            logger.info("\n【第二阶段：导出关系】")
            all_files.append(self.export_disease_symptom_relation(max_items))
            all_files.append(self.export_disease_cause_relation(max_items))
            all_files.append(self.export_disease_complication_relation(max_items))
            all_files.append(self.export_disease_bodypart_relation(max_items))
            all_files.append(self.export_disease_department_relation(max_items))
            all_files.append(self.export_drug_disease_relation(max_items))
            
            # 合并所有文件
            logger.info("\n【第三阶段：合并文件】")
            merged_file = self.merge_all_files(all_files)
            
            # 保存统计信息
            self._save_stats()
            
        except KeyboardInterrupt:
            logger.warning("用户中断")
            sys.exit(0)
        except Exception as e:
            logger.error(f"导出失败: {e}", exc_info=True)
            raise
        
        # 输出统计
        elapsed = time.time() - start_time
        logger.info("\n" + "=" * 60)
        logger.info("导出完成！")
        logger.info("=" * 60)
        logger.info(f"总用时: {elapsed:.2f} 秒 ({elapsed/60:.2f} 分钟)")
        logger.info(f"\n实体统计:")
        for entity_type, stats in self.stats.get('entities', {}).items():
            logger.info(f"  - {entity_type}: {stats.get('count', 0)} 个实体，{stats.get('triples', 0)} 条三元组")
        logger.info(f"\n关系统计:")
        for relation_type, stats in self.stats.get('relations', {}).items():
            logger.info(f"  - {relation_type}: {stats.get('triples', 0)} 条三元组")
        logger.info("=" * 60)
    
    def _save_stats(self):
        """保存统计信息"""
        stats_file = self.output_dir / "export_stats.json"
        with open(stats_file, 'w', encoding='utf-8') as f:
            json.dump(self.stats, f, indent=2, ensure_ascii=False)
        logger.info(f"✓ 统计信息已保存: {stats_file}")
 
 
def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Wikidata 医学知识图谱模块化导出',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 完整导出
  python export_wikidata_medical_modular.py
  
  # 测试导出（每类 100 条）
  python export_wikidata_medical_modular.py --max-items 100
  
  # 使用代理
  python export_wikidata_medical_modular.py --proxy http://127.0.0.1:7890
  
  # 自定义输出目录
  python export_wikidata_medical_modular.py --output-dir ./my_output
        """
    )
    
    parser.add_argument('--output-dir', '-o', 
                       default='./output',
                       help='输出目录 (默认: ./output)')
    
    parser.add_argument('--batch-size', '-b', 
                       type=int, default=3000,
                       help='每批处理的数据量 (默认: 3000)')
    
    parser.add_argument('--max-items', '-m', 
                       type=int, default=None,
                       help='每类最大导出数量，用于测试 (默认: 无限制)')
    
    parser.add_argument('--proxy', '-p', 
                       default=None,
                       help='代理地址，例如: http://127.0.0.1:7897')
    
    parser.add_argument('--endpoint', '-e', 
                       type=int, default=0,
                       choices=[0, 1],
                       help='选择端点: 0=QLever(推荐), 1=官方 (默认: 0)')
    
    args = parser.parse_args()
    
    # 显示配置
    logger.info("=" * 60)
    logger.info("配置信息:")
    logger.info(f"  输出目录: {args.output_dir}")
    logger.info(f"  批次大小: {args.batch_size}")
    logger.info(f"  最大导出: {args.max_items if args.max_items else '无限制'}")
    logger.info(f"  代理: {args.proxy if args.proxy else '无'}")
    logger.info(f"  端点: {WikidataMedicalExporter.ENDPOINTS[args.endpoint]['name']}")
    logger.info("=" * 60)
    
    # 创建导出器
    exporter = WikidataMedicalExporter(
        output_dir=args.output_dir,
        batch_size=args.batch_size,
        proxy=args.proxy,
        endpoint_index=args.endpoint
    )
    
    # 导出所有数据
    exporter.export_all(max_items=args.max_items)
    
    logger.info(f"\n✓ 所有文件已保存到: {args.output_dir}")
 
 
if __name__ == "__main__":
    main()
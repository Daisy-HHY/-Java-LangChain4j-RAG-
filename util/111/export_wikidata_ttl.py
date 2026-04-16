#!/usr/bin/env python3
"""
Wikidata 医学数据导出为 TTL 格式脚本
支持中英文双语、多端点、代理、增量导出、断点续传
作者: Zread AI Assistant
版本: 2.0
"""
 
import requests
import time
import json
import sys
import os
from typing import List, Dict, Optional, Set
from rdflib import Graph, Namespace, Literal, RDF, URIRef
from rdflib.namespace import RDFS, XSD, SKOS
from datetime import datetime
from pathlib import Path
import logging
from tqdm import tqdm
 
# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('wikidata_export.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)
 
# 命名空间定义
WD = Namespace("http://www.wikidata.org/entity/")
WDT = Namespace("http://www.wikidata.org/prop/direct/")
SCHEMA = Namespace("http://schema.org/")
MED = Namespace("http://example.org/medical-ontology/")
 
# 用户代理设置（Wikidata 要求）
USER_AGENT = "MedicalKGExporter/2.0 (Educational Research; Contact: your-email@example.com)"
 
 
class WikidataTTLExporter:
    """Wikidata 医学数据 TTL 导出器"""
    
    # 可用的镜像端点列表
    ENDPOINTS = [
        {
            'name': 'QLever',
            'url': 'https://qlever.cs.uni-freiburg.de/api/wikidata',
            'timeout': 600,
            'recommended': True
        },
        {
            'name': 'Official',
            'url': 'https://query.wikidata.org/sparql',
            'timeout': 60,
            'recommended': False
        },
        {
            'name': 'Orb',
            'url': 'https://try.orbopengraph.com/sparql',
            'timeout': 120,
            'recommended': False
        }
    ]
    
    def __init__(self, 
                 output_file: str,
                 batch_size: int = 3000,
                 proxy: Optional[str] = None,
                 endpoint_index: int = 0,
                 resume: bool = False):
        """
        初始化导出器
        
        Args:
            output_file: 输出文件路径
            batch_size: 每批处理数量
            proxy: 代理地址，例如 http://127.0.0.1:7890
            endpoint_index: 使用的端点索引
            resume: 是否从上次中断处继续
        """
        self.output_file = output_file
        self.batch_size = batch_size
        self.proxy = proxy
        self.current_endpoint = self.ENDPOINTS[endpoint_index]
        self.resume = resume
        
        # 状态文件
        self.state_file = f"{output_file}.state.json"
        self.temp_file = f"{output_file}.temp.ttl"
        
        # 初始化图
        self.graph = Graph()
        self._bind_namespaces()
        
        # 统计信息
        self.stats = {
            'total_exported': 0,
            'diseases': 0,
            'drugs': 0,
            'anatomy': 0,
            'relations': 0,
            'start_time': datetime.now().isoformat(),
            'last_offset': 0
        }
        
        # 代理配置
        self.proxies = None
        if proxy:
            self.proxies = {
                'http': proxy,
                'https': proxy
            }
            logger.info(f"使用代理: {proxy}")
        
        # 加载之前的状态
        if resume and os.path.exists(self.state_file):
            self._load_state()
            logger.info(f"从上次中断处继续，offset={self.stats['last_offset']}")
        
        logger.info(f"使用端点: {self.current_endpoint['name']} - {self.current_endpoint['url']}")
        
    def _bind_namespaces(self):
        """绑定命名空间"""
        self.graph.bind("wd", WD)
        self.graph.bind("wdt", WDT)
        self.graph.bind("schema", SCHEMA)
        self.graph.bind("med", MED)
        self.graph.bind("rdfs", RDFS)
        self.graph.bind("skos", SKOS)
    
    def _load_state(self):
        """加载之前的状态"""
        try:
            with open(self.state_file, 'r', encoding='utf-8') as f:
                self.stats = json.load(f)
            
            # 加载之前的图
            if os.path.exists(self.temp_file):
                self.graph.parse(self.temp_file, format='turtle')
                logger.info(f"已加载 {len(self.graph)} 条三元组")
        except Exception as e:
            logger.warning(f"加载状态文件失败: {e}，将从头开始")
            self.stats['last_offset'] = 0
    
    def _save_state(self):
        """保存当前状态"""
        try:
            with open(self.state_file, 'w', encoding='utf-8') as f:
                json.dump(self.stats, f, indent=2, ensure_ascii=False)
            
            # 保存临时图
            self.graph.serialize(destination=self.temp_file, format='turtle', encoding='utf-8')
        except Exception as e:
            logger.error(f"保存状态失败: {e}")
    
    def execute_sparql(self, query: str, retry: int = 3, 
                      output_format: str = 'json') -> Optional[Dict]:
        """
        执行 SPARQL 查询，支持重试和多端点故障转移
        
        Args:
            query: SPARQL 查询语句
            retry: 重试次数
            output_format: 输出格式 (json/turtle)
            
        Returns:
            查询结果字典或 None
        """
        headers = {
            'User-Agent': USER_AGENT,
            'Accept': 'application/sparql-results+json' if output_format == 'json' else 'text/turtle'
        }
        
        params = {
            'query': query,
            'format': 'json' if output_format == 'json' else 'text/turtle'
        }
        
        # 首先尝试当前端点
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
                    logger.info(f"等待 10 秒后重试...")
                    time.sleep(10)
                else:
                    logger.error("已达到最大重试次数")
                    
            except requests.exceptions.HTTPError as e:
                logger.error(f"HTTP 错误: {e}")
                if e.response.status_code == 429:  # Too Many Requests
                    logger.warning("请求频率过高，等待 30 秒...")
                    time.sleep(30)
                elif e.response.status_code >= 500:
                    logger.warning(f"服务器错误 {e.response.status_code}，重试...")
                    time.sleep(10)
                else:
                    break
                    
            except Exception as e:
                logger.error(f"未知错误: {e}")
                break
        
        # 如果当前端点失败，尝试切换到其他端点
        logger.warning(f"当前端点失败，尝试切换到其他镜像...")
        for endpoint in self.ENDPOINTS:
            if endpoint['url'] == self.current_endpoint['url']:
                continue
            
            logger.info(f"尝试端点: {endpoint['name']}")
            try:
                response = requests.get(
                    endpoint['url'], 
                    params=params, 
                    headers=headers,
                    proxies=self.proxies,
                    timeout=endpoint['timeout'],
                    verify=True
                )
                response.raise_for_status()
                
                # 如果成功，切换到这个端点
                self.current_endpoint = endpoint
                logger.info(f"✓ 成功切换到端点: {endpoint['name']}")
                
                if output_format == 'json':
                    return response.json()
                else:
                    return response.text
                
            except Exception as e:
                logger.warning(f"✗ 端点 {endpoint['name']} 也失败了: {e}")
                continue
        
        logger.error("所有端点都失败了，请检查网络连接或使用代理")
        return None
    
    def export_diseases(self, limit: int, offset: int) -> int:
        """
        导出疾病实体
        
        Args:
            limit: 每批数量
            offset: 偏移量
            
        Returns:
            导出的实体数量
        """
        logger.info(f"导出疾病实体 (offset={offset}, limit={limit})...")
        
        query = f"""
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX schema: <http://schema.org/>
        
        SELECT DISTINCT ?disease ?label_zh ?label_en ?desc_zh ?desc_en ?parent WHERE {{
          ?disease wdt:P31/wdt:P279* wd:Q12136 .
          
          OPTIONAL {{
            ?disease rdfs:label ?label_zh .
            FILTER(LANG(?label_zh) = "zh")
          }}
          
          OPTIONAL {{
            ?disease rdfs:label ?label_en .
            FILTER(LANG(?label_en) = "en")
          }}
          
          OPTIONAL {{
            ?disease schema:description ?desc_zh .
            FILTER(LANG(?desc_zh) = "zh")
          }}
          
          OPTIONAL {{
            ?disease schema:description ?desc_en .
            FILTER(LANG(?desc_en) = "en")
          }}
          
          OPTIONAL {{
            ?disease wdt:P279 ?parent .
          }}
          
          # 确保有中文或英文标签
          FILTER(EXISTS {{
            ?disease rdfs:label ?anyLabel .
            FILTER(LANG(?anyLabel) IN ("zh", "en"))
          }})
        }}
        LIMIT {limit}
        OFFSET {offset}
        """
        
        results = self.execute_sparql(query)
        if not results:
            return 0
        
        count = 0
        bindings = results.get('results', {}).get('bindings', [])
        
        for binding in bindings:
            disease_uri = binding.get('disease', {}).get('value')
            if not disease_uri:
                continue
            
            subject = URIRef(disease_uri)
            
            # 添加类型
            self.graph.add((subject, RDF.type, MED.Disease))
            
            # 添加中文标签
            if 'label_zh' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_zh']['value'], lang='zh'
                )))
            
            # 添加英文标签
            if 'label_en' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_en']['value'], lang='en'
                )))
            
            # 添加中文描述
            if 'desc_zh' in binding:
                self.graph.add((subject, SCHEMA.description, Literal(
                    binding['desc_zh']['value'], lang='zh'
                )))
            
            # 添加英文描述
            if 'desc_en' in binding:
                self.graph.add((subject, SCHEMA.description, Literal(
                    binding['desc_en']['value'], lang='en'
                )))
            
            # 添加父类关系
            if 'parent' in binding:
                parent_uri = binding['parent']['value']
                self.graph.add((subject, WDT.P279, URIRef(parent_uri)))
            
            count += 1
        
        logger.info(f"✓ 导出疾病实体: {count} 个")
        return count
    
    def export_disease_relations(self, limit: int, offset: int) -> int:
        """
        导出疾病关系（症状、治疗等）
        
        Args:
            limit: 每批数量
            offset: 偏移量
            
        Returns:
            导出的三元组数量
        """
        logger.info(f"导出疾病关系 (offset={offset}, limit={limit})...")
        
        query = f"""
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        CONSTRUCT {{
          ?disease ?p ?o .
        }}
        WHERE {{
          ?disease wdt:P31/wdt:P279* wd:Q12136 .
          ?disease ?p ?o .
          
          FILTER(?p IN (
            wdt:P780,   # 症状
            wdt:P2176,  # 药物治疗
            wdt:P2177,  # 医学治疗
            wdt:P828,   # 病因
            wdt:P1995,  # 并发症
            wdt:P4272,  # 影响的解剖结构
            wdt:P2573,  # 症状
            wdt:P1690,  # 诊断方法
            wdt:P1542,  # 死亡率
            wdt:P828    # 病因
          ))
          
          # 确保疾病有中文或英文标签
          FILTER(EXISTS {{
            ?disease rdfs:label ?label .
            FILTER(LANG(?label) IN ("zh", "en"))
          }})
        }}
        LIMIT {limit}
        OFFSET {offset}
        """
        
        # 使用 CONSTRUCT 查询，返回 Turtle 格式
        turtle_data = self.execute_sparql(query, output_format='turtle')
        if not turtle_data:
            return 0
        
        try:
            # 解析 Turtle 数据并添加到主图
            temp_graph = Graph()
            temp_graph.parse(data=turtle_data, format='turtle')
            
            # 合并图
            for triple in temp_graph:
                self.graph.add(triple)
            
            count = len(temp_graph)
            logger.info(f"✓ 导出疾病关系: {count} 条三元组")
            return count
            
        except Exception as e:
            logger.error(f"解析 Turtle 数据失败: {e}")
            return 0
    
    def export_drugs(self, limit: int, offset: int) -> int:
        """
        导出药物实体
        
        Args:
            limit: 每批数量
            offset: 偏移量
            
        Returns:
            导出的实体数量
        """
        logger.info(f"导出药物实体 (offset={offset}, limit={limit})...")
        
        query = f"""
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX schema: <http://schema.org/>
        
        SELECT DISTINCT ?drug ?label_zh ?label_en ?desc_zh ?desc_en ?atc ?smiles WHERE {{
          ?drug wdt:P31/wdt:P279* wd:Q188123 .
          
          OPTIONAL {{ 
            ?drug rdfs:label ?label_zh . 
            FILTER(LANG(?label_zh) = "zh") 
          }}
          OPTIONAL {{ 
            ?drug rdfs:label ?label_en . 
            FILTER(LANG(?label_en) = "en") 
          }}
          OPTIONAL {{ 
            ?drug schema:description ?desc_zh . 
            FILTER(LANG(?desc_zh) = "zh") 
          }}
          OPTIONAL {{ 
            ?drug schema:description ?desc_en . 
            FILTER(LANG(?desc_en) = "en") 
          }}
          OPTIONAL {{ ?drug wdt:P267 ?atc . }}
          OPTIONAL {{ ?drug wdt:P233 ?smiles . }}
          
          FILTER(EXISTS {{
            ?drug rdfs:label ?anyLabel .
            FILTER(LANG(?anyLabel) IN ("zh", "en"))
          }})
        }}
        LIMIT {limit}
        OFFSET {offset}
        """
        
        results = self.execute_sparql(query)
        if not results:
            return 0
        
        count = 0
        bindings = results.get('results', {}).get('bindings', [])
        
        for binding in bindings:
            drug_uri = binding.get('drug', {}).get('value')
            if not drug_uri:
                continue
            
            subject = URIRef(drug_uri)
            self.graph.add((subject, RDF.type, MED.Drug))
            
            if 'label_zh' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_zh']['value'], lang='zh'
                )))
            
            if 'label_en' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_en']['value'], lang='en'
                )))
            
            if 'desc_zh' in binding:
                self.graph.add((subject, SCHEMA.description, Literal(
                    binding['desc_zh']['value'], lang='zh'
                )))
            
            if 'desc_en' in binding:
                self.graph.add((subject, SCHEMA.description, Literal(
                    binding['desc_en']['value'], lang='en'
                )))
            
            if 'atc' in binding:
                self.graph.add((subject, WDT.P267, Literal(
                    binding['atc']['value']
                )))
            
            if 'smiles' in binding:
                self.graph.add((subject, WDT.P233, Literal(
                    binding['smiles']['value']
                )))
            
            count += 1
        
        logger.info(f"✓ 导出药物实体: {count} 个")
        return count
    
    def export_drug_relations(self, limit: int, offset: int) -> int:
        """导出药物关系（治疗、副作用等）"""
        logger.info(f"导出药物关系 (offset={offset}, limit={limit})...")
        
        query = f"""
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        
        CONSTRUCT {{
          ?drug ?p ?o .
        }}
        WHERE {{
          ?drug wdt:P31/wdt:P279* wd:Q188123 .
          ?drug ?p ?o .
          
          FILTER(?p IN (
            wdt:P2175,  # 医学条件治疗
            wdt:P2176,  # 用于治疗
            wdt:P1530,  # 副作用
            wdt:P769    # 显著副作用
          ))
          
          FILTER(EXISTS {{
            ?drug rdfs:label ?label .
            FILTER(LANG(?label) IN ("zh", "en"))
          }})
        }}
        LIMIT {limit}
        OFFSET {offset}
        """
        
        turtle_data = self.execute_sparql(query, output_format='turtle')
        if not turtle_data:
            return 0
        
        try:
            temp_graph = Graph()
            temp_graph.parse(data=turtle_data, format='turtle')
            
            for triple in temp_graph:
                self.graph.add(triple)
            
            count = len(temp_graph)
            logger.info(f"✓ 导出药物关系: {count} 条三元组")
            return count
            
        except Exception as e:
            logger.error(f"解析药物关系失败: {e}")
            return 0
    
    def export_anatomy(self, limit: int, offset: int) -> int:
        """导出解剖结构"""
        logger.info(f"导出解剖结构 (offset={offset}, limit={limit})...")
        
        query = f"""
        PREFIX wd: <http://www.wikidata.org/entity/>
        PREFIX wdt: <http://www.wikidata.org/prop/direct/>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        PREFIX schema: <http://schema.org/>
        
        SELECT DISTINCT ?structure ?label_zh ?label_en ?part_of WHERE {{
          ?structure wdt:P31/wdt:P279* wd:Q4936952 .
          
          OPTIONAL {{ 
            ?structure rdfs:label ?label_zh . 
            FILTER(LANG(?label_zh) = "zh") 
          }}
          OPTIONAL {{ 
            ?structure rdfs:label ?label_en . 
            FILTER(LANG(?label_en) = "en") 
          }}
          OPTIONAL {{ ?structure wdt:P361 ?part_of . }}
          
          FILTER(EXISTS {{
            ?structure rdfs:label ?anyLabel .
            FILTER(LANG(?anyLabel) IN ("zh", "en"))
          }})
        }}
        LIMIT {limit}
        OFFSET {offset}
        """
        
        results = self.execute_sparql(query)
        if not results:
            return 0
        
        count = 0
        bindings = results.get('results', {}).get('bindings', [])
        
        for binding in bindings:
            structure_uri = binding.get('structure', {}).get('value')
            if not structure_uri:
                continue
            
            subject = URIRef(structure_uri)
            self.graph.add((subject, RDF.type, MED.BodyPart))
            
            if 'label_zh' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_zh']['value'], lang='zh'
                )))
            
            if 'label_en' in binding:
                self.graph.add((subject, RDFS.label, Literal(
                    binding['label_en']['value'], lang='en'
                )))
            
            if 'part_of' in binding:
                part_of_uri = binding['part_of']['value']
                self.graph.add((subject, WDT.P361, URIRef(part_of_uri)))
            
            count += 1
        
        logger.info(f"✓ 导出解剖结构: {count} 个")
        return count
    
    def export_category(self, category_name: str, category_func, max_items: Optional[int] = None):
        """
        通用分类导出方法
        
        Args:
            category_name: 分类名称（用于日志）
            category_func: 导出函数
            max_items: 最大导出数量
        """
        offset = self.stats.get(f'{category_name}_offset', 0)
        total = 0
        
        logger.info(f"开始导出 {category_name}...")
        
        with tqdm(desc=f"导出 {category_name}", unit="batch") as pbar:
            while True:
                if max_items and total >= max_items:
                    logger.info(f"{category_name} 已达到最大限制: {max_items}")
                    break
                
                count = category_func(self.batch_size, offset)
                
                if count == 0:
                    logger.info(f"{category_name} 导出完成")
                    break
                
                offset += self.batch_size
                total += count
                
                # 更新统计
                self.stats[f'{category_name}_offset'] = offset
                self.stats[category_name] = total
                self.stats['total_exported'] += count
                
                # 定期保存状态
                if offset % (self.batch_size * 5) == 0:
                    self._save_state()
                    logger.info(f"已保存状态，当前 {category_name} offset={offset}")
                
                pbar.update(1)
                pbar.set_postfix({'total': total, 'triples': len(self.graph)})
                
                # 延迟避免 API 限制
                time.sleep(1)
    
    def export_all(self, max_total: Optional[int] = None):
        """
        导出所有数据
        
        Args:
            max_total: 最大导出总数（用于测试）
        """
        logger.info("=" * 60)
        logger.info("开始导出 Wikidata 医学数据")
        logger.info("=" * 60)
        
        start_time = time.time()
        
        try:
            # 1. 导出疾病实体
            self.export_category('diseases', self.export_diseases, max_total)
            
            # 2. 导出疾病关系
            self.export_category('disease_relations', self.export_disease_relations, max_total)
            
            # 3. 导出药物实体
            self.export_category('drugs', self.export_drugs, max_total)
            
            # 4. 导出药物关系
            self.export_category('drug_relations', self.export_drug_relations, max_total)
            
            # 5. 导出解剖结构
            self.export_category('anatomy', self.export_anatomy, max_total)
            
        except KeyboardInterrupt:
            logger.warning("用户中断，正在保存当前进度...")
            self._save_state()
            logger.info("进度已保存，可以使用 --resume 参数继续")
            sys.exit(0)
        except Exception as e:
            logger.error(f"导出过程中出错: {e}", exc_info=True)
            self._save_state()
            raise
        
        # 计算用时
        elapsed = time.time() - start_time
        
        logger.info("=" * 60)
        logger.info("导出完成！")
        logger.info(f"总用时: {elapsed:.2f} 秒 ({elapsed/60:.2f} 分钟)")
        logger.info(f"总三元组数: {len(self.graph)}")
        logger.info(f"疾病实体: {self.stats.get('diseases', 0)}")
        logger.info(f"药物实体: {self.stats.get('drugs', 0)}")
        logger.info(f"解剖结构: {self.stats.get('anatomy', 0)}")
        logger.info("=" * 60)
    
    def save_to_file(self):
        """保存到 TTL 文件"""
        logger.info(f"保存到文件: {self.output_file}")
        
        # 添加元数据
        metadata_uri = URIRef("http://example.org/wikidata-medical-export")
        self.graph.add((metadata_uri, RDF.type, SCHEMA.Dataset))
        self.graph.add((metadata_uri, SCHEMA.name, Literal("Wikidata Medical Data Export", lang='en')))
        self.graph.add((metadata_uri, SCHEMA.description, Literal("医学知识图谱数据（来自 Wikidata）", lang='zh')))
        self.graph.add((metadata_uri, SCHEMA.dateCreated, Literal(
            datetime.now().isoformat(), datatype=XSD.dateTime
        )))
        self.graph.add((metadata_uri, SCHEMA.creator, Literal("Wikidata TTL Exporter")))
        
        # 序列化为 TTL
        try:
            self.graph.serialize(
                destination=self.output_file,
                format='turtle',
                encoding='utf-8'
            )
            logger.info(f"✓ 文件保存成功: {self.output_file}")
            
            # 清理临时文件
            if os.path.exists(self.temp_file):
                os.remove(self.temp_file)
            if os.path.exists(self.state_file):
                os.remove(self.state_file)
            
            # 显示文件大小
            file_size = os.path.getsize(self.output_file)
            logger.info(f"文件大小: {file_size / 1024 / 1024:.2f} MB")
            
        except Exception as e:
            logger.error(f"保存文件失败: {e}")
            raise
 
 
def test_connection(proxy: Optional[str] = None, endpoint_index: int = 0):
    """
    测试连接到 Wikidata
    
    Args:
        proxy: 代理地址
        endpoint_index: 端点索引
    """
    logger.info("测试连接到 Wikidata...")
    
    exporter = WikidataTTLExporter(
        output_file='test.ttl',
        batch_size=10,
        proxy=proxy,
        endpoint_index=endpoint_index
    )
    
    # 简单的测试查询
    test_query = """
    PREFIX wd: <http://www.wikidata.org/entity/>
    PREFIX wdt: <http://www.wikidata.org/prop/direct/>
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    
    SELECT ?disease ?label WHERE {
      ?disease wdt:P31 wd:Q12136 .
      ?disease rdfs:label ?label .
      FILTER(LANG(?label) = "zh")
    }
    LIMIT 5
    """
    
    result = exporter.execute_sparql(test_query)
    
    if result:
        bindings = result.get('results', {}).get('bindings', [])
        logger.info(f"✓ 连接成功！查询到 {len(bindings)} 条结果")
        for binding in bindings:
            label = binding.get('label', {}).get('value', 'N/A')
            logger.info(f"  - {label}")
        return True
    else:
        logger.error("✗ 连接失败")
        return False
 
 
def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='Wikidata 医学数据导出为 TTL 格式',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 基本用法
  python export_wikidata_ttl.py
  
  # 使用代理
  python export_wikidata_ttl.py --proxy http://127.0.0.1:7890
  
  # 使用 QLever 镜像（推荐）
  python export_wikidata_ttl.py --endpoint 0
  
  # 导出测试数据
  python export_wikidata_ttl.py --max-total 100 --output test.ttl
  
  # 从中断处继续
  python export_wikidata_ttl.py --resume
  
  # 测试连接
  python export_wikidata_ttl.py --test-connection
        """
    )
    
    parser.add_argument('--output', '-o', 
                       default='wikidata_medical.ttl',
                       help='输出 TTL 文件路径 (默认: wikidata_medical.ttl)')
    
    parser.add_argument('--batch-size', '-b', 
                       type=int, default=3000,
                       help='每批处理的数据量 (默认: 3000)')
    
    parser.add_argument('--max-total', '-m', 
                       type=int, default=None,
                       help='最大导出总数，用于测试 (默认: 无限制)')
    
    parser.add_argument('--proxy', '-p', 
                       default=None,
                       help='代理地址，例如: http://127.0.0.1:7890 或 socks5://127.0.0.1:1080')
    
    parser.add_argument('--endpoint', '-e', 
                       type=int, default=0,
                       choices=[0, 1, 2],
                       help='选择端点: 0=QLever(推荐), 1=官方, 2=Orb (默认: 0)')
    
    parser.add_argument('--resume', '-r',
                       action='store_true',
                       help='从上次中断处继续导出')
    
    parser.add_argument('--test-connection', '-t',
                       action='store_true',
                       help='测试连接到 Wikidata')
    
    args = parser.parse_args()
    
    # 测试连接
    if args.test_connection:
        success = test_connection(args.proxy, args.endpoint)
        sys.exit(0 if success else 1)
    
    # 显示配置
    logger.info("=" * 60)
    logger.info("配置信息:")
    logger.info(f"  输出文件: {args.output}")
    logger.info(f"  批次大小: {args.batch_size}")
    logger.info(f"  最大导出: {args.max_total if args.max_total else '无限制'}")
    logger.info(f"  代理: {args.proxy if args.proxy else '无'}")
    logger.info(f"  端点: {WikidataTTLExporter.ENDPOINTS[args.endpoint]['name']}")
    logger.info(f"  断点续传: {'是' if args.resume else '否'}")
    logger.info("=" * 60)
    
    # 创建导出器
    exporter = WikidataTTLExporter(
        output_file=args.output,
        batch_size=args.batch_size,
        proxy=args.proxy,
        endpoint_index=args.endpoint,
        resume=args.resume
    )
    
    # 导出所有数据
    exporter.export_all(max_total=args.max_total)
    
    # 保存到文件
    exporter.save_to_file()
    
    logger.info(f"\n✓ 导出成功！文件保存到: {args.output}")
 
 
if __name__ == "__main__":
    main()
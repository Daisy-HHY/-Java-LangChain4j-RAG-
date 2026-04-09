package com.kgqa.service.sparql;

import com.kgqa.service.MedQAImportService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从医学文本中提取三元组
 * 用于构建知识图谱，支持 SPARQL 查询
 */
@Service
public class TripleExtractor {

    // 医学实体类型
    public static final String TYPE_DRUG = "药物";
    public static final String TYPE_DISEASE = "疾病";
    public static final String TYPE_SYMPTOM = "症状";
    public static final String TYPE_TREATMENT = "治疗";
    public static final String TYPE_TEST = "检验";

    // 常见医学实体词典（简化版，实际应用中应使用更大的词典或 NER 模型）
    private static final Set<String> DRUG_KEYWORDS = Set.of(
            "阿司匹林", "布洛芬", "青霉素", "头孢", "胰岛素", "硝酸甘油",
            "肾上腺素", "去甲肾上腺素", "多巴胺", "地塞米松", "泼尼松",
            "阿莫西林", "红霉素", "氯霉素", "庆大霉素", "链霉素",
            "吗啡", "哌替啶", "芬太尼", "可待因", "氯丙嗪",
            "安定", "地西泮", "苯巴比妥", "苯妥英钠", "卡马西平",
            "利福平", "异烟肼", "乙胺丁醇", "吡嗪酰胺",
            "氯硝西泮", "阿普唑仑", "劳拉西泮",
            "硝苯地平", "卡托普利", "依那普利", "氯沙坦", "缬沙坦",
            "美托洛尔", "普萘洛尔", "阿替洛尔", "氨氯地平", "维拉帕米",
            "呋塞米", "氢氯噻嗪", "螺内酯", "甘露醇",
            "奥美拉唑", "西咪替丁", "雷尼替丁", "法莫替丁",
            "甲硝唑", "替硝唑", "奥硝唑",
            "强的松", "甲泼尼龙", "曲安奈德",
            "二甲双胍", "格列本脲", "格列吡嗪",
            "他汀", "洛伐他汀", "辛伐他汀", "阿托伐他汀", "瑞舒伐他汀",
            "华法林", "肝素", "氯吡格雷", "替格瑞洛",
            "麻黄碱", "伪麻黄碱", "对乙酰氨基酚", "咖啡因",
            "维生素", "钙片", "铁剂", "叶酸"
    );

    private static final Set<String> DISEASE_KEYWORDS = Set.of(
            "心肌梗死", "心梗", "冠心病", "心绞痛", "心力衰竭", "心律失常",
            "高血压", "低血压", "动脉粥样硬化", "先天性心脏病",
            "肺炎", "支气管炎", "哮喘", "COPD", "肺结核", "肺癌",
            "胃炎", "胃溃疡", "十二指肠溃疡", "胃癌", "肝炎", "肝硬化", "肝癌",
            "胆囊炎", "胆结石", "胰腺炎", "阑尾炎",
            "肾小球肾炎", "肾盂肾炎", "肾功能衰竭", "尿毒症",
            "糖尿病", "甲亢", "甲减", "肾上腺疾病", "垂体疾病",
            "脑卒中", "脑梗死", "脑出血", "癫痫", "帕金森", "老年痴呆",
            "贫血", "白血病", "淋巴瘤", "紫癜", "血友病",
            "系统性红斑狼疮", "类风湿关节炎", "干燥综合征", "硬皮病",
            "抑郁症", "精神分裂症", "焦虑症", "强迫症", "躁狂症",
            "骨折", "骨质疏松", "骨关节炎", "腰椎间盘突出", "颈椎病"
    );

    private static final Set<String> SYMPTOM_KEYWORDS = Set.of(
            "发热", "头痛", "头晕", "胸痛", "胸闷", "心悸", "呼吸困难",
            "咳嗽", "咳痰", "咯血", "恶心", "呕吐", "腹痛", "腹胀",
            "腹泻", "便秘", "便血", "黄疸", "水肿", "体重下降",
            "乏力", "盗汗", "皮疹", "瘙痒", "出血", "昏迷", "抽搐"
    );

    /**
     * 从 QA 题目中提取三元组
     */
    public List<Triple> extractFromQA(MedQAImportService.MedQAItem item) {
        List<Triple> triples = new ArrayList<>();

        String question = item.question != null ? item.question : "";
        String answer = item.answer != null ? item.answer : "";

        // 提取题目中的实体
        Set<String> drugs = extractEntities(question, DRUG_KEYWORDS);
        Set<String> diseases = extractEntities(question, DISEASE_KEYWORDS);
        Set<String> symptoms = extractEntities(question, SYMPTOM_KEYWORDS);

        // 从答案和选项中提取实体
        if (item.options != null) {
            for (String opt : item.options) {
                drugs.addAll(extractEntities(opt, DRUG_KEYWORDS));
                diseases.addAll(extractEntities(opt, DISEASE_KEYWORDS));
                symptoms.addAll(extractEntities(opt, SYMPTOM_KEYWORDS));
            }
        }
        drugs.addAll(extractEntities(answer, DRUG_KEYWORDS));
        diseases.addAll(extractEntities(answer, DISEASE_KEYWORDS));
        symptoms.addAll(extractEntities(answer, SYMPTOM_KEYWORDS));

        // 构建三元组
        // 题目 - 考察 - 知识点
        if (item.meta_info != null && !item.meta_info.isEmpty()) {
            for (String drug : drugs) {
                triples.add(new Triple(drug, "相关治疗", item.meta_info));
            }
            for (String disease : diseases) {
                triples.add(new Triple(disease, "相关知识", item.meta_info));
            }
        }

        // 从题目文本中提取关系
        extractRelationsFromText(question, drugs, diseases, symptoms, triples);

        return triples;
    }

    /**
     * 从文本中提取关系三元组
     */
    private void extractRelationsFromText(String text, Set<String> drugs,
                                         Set<String> diseases, Set<String> symptoms,
                                         List<Triple> triples) {
        // 适应症关系
        if (text.contains("适应症") || text.contains("治疗")) {
            for (String drug : drugs) {
                for (String disease : diseases) {
                    if (text.contains(drug) && text.contains(disease)) {
                        triples.add(new Triple(drug, "适应症", disease));
                    }
                }
            }
        }

        // 副作用关系
        if (text.contains("副作用") || text.contains("不良反应")) {
            for (String drug : drugs) {
                for (String symptom : symptoms) {
                    if (text.contains(drug) && text.contains(symptom)) {
                        triples.add(new Triple(drug, "副作用", symptom));
                    }
                }
            }
        }

        // 症状关系
        if (text.contains("症状") || text.contains("表现为")) {
            for (String disease : diseases) {
                for (String symptom : symptoms) {
                    if (text.contains(disease) && text.contains(symptom)) {
                        triples.add(new Triple(disease, "症状", symptom));
                    }
                }
            }
        }
    }

    /**
     * 从文本中提取实体
     */
    private Set<String> extractEntities(String text, Set<String> dictionary) {
        Set<String> found = new HashSet<>();
        if (text == null || text.isEmpty()) return found;

        for (String entity : dictionary) {
            if (text.contains(entity)) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * 三元组
     */
    public static class Triple {
        private final String subject;
        private final String predicate;
        private final String object;

        public Triple(String subject, String predicate, String object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        public String getSubject() { return subject; }
        public String getPredicate() { return predicate; }
        public String getObject() { return object; }

        @Override
        public String toString() {
            return subject + " 的 " + predicate + " 是 " + object;
        }

        /**
         * 转换为自然语言（用于向量存储）
         */
        public String toNaturalLanguage() {
            return toString();
        }

        /**
         * 转换为 SPARQL 格式
         */
        public String toSPARQL() {
            return String.format("SELECT ?%s WHERE { <%s> <%s> ?%s }",
                    "obj", subject, predicate, "obj");
        }
    }
}

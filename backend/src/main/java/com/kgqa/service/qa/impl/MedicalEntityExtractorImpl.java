package com.kgqa.service.qa.impl;

import com.kgqa.service.qa.MedicalEntityExtractor;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医学实体抽取服务实现
 * 从用户问题中识别药物、疾病、症状等实体
 */
@Service
public class MedicalEntityExtractorImpl implements MedicalEntityExtractor {

    private final ChatModel chatModel;

    // 常见中文医学实体词典
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
            "骨折", "骨质疏松", "骨关节炎", "腰椎间盘突出", "颈椎病",
            "感冒", "流感", "发热", "咳嗽", "腹泻", "便秘",
            "鼻炎", "咽炎", "喉炎", "中耳炎", "结膜炎",
            "湿疹", "荨麻疹", "银屑病", "白癜风",
            "痛风", "风湿热", "强直性脊柱炎"
    );

    private static final Set<String> SYMPTOM_KEYWORDS = Set.of(
            "发热", "发烧", "头痛", "头晕", "胸痛", "胸闷", "心悸", "呼吸困难",
            "咳嗽", "咳痰", "咯血", "恶心", "呕吐", "腹痛", "腹胀",
            "腹泻", "便秘", "便血", "黄疸", "水肿", "体重下降",
            "乏力", "盗汗", "皮疹", "瘙痒", "出血", "昏迷", "抽搐",
            "麻木", "刺痛", "疼痛", "酸痛", "胀痛", "绞痛",
            "失眠", "嗜睡", "焦虑", "抑郁", "意识障碍"
    );

    // 提示词
    private static final String EXTRACT_PROMPT = """
            你是一个医学实体识别助手。请从以下用户问题中提取医学实体。

            实体类型：
            - 药物：药物名称，如"阿司匹林"、"布洛芬"、"青霉素"
            - 疾病：疾病名称，如"高血压"、"糖尿病"、"心肌梗死"
            - 症状：症状描述，如"头痛"、"发热"、"胸闷"

            请按以下 JSON 格式返回：
            {
              "entities": [
                {"type": "药物", "value": "实体名称"},
                {"type": "疾病", "value": "实体名称"}
              ],
              "intent": "用户的查询意图，如"查询适应症"、"查询副作用""
            }

            如果没有识别到实体，返回空列表。

            用户问题：%s
            """;

    public MedicalEntityExtractorImpl(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 提取医学实体
     */
    @Override
    public ExtractionResult extract(String question) {
        if (question == null || question.trim().isEmpty()) {
            return new ExtractionResult(List.of(), "未知");
        }

        // 首先使用词典进行快速匹配
        List<MedicalEntity> entities = extractByDictionary(question);

        // 如果词典匹配结果为空，使用 LLM 增强提取
        if (entities.isEmpty()) {
            entities = extractByLLM(question);
        }

        // 推断意图
        String intent = inferIntent(question);

        return new ExtractionResult(entities, intent);
    }

    /**
     * 使用词典快速提取实体
     */
    private List<MedicalEntity> extractByDictionary(String question) {
        List<MedicalEntity> entities = new ArrayList<>();
        Set<String> found = new HashSet<>();

        // 提取药物
        for (String drug : DRUG_KEYWORDS) {
            if (question.contains(drug) && !found.contains(drug)) {
                entities.add(new MedicalEntity("药物", drug));
                found.add(drug);
            }
        }

        // 提取疾病
        for (String disease : DISEASE_KEYWORDS) {
            if (question.contains(disease) && !found.contains(disease)) {
                entities.add(new MedicalEntity("疾病", disease));
                found.add(disease);
            }
        }

        // 提取症状
        for (String symptom : SYMPTOM_KEYWORDS) {
            if (question.contains(symptom) && !found.contains(symptom)) {
                entities.add(new MedicalEntity("症状", symptom));
                found.add(symptom);
            }
        }

        return entities;
    }

    /**
     * 使用 LLM 提取实体
     */
    private List<MedicalEntity> extractByLLM(String question) {
        try {
            String prompt = String.format(EXTRACT_PROMPT, question);
            String response = chatModel.chat(prompt).trim();

            // 简单解析 JSON 响应
            List<MedicalEntity> entities = new ArrayList<>();

            // 提取药物
            Pattern drugPattern = Pattern.compile("\"([^\"]+)\".*?药物");
            Matcher drugMatcher = drugPattern.matcher(response);
            while (drugMatcher.find()) {
                entities.add(new MedicalEntity("药物", drugMatcher.group(1)));
            }

            // 提取疾病
            Pattern diseasePattern = Pattern.compile("\"([^\"]+)\".*?疾病");
            Matcher diseaseMatcher = diseasePattern.matcher(response);
            while (diseaseMatcher.find()) {
                entities.add(new MedicalEntity("疾病", diseaseMatcher.group(1)));
            }

            return entities;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 从问题中推断意图
     */
    private String inferIntent(String question) {
        String q = question.toLowerCase();

        if (q.contains("适应症") || q.contains("治疗")) {
            return "查询适应症";
        }
        if (q.contains("副作用") || q.contains("不良反应")) {
            return "查询副作用";
        }
        if (q.contains("禁忌") || q.contains("注意事项")) {
            return "查询禁忌";
        }
        if (q.contains("症状") || q.contains("表现")) {
            return "查询症状";
        }
        if (q.contains("诊断") || q.contains("检查")) {
            return "查询诊断";
        }
        if (q.contains("用法") || q.contains("剂量")) {
            return "查询用法";
        }
        if (q.contains("机制") || q.contains("原理")) {
            return "查询机制";
        }
        if (q.contains("区别") || q.contains("不同")) {
            return "比较";
        }

        return "一般查询";
    }
}

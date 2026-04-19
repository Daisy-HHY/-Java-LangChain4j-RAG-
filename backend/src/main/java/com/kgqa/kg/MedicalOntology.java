package com.kgqa.kg;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * 医疗知识图谱本体常量
 * 支持两种本体：
 * 1. kgqa 本体 (http://kgqa.com/medical#)
 * 2. kgdrug 本体 (http://www.kgdrug.com)
 */
public final class MedicalOntology {

    // ===== kgdrug 本体 (YeYzheng 项目) =====
    public static final String KGDRUG_BASE = "http://www.kgdrug.com";
    public static final Resource KGDRUG_DISEASE = ResourceFactory.createResource(KGDRUG_BASE + "#disease");
    public static final Resource KGDRUG_DRUG = ResourceFactory.createResource(KGDRUG_BASE + "#drug");
    public static final Resource KGDRUG_SYMPTOM = ResourceFactory.createResource(KGDRUG_BASE + "#symptom");

    // kgdrug 关系属性
    public static final Property KGDRUG_HASZHENGZHUANG = ResourceFactory.createProperty(KGDRUG_BASE + "#haszhengzhuang");
    public static final Property KGDRUG_RELATEDISEASE = ResourceFactory.createProperty(KGDRUG_BASE + "#relatedisease");
    public static final Property KGDRUG_NEEDCURE = ResourceFactory.createProperty(KGDRUG_BASE + "#needcure");
    public static final Property KGDRUG_CURE = ResourceFactory.createProperty(KGDRUG_BASE + "#cure");

    // kgdrug 数据属性
    public static final Property KGDRUG_JIBINGNAME = ResourceFactory.createProperty(KGDRUG_BASE + "#jibingname");
    public static final Property KGDRUG_PRONAME = ResourceFactory.createProperty(KGDRUG_BASE + "#proname");
    public static final Property KGDRUG_ZZNAME = ResourceFactory.createProperty(KGDRUG_BASE + "#zzname");
    public static final Property KGDRUG_BINGYIN = ResourceFactory.createProperty(KGDRUG_BASE + "#bingyin");
    public static final Property KGDRUG_BINGFAZHENG = ResourceFactory.createProperty(KGDRUG_BASE + "#bingfazheng");
    public static final Property KGDRUG_ZHILIAO = ResourceFactory.createProperty(KGDRUG_BASE + "#zhiliao");
    public static final Property KGDRUG_YUFANG = ResourceFactory.createProperty(KGDRUG_BASE + "#yufang");
    public static final Property KGDRUG_GAISHU = ResourceFactory.createProperty(KGDRUG_BASE + "#gaishu");
    public static final Property KGDRUG_GAZHZH = ResourceFactory.createProperty(KGDRUG_BASE + "#gazhzh");

    // ===== kgqa 本体 (原有) =====
    public static final String KGQA_BASE = "http://kgqa.com/medical#";
    public static final Resource DISEASE = ResourceFactory.createResource(KGQA_BASE + "Disease");
    public static final Resource SYMPTOM = ResourceFactory.createResource(KGQA_BASE + "Symptom");
    public static final Resource DRUG = ResourceFactory.createResource(KGQA_BASE + "Drug");
    public static final Resource DEPARTMENT = ResourceFactory.createResource(KGQA_BASE + "Department");
    public static final Resource EXAMINATION = ResourceFactory.createResource(KGQA_BASE + "Examination");
    public static final Resource BODY_PART = ResourceFactory.createResource(KGQA_BASE + "BodyPart");
    public static final Resource TREATMENT = ResourceFactory.createResource(KGQA_BASE + "Treatment");

    // kgqa 关系属性
    public static final Property HAS_SYMPTOM = ResourceFactory.createProperty(KGQA_BASE + "hasSymptom");
    public static final Property TREATED_BY = ResourceFactory.createProperty(KGQA_BASE + "treatedBy");
    public static final Property BELONGS_TO_DEPT = ResourceFactory.createProperty(KGQA_BASE + "belongsToDept");
    public static final Property CAUSED_BY = ResourceFactory.createProperty(KGQA_BASE + "causedBy");
    public static final Property COMPLICATION_OF = ResourceFactory.createProperty(KGQA_BASE + "complicationOf");
    public static final Property INDICATION = ResourceFactory.createProperty(KGQA_BASE + "indication");
    public static final Property SIDE_EFFECT = ResourceFactory.createProperty(KGQA_BASE + "sideEffect");
    public static final Property CONTRAINDICATED_WITH = ResourceFactory.createProperty(KGQA_BASE + "contraindicatedWith");
    public static final Property REQUIRES_EXAM = ResourceFactory.createProperty(KGQA_BASE + "requiresExam");
    public static final Property LOCATED_IN = ResourceFactory.createProperty(KGQA_BASE + "locatedIn");

    // ===== Wikidata 属性 URI =====
    public static final String WDT_BASE = "http://www.wikidata.org/prop/direct/";
    public static final Property WDT_P780 = ResourceFactory.createProperty(WDT_BASE + "P780");     // symptom
    public static final Property WDT_P2176 = ResourceFactory.createProperty(WDT_BASE + "P2176");     // treated by drug
    public static final Property WDT_P828 = ResourceFactory.createProperty(WDT_BASE + "P828");       // cause
    public static final Property WDT_P1542 = ResourceFactory.createProperty(WDT_BASE + "P1542");    // complication
    public static final Property WDT_P1995 = ResourceFactory.createProperty(WDT_BASE + "P1995");    // medical specialty

    // ===== 工具方法 =====
    public static Resource createEntityResource(String name, Resource type) {
        String localName = name.trim()
                .replaceAll("[\\s/()]+", "_")
                .replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
        String typePrefix = type.getLocalName().toLowerCase().substring(0, 3);
        return ResourceFactory.createResource(DATA_URI + typePrefix + "_" + localName);
    }

    public static final String DATA_URI = "http://kgqa.com/data#";

    private MedicalOntology() {}
}

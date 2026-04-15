package com.kgqa.kg;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * 医疗知识图谱本体常量
 * 所有实体类型和关系的 URI 定义集中在此
 */
public final class MedicalOntology {

    public static final String BASE_URI = "http://kgqa.com/medical#";
    public static final String DATA_URI = "http://kgqa.com/data#";

    // ===== 实体类型 =====
    public static final Resource DISEASE =
            ResourceFactory.createResource(BASE_URI + "Disease");
    public static final Resource SYMPTOM =
            ResourceFactory.createResource(BASE_URI + "Symptom");
    public static final Resource DRUG =
            ResourceFactory.createResource(BASE_URI + "Drug");
    public static final Resource DEPARTMENT =
            ResourceFactory.createResource(BASE_URI + "Department");
    public static final Resource EXAMINATION =
            ResourceFactory.createResource(BASE_URI + "Examination");
    public static final Resource BODY_PART =
            ResourceFactory.createResource(BASE_URI + "BodyPart");
    public static final Resource TREATMENT =
            ResourceFactory.createResource(BASE_URI + "Treatment");

    // ===== 关系属性 =====
    // 疾病相关
    public static final Property HAS_SYMPTOM =
            ResourceFactory.createProperty(BASE_URI + "hasSymptom");
    public static final Property TREATED_BY =
            ResourceFactory.createProperty(BASE_URI + "treatedBy");
    public static final Property BELONGS_TO_DEPT =
            ResourceFactory.createProperty(BASE_URI + "belongsToDept");
    public static final Property REQUIRES_EXAM =
            ResourceFactory.createProperty(BASE_URI + "requiresExam");
    public static final Property LOCATED_IN =
            ResourceFactory.createProperty(BASE_URI + "locatedIn");
    public static final Property CAUSED_BY =
            ResourceFactory.createProperty(BASE_URI + "causedBy");
    public static final Property COMPLICATION_OF =
            ResourceFactory.createProperty(BASE_URI + "complicationOf");

    // 药物相关
    public static final Property INDICATION =
            ResourceFactory.createProperty(BASE_URI + "indication");
    public static final Property SIDE_EFFECT =
            ResourceFactory.createProperty(BASE_URI + "sideEffect");
    public static final Property CONTRAINDICATED_WITH =
            ResourceFactory.createProperty(BASE_URI + "contraindicatedWith");
    public static final Property DRUG_CLASS =
            ResourceFactory.createProperty(BASE_URI + "drugClass");
    public static final Property MECHANISM_OF_ACTION =
            ResourceFactory.createProperty(BASE_URI + "mechanismOfAction");

    // ===== Wikidata 属性 URI (用于 SPARQL 查询) =====
    public static final String WDT_BASE = "http://www.wikidata.org/prop/direct/";
    public static final Property WDT_P31 =      // instance of
            ResourceFactory.createProperty(WDT_BASE + "P31");
    public static final Property WDT_P780 =      // symptom
            ResourceFactory.createProperty(WDT_BASE + "P780");
    public static final Property WDT_P2176 =    // treated by / drug
            ResourceFactory.createProperty(WDT_BASE + "P2176");
    public static final Property WDT_P828 =     // cause
            ResourceFactory.createProperty(WDT_BASE + "P828");
    public static final Property WDT_P1542 =    // complication
            ResourceFactory.createProperty(WDT_BASE + "P1542");
    public static final Property WDT_P923 =     // diagnostic test
            ResourceFactory.createProperty(WDT_BASE + "P923");
    public static final Property WDT_P1995 =    // specialty (medical specialty)
            ResourceFactory.createProperty(WDT_BASE + "P1995");
    public static final Property WDT_P927 =     // located in (body location)
            ResourceFactory.createProperty(WDT_BASE + "P927");

    // ===== Wikidata 实体 URI =====
    public static final String WD_ENTITY = "http://www.wikidata.org/entity/";
    public static final Resource WD_DISEASE =    // Q12136
            ResourceFactory.createResource(WD_ENTITY + "Q12136");
    public static final Resource WD_DRUG =      // Q12140
            ResourceFactory.createResource(WD_ENTITY + "Q12140");

    // ===== 工具方法 =====
    // 根据实体名称生成 URI（中英文兼容）
    public static Resource createEntityResource(String name, Resource type) {
        String localName = name.trim()
                .replaceAll("[\\s/()]+", "_")
                .replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "");
        String typePrefix = type.getLocalName().toLowerCase().substring(0, 3);
        return ResourceFactory.createResource(DATA_URI + typePrefix + "_" + localName);
    }

    private MedicalOntology() {}
}

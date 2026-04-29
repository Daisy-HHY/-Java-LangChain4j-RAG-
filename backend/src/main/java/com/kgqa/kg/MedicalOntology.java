package com.kgqa.kg;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * kgdrug 医疗知识图谱本体常量。
 */
public final class MedicalOntology {

    public static final String KGDRUG_BASE = "http://www.kgdrug.com";
    public static final String KGDRUG_NS = KGDRUG_BASE + "#";

    public static final Resource DISEASE = ResourceFactory.createResource(KGDRUG_NS + "disease");
    public static final Resource DRUG = ResourceFactory.createResource(KGDRUG_NS + "drug");
    public static final Resource SYMPTOM = ResourceFactory.createResource(KGDRUG_NS + "symptom");

    public static final Property HAS_SYMPTOM = property("haszhengzhuang");
    public static final Property RELATED_DISEASE = property("relatedisease");
    public static final Property NEED_CURE = property("needcure");
    public static final Property CURE = property("cure");

    public static final Property DISEASE_NAME = property("jibingname");
    public static final Property DRUG_NAME = property("proname");
    public static final Property SYMPTOM_NAME = property("zzname");
    public static final Property CAUSE = property("bingyin");
    public static final Property COMPLICATION = property("bingfazheng");
    public static final Property TREATMENT = property("zhiliao");
    public static final Property PREVENTION = property("yufang");
    public static final Property OVERVIEW = property("gaishu");
    public static final Property DRUG_USAGE = property("gazhzh");
    public static final Property APPROVAL_NUMBER = property("pzwh");

    private MedicalOntology() {
    }

    private static Property property(String localName) {
        return ResourceFactory.createProperty(KGDRUG_NS + localName);
    }
}

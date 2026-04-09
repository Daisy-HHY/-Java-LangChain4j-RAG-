package com.kgqa.kg;

import com.kgqa.kg.TripleExtractor.Triple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱构建器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraphBuilder {

    private final TdbManager tdbManager;

    // 实体类型名称 → 本体 Resource 的映射
    private static final Map<String, Resource> TYPE_MAP = Map.of(
            "Disease", MedicalOntology.DISEASE,
            "Symptom", MedicalOntology.SYMPTOM,
            "Drug", MedicalOntology.DRUG,
            "Department", MedicalOntology.DEPARTMENT,
            "Examination", MedicalOntology.EXAMINATION,
            "BodyPart", MedicalOntology.BODY_PART,
            "Treatment", MedicalOntology.TREATMENT
    );

    // 关系名称 → 本体 Property 的映射
    private static final Map<String, Property> PRED_MAP = Map.of(
            "hasSymptom", MedicalOntology.HAS_SYMPTOM,
            "treatedBy", MedicalOntology.TREATED_BY,
            "indication", MedicalOntology.INDICATION,
            "requiresExam", MedicalOntology.REQUIRES_EXAM,
            "locatedIn", MedicalOntology.LOCATED_IN,
            "belongsToDept", MedicalOntology.BELONGS_TO_DEPT,
            "contraindicatedWith", MedicalOntology.CONTRAINDICATED_WITH,
            "causedBy", MedicalOntology.CAUSED_BY,
            "complicationOf", MedicalOntology.COMPLICATION_OF,
            "sideEffect", MedicalOntology.SIDE_EFFECT
    );

    /**
     * 批量写入三元组
     */
    public int writeTriples(List<Triple> triples) {
        int[] successCount = {0};

        tdbManager.writeTransaction(model -> {
            for (Triple triple : triples) {
                try {
                    writeTriple(model, triple);
                    successCount[0]++;
                } catch (Exception e) {
                    log.warn("写入三元组失败: {} -[{}]-> {}，原因: {}",
                            triple.subject(), triple.predicate(), triple.object(),
                            e.getMessage());
                }
            }
        });

        log.info("写入三元组：共 {} 条，成功 {} 条", triples.size(), successCount[0]);
        return successCount[0];
    }

    private void writeTriple(Model model, Triple triple) {
        Resource subjectType = TYPE_MAP.get(triple.subjectType());
        Resource objectType = TYPE_MAP.get(triple.objectType());
        Property predicate = PRED_MAP.get(triple.predicate());

        if (subjectType == null || objectType == null || predicate == null) {
            log.debug("未知类型或关系，跳过: {}/{} -[{}]-> {}/{}",
                    triple.subject(), triple.subjectType(),
                    triple.predicate(),
                    triple.object(), triple.objectType());
            return;
        }

        Resource subjectNode = MedicalOntology.createEntityResource(triple.subject(), subjectType);
        subjectNode.inModel(model).addProperty(RDF.type, subjectType);
        subjectNode.inModel(model).addProperty(RDFS.label, triple.subject());

        Resource objectNode = MedicalOntology.createEntityResource(triple.object(), objectType);
        objectNode.inModel(model).addProperty(RDF.type, objectType);
        objectNode.inModel(model).addProperty(RDFS.label, triple.object());

        model.add(subjectNode, predicate, objectNode);
    }

    /**
     * 查询数据统计
     */
    public void printStats() {
        long total = tdbManager.countTriples();
        log.info("知识图谱当前三元组总数: {}", total);
    }
}

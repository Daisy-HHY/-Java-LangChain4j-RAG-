package com.kgqa.kg;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从 kgdrug TDB 中加载实体名称，用于自然语言问题的本地实体匹配。
 */
@Slf4j
@Component
public class KgdrugEntityDictionary {

    private static final int MAX_NAME_LENGTH = 80;

    private final TdbManager tdbManager;

    private List<String> diseases = List.of();
    private List<String> drugs = List.of();
    private List<String> symptoms = List.of();

    public KgdrugEntityDictionary(TdbManager tdbManager) {
        this.tdbManager = tdbManager;
    }

    @PostConstruct
    public void load() {
        diseases = loadNames(MedicalOntology.DISEASE_NAME.getURI());
        drugs = loadNames(MedicalOntology.DRUG_NAME.getURI());
        symptoms = loadNames(MedicalOntology.SYMPTOM_NAME.getURI());

        log.info("kgdrug 实体词典加载完成: 疾病 {} 个, 药品 {} 个, 症状 {} 个",
                diseases.size(), drugs.size(), symptoms.size());
    }

    public List<String> diseases() {
        return diseases;
    }

    public List<String> drugs() {
        return drugs;
    }

    public List<String> symptoms() {
        return symptoms;
    }

    private List<String> loadNames(String propertyUri) {
        String sparql = "SELECT DISTINCT ?name WHERE { ?s <" + propertyUri + "> ?name }";

        try {
            return tdbManager.readDatasetTransaction(dataset -> {
                List<String> names = new ArrayList<>();
                try (var qexec = QueryExecutionFactory.create(sparql, dataset)) {
                    ResultSet rs = qexec.execSelect();
                    while (rs.hasNext()) {
                        var node = rs.next().get("name");
                        if (node != null && node.isLiteral()) {
                            String name = node.asLiteral().getString().trim();
                            if (isUsableName(name)) {
                                names.add(name);
                            }
                        }
                    }
                }

                return names.stream()
                        .distinct()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .toList();
            });
        } catch (Exception e) {
            log.warn("加载 kgdrug 实体词典失败: {}", propertyUri, e);
            return List.of();
        }
    }

    private boolean isUsableName(String name) {
        return name != null
                && !name.isBlank()
                && name.length() <= MAX_NAME_LENGTH;
    }
}

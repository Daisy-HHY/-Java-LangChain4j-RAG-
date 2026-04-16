package com.kgqa.data;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Wikidata 数据导出器
 * 从 Wikidata SPARQL 端点下载疾病-症状等关系数据
 */
public class WikidataDataExporter {

    private static final Logger log = LoggerFactory.getLogger(WikidataDataExporter.class);

    // Wikidata SPARQL 端点
    private static final String WIKIDATA_SPARQL = "https://query.wikidata.org/sparql";

    // 命名空间
    private static final String WD = "http://www.wikidata.org/entity/";
    private static final String WDT = "http://www.wikidata.org/prop/direct/";
    private static final String SCHEMA = "http://schema.org/";

    // Wikidata 属性
    private static final Property P31 = ResourceFactory.createProperty(WDT + "P31");      // instance of
    private static final Property P780 = ResourceFactory.createProperty(WDT + "P780");    // symptom
    private static final Property P2176 = ResourceFactory.createProperty(WDT + "P2176");  // treated by drug
    private static final Property P828 = ResourceFactory.createProperty(WDT + "P828");    // cause
    private static final Property P923 = ResourceFactory.createProperty(WDT + "P923");    // diagnostic test
    private static final Property P1542 = ResourceFactory.createProperty(WDT + "P1542");    // complication
    private static final Property P1995 = ResourceFactory.createProperty(WDT + "P1995");  // medical specialty
    private static final Property P927 = ResourceFactory.createProperty(WDT + "P927");     // located in

    // 疾病类
    private static final Resource DISEASE = ResourceFactory.createResource(WD + "Q12136");

    // 输出目录
    private String outputPath;

    public WikidataDataExporter(String outputPath) {
        this.outputPath = outputPath;
    }

    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : "E:/Github_project/LangChain4j-KGQA/wikidata";

        WikidataDataExporter exporter = new WikidataDataExporter(outputPath);

        System.out.println("===== 开始从 Wikidata 导出数据 =====");
        System.out.println("输出目录: " + outputPath);

        // 导出各关系数据
        exporter.exportData("disease_symptom", buildSymptomQuery());
        exporter.exportData("disease_drug", buildDrugQuery());
        exporter.exportData("disease_cause", buildCauseQuery());
        exporter.exportData("disease_exam", buildExamQuery());
        exporter.exportData("disease_complication", buildComplicationQuery());
        exporter.exportData("disease_specialty", buildSpecialtyQuery());
        exporter.exportData("disease_location", buildLocationQuery());

        System.out.println("===== Wikidata 数据导出完成 =====");
    }

    /**
     * 导出疾病-症状关系
     */
    private static String buildSymptomQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?symptom ?symptomLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P780 ?symptom .
              ?disease rdfs:label ?diseaseLabel .
              ?symptom rdfs:label ?symptomLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?symptomLabel) = "zh-hans")
            }
            """;
    }

    private static String buildDrugQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?drug ?drugLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P2176 ?drug .
              ?disease rdfs:label ?diseaseLabel .
              ?drug rdfs:label ?drugLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?drugLabel) = "zh-hans")
            }
            """;
    }

    private static String buildCauseQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?cause ?causeLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P828 ?cause .
              ?disease rdfs:label ?diseaseLabel .
              ?cause rdfs:label ?causeLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?causeLabel) = "zh-hans")
            }
            """;
    }

    private static String buildExamQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?exam ?examLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P923 ?exam .
              ?disease rdfs:label ?diseaseLabel .
              ?exam rdfs:label ?examLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?examLabel) = "zh-hans")
            }
            """;
    }

    private static String buildComplicationQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?complication ?complicationLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P1542 ?complication .
              ?disease rdfs:label ?diseaseLabel .
              ?complication rdfs:label ?complicationLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?complicationLabel) = "zh-hans")
            }
            """;
    }

    private static String buildSpecialtyQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?specialty ?specialtyLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P1995 ?specialty .
              ?disease rdfs:label ?diseaseLabel .
              ?specialty rdfs:label ?specialtyLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?specialtyLabel) = "zh-hans")
            }
            """;
    }

    private static String buildLocationQuery() {
        return """
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

            SELECT ?disease ?diseaseLabel ?location ?locationLabel
            WHERE {
              ?disease wdt:P31 wd:Q12136 .
              ?disease wdt:P927 ?location .
              ?disease rdfs:label ?diseaseLabel .
              ?location rdfs:label ?locationLabel .
              FILTER(LANG(?diseaseLabel) = "zh-hans")
              FILTER(LANG(?locationLabel) = "zh-hans")
            }
            """;
    }

    /**
     * 导出数据到 TTL 文件
     */
    public void exportData(String fileName, String sparqlQuery) throws Exception {
        System.out.println("开始导出: " + fileName);

        // 执行 SPARQL 查询
        List<Triple> triples = executeSparql(sparqlQuery);

        if (triples.isEmpty()) {
            System.out.println(fileName + ": 无数据");
            return;
        }

        // 构建 Model
        Model model = ModelFactory.createDefaultModel();

        Set<String> diseaseUris = new HashSet<>();
        Map<String, String> labels = new HashMap<>();

        for (Triple triple : triples) {
            String subjectUri = triple.subject;
            String predicateUri = triple.predicate;
            String objectUri = triple.object;
            String objectLabel = triple.objectLabel;

            diseaseUris.add(subjectUri);
            diseaseUris.add(objectUri);

            Resource subject = model.createResource(subjectUri);
            Property predicate = model.createProperty(predicateUri);
            Resource object = model.createResource(objectUri);

            model.add(subject, predicate, object);
        }

        // 添加 labels
        for (String uri : diseaseUris) {
            String label = extractLabelFromUri(uri);
            Resource resource = model.createResource(uri);
            resource.addProperty(RDFS.label, model.createLiteral(label, "zh-hans"));
        }

        // 写入文件
        Path outputFile = Paths.get(outputPath, fileName + ".ttl");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {
            model.write(out, "TURTLE");
        }

        System.out.println(fileName + " 导出完成: " + triples.size() + " 条三元组 -> " + outputFile);
    }

    /**
     * 执行 SPARQL 查询
     */
    private List<Triple> executeSparql(String sparql) throws Exception {
        List<Triple> results = new ArrayList<>();

        String encodedQuery = URLEncoder.encode(sparql, StandardCharsets.UTF_8);
        String urlStr = WIKIDATA_SPARQL + "?query=" + encodedQuery;

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/sparql-results+json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; WikidataExporter/1.0)");

        try {
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode);
            }

            // 解析 JSON 结果
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // 简单解析 JSON（避免引入 JSON 库）
            String json = response.toString();
            results.addAll(parseSparqlResults(json, sparql));

        } finally {
            conn.disconnect();
        }

        return results;
    }

    /**
     * 解析 SPARQL JSON 结果
     */
    private List<Triple> parseSparqlResults(String json, String sparql) {
        List<Triple> triples = new ArrayList<>();

        // 简单字符串解析
        // 查找 subject, predicate, object 值
        int subjectBinding = -1, predicateBinding = -1, objectBinding = -1;
        int labelBinding = -1;
        int objectLabelBinding = -1;

        // 根据查询类型确定 binding 名称
        String type = determineQueryType(sparql);

        switch (type) {
            case "symptom":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P780\"");
                objectBinding = indexOf(json, "\"symptom\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"symptomLabel\"");
                break;
            case "drug":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P2176\"");
                objectBinding = indexOf(json, "\"drug\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"drugLabel\"");
                break;
            case "cause":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P828\"");
                objectBinding = indexOf(json, "\"cause\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"causeLabel\"");
                break;
            case "exam":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P923\"");
                objectBinding = indexOf(json, "\"exam\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"examLabel\"");
                break;
            case "complication":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P1542\"");
                objectBinding = indexOf(json, "\"complication\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"complicationLabel\"");
                break;
            case "specialty":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P1995\"");
                objectBinding = indexOf(json, "\"specialty\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"specialtyLabel\"");
                break;
            case "location":
                subjectBinding = indexOf(json, "\"disease\"");
                predicateBinding = indexOf(json, "\"P927\"");
                objectBinding = indexOf(json, "\"location\"");
                labelBinding = indexOf(json, "\"diseaseLabel\"");
                objectLabelBinding = indexOf(json, "\"locationLabel\"");
                break;
        }

        // 提取每个结果
        int resultStart = indexOf(json, "\"results\"");
        if (resultStart == -1) return triples;

        int bindingStart = indexOf(json, "\"bindings\"", resultStart);
        if (bindingStart == -1) return triples;

        int bindingEnd = indexOf(json, "]", bindingStart);
        if (bindingEnd == -1) bindingEnd = json.length();

        int pos = bindingStart;
        while (pos < bindingEnd) {
            int bindingItemStart = indexOf(json, "{", pos);
            if (bindingItemStart == -1 || bindingItemStart > bindingEnd) break;

            int bindingItemEnd = indexOf(json, "}", bindingItemStart);
            if (bindingItemEnd == -1) break;

            String bindingItem = json.substring(bindingItemStart, bindingItemEnd + 1);

            String subject = extractUriValue(bindingItem, "\"disease\"");
            String predicate = extractPredicateFromBinding(bindingItem);
            String object = extractUriValue(bindingItem, getObjectVar(type));
            String objectLabel = extractLabelValue(bindingItem, getObjectLabelVar(type));

            if (subject != null && predicate != null && object != null) {
                triples.add(new Triple(subject, predicate, object, objectLabel));
            }

            pos = bindingItemEnd + 1;
        }

        return triples;
    }

    private String determineQueryType(String sparql) {
        if (sparql.contains("P780")) return "symptom";
        if (sparql.contains("P2176")) return "drug";
        if (sparql.contains("P828")) return "cause";
        if (sparql.contains("P923")) return "exam";
        if (sparql.contains("P1542")) return "complication";
        if (sparql.contains("P1995")) return "specialty";
        if (sparql.contains("P927")) return "location";
        return "unknown";
    }

    private String getObjectVar(String type) {
        switch (type) {
            case "symptom": return "symptom";
            case "drug": return "drug";
            case "cause": return "cause";
            case "exam": return "exam";
            case "complication": return "complication";
            case "specialty": return "specialty";
            case "location": return "location";
            default: return "object";
        }
    }

    private String getObjectLabelVar(String type) {
        return getObjectVar(type) + "Label";
    }

    private String extractUriValue(String json, String varName) {
        int varPos = indexOf(json, varName);
        if (varPos == -1) return null;

        int valuePos = indexOf(json, "\"value\"", varPos);
        if (valuePos == -1) return null;

        int uriStart = indexOf(json, "<", valuePos);
        int uriEnd = indexOf(json, ">", uriStart);
        if (uriStart == -1 || uriEnd == -1) return null;

        return json.substring(uriStart + 1, uriEnd);
    }

    private String extractLabelValue(String json, String varName) {
        int varPos = indexOf(json, varName);
        if (varPos == -1) return null;

        int valuePos = indexOf(json, "\"value\"", varPos);
        if (valuePos == -1) return null;

        int textPos = indexOf(json, "\"xml:lang\"", valuePos);
        if (textPos == -1) return null;

        int quoteStart = indexOf(json, "\"", textPos + 20);
        int quoteEnd = indexOf(json, "\"", quoteStart + 1);
        if (quoteStart == -1 || quoteEnd == -1) return null;

        return json.substring(quoteStart + 1, quoteEnd);
    }

    private String extractPredicateFromBinding(String bindingItem) {
        // 从 P31 等确定 predicate
        if (indexOf(bindingItem, "\"P780\"") != -1) return WDT + "P780";
        if (indexOf(bindingItem, "\"P2176\"") != -1) return WDT + "P2176";
        if (indexOf(bindingItem, "\"P828\"") != -1) return WDT + "P828";
        if (indexOf(bindingItem, "\"P923\"") != -1) return WDT + "P923";
        if (indexOf(bindingItem, "\"P1542\"") != -1) return WDT + "P1542";
        if (indexOf(bindingItem, "\"P1995\"") != -1) return WDT + "P1995";
        if (indexOf(bindingItem, "\"P927\"") != -1) return WDT + "P927";
        return WDT + "P780";
    }

    private String extractLabelFromUri(String uri) {
        int lastSlash = uri.lastIndexOf('/');
        return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
    }

    private int indexOf(String str, String target) {
        return str.indexOf(target);
    }

    private int indexOf(String str, String target, int start) {
        return str.indexOf(target, start);
    }

    /**
     * 三元组
     */
    static class Triple {
        String subject;
        String predicate;
        String object;
        String objectLabel;

        Triple(String subject, String predicate, String object, String objectLabel) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
            this.objectLabel = objectLabel;
        }
    }
}

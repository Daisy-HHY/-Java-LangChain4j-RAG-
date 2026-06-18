package com.kgqa.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Prints a lightweight summary of the bundled RAG evaluation dataset.
 *
 * <p>This runner intentionally does not call model or database services. It is
 * a stable first step for keeping evaluation samples in a machine-readable
 * format before wiring them into the live RAG pipeline.</p>
 */
public class RagEvaluationDatasetPrinter {

    private static final String DATASET_PATH = "evaluation/rag_eval_samples.jsonl";

    public static void main(String[] args) throws Exception {
        List<EvaluationSample> samples = loadSamples();
        Map<String, Long> byCategory = samples.stream()
                .collect(Collectors.groupingBy(EvaluationSample::category, LinkedHashMap::new, Collectors.counting()));

        System.out.println("RAG evaluation dataset: " + samples.size() + " samples");
        byCategory.forEach((category, count) -> System.out.println("- " + category + ": " + count));
        System.out.println();
        samples.forEach(sample -> System.out.printf(
                "%s [%s -> %s] %s%n",
                sample.id(), sample.category(), sample.expectedRoute(), sample.question()
        ));
    }

    private static List<EvaluationSample> loadSamples() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<List<String>> termsType = new TypeReference<>() {
        };

        try (InputStream inputStream = RagEvaluationDatasetPrinter.class
                .getClassLoader()
                .getResourceAsStream(DATASET_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("Evaluation dataset not found: " + DATASET_PATH);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .map(line -> parseSample(objectMapper, termsType, line))
                        .toList();
            }
        }
    }

    private static EvaluationSample parseSample(ObjectMapper objectMapper,
                                                TypeReference<List<String>> termsType,
                                                String line) {
        try {
            Map<String, Object> raw = objectMapper.readValue(line, new TypeReference<>() {
            });
            List<String> expectedTerms = objectMapper.convertValue(raw.get("expectedTerms"), termsType);
            return new EvaluationSample(
                    required(raw, "id"),
                    required(raw, "category"),
                    required(raw, "question"),
                    required(raw, "expectedRoute"),
                    expectedTerms == null ? List.of() : expectedTerms,
                    Objects.toString(raw.get("notes"), "")
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid evaluation sample: " + line, e);
        }
    }

    private static String required(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvaluationSample(
            String id,
            String category,
            String question,
            String expectedRoute,
            List<String> expectedTerms,
            String notes
    ) {
    }
}

package com.kgqa.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * MedQA JSONL 格式解析器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedQaParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析 JSONL 格式的 MedQA 文件
     */
    public List<MedQaRecord> parseFromPath(String filePath) {
        List<MedQaRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        getClass().getResourceAsStream(filePath), StandardCharsets.UTF_8))) {

            String line;
            int lineNum = 0;
            int errorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    MedQaRecord record = objectMapper.readValue(line, MedQaRecord.class);
                    if (record.getQuestion() != null && record.getOptions() != null
                            && !record.getOptions().isEmpty()) {
                        records.add(record);
                    } else {
                        log.warn("跳过不完整记录，行号: {}", lineNum);
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.warn("解析失败，行号: {}，错误: {}", lineNum, e.getMessage());
                }
            }

            log.info("MedQA 解析完成：共 {} 行，成功 {} 条，失败 {} 条",
                    lineNum, records.size(), errorCount);

        } catch (IOException e) {
            log.error("读取 MedQA 文件失败: {}", filePath, e);
        }

        return records;
    }

    /**
     * 解析外部文件路径
     */
    public List<MedQaRecord> parseFromExternalPath(String absolutePath) {
        List<MedQaRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new java.io.FileInputStream(absolutePath), StandardCharsets.UTF_8))) {

            String line;
            int lineNum = 0;
            int errorCount = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    MedQaRecord record = objectMapper.readValue(line, MedQaRecord.class);
                    if (record.getQuestion() != null && record.getOptions() != null
                            && !record.getOptions().isEmpty()) {
                        records.add(record);
                    }
                } catch (Exception e) {
                    errorCount++;
                }
            }

            log.info("MedQA 解析完成（外部文件）：共 {} 行，成功 {} 条，失败 {} 条",
                    lineNum, records.size(), errorCount);

        } catch (IOException e) {
            log.error("读取 MedQA 文件失败: {}", absolutePath, e);
        }

        return records;
    }

    /**
     * 分批返回，避免一次性加载 30K 条到内存
     */
    public void parseInBatches(String filePath, int batchSize, BatchConsumer<List<MedQaRecord>> consumer) {
        List<MedQaRecord> all = parseFromPath(filePath);
        parseListInBatches(all, batchSize, consumer);
    }

    public void parseExternalInBatches(String absolutePath, int batchSize, BatchConsumer<List<MedQaRecord>> consumer) {
        List<MedQaRecord> all = parseFromExternalPath(absolutePath);
        parseListInBatches(all, batchSize, consumer);
    }

    private void parseListInBatches(List<MedQaRecord> all, int batchSize, BatchConsumer<List<MedQaRecord>> consumer) {
        List<MedQaRecord> batch = new ArrayList<>(batchSize);

        for (int i = 0; i < all.size(); i++) {
            batch.add(all.get(i));
            if (batch.size() == batchSize || i == all.size() - 1) {
                consumer.accept(new ArrayList<>(batch), i / batchSize);
                batch.clear();
            }
        }
    }

    @FunctionalInterface
    public interface BatchConsumer<T> {
        void accept(T batch, int batchIndex);
    }
}

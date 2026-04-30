package com.kgqa.controller;

import com.kgqa.service.DataImportService;
import com.kgqa.service.TextBookImportService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@Profile("dev")
public class DataImportController {

    private final DataImportService dataImportService;
    private final TextBookImportService textBookImportService;

    public DataImportController(DataImportService dataImportService,
                               TextBookImportService textBookImportService) {
        this.dataImportService = dataImportService;
        this.textBookImportService = textBookImportService;
    }

    /**
     * TDB 知识图谱只作为 SPARQL 数据源，不导入 RAG 向量库。
     */
    @PostMapping("/tdb")
    public Map<String, Object> importFromTDB(
            @RequestParam String path,
            @RequestParam(defaultValue = "知识图谱") String title) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "TDB 数据集仅用于 kgdrug 知识图谱 SPARQL 查询，不允许导入 RAG 向量库");
        result.put("count", 0);
        return result;
    }

    /**
     * 从 CSV 导入数据
     */
    @PostMapping("/csv")
    public Map<String, Object> importFromCSV(
            @RequestParam String path,
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String tags) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = dataImportService.importFromCSV(path, title, tags);
            result.put("success", true);
            result.put("message", "导入成功");
            result.put("count", count);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 导入中文医学教科书
     */
    @PostMapping("/textbook")
    public Map<String, Object> importTextbook(
            @RequestParam(defaultValue = "E:/Github_project/LangChain4j-KGQA/data/data_clean/textbooks/zh_paragraph") String directory,
            @RequestParam(defaultValue = "医学教科书") String title) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = textBookImportService.importAllChineseTextBooks(directory);
            result.put("success", true);
            result.put("message", "教科书导入成功");
            result.put("count", count);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
        }
        return result;
    }
}

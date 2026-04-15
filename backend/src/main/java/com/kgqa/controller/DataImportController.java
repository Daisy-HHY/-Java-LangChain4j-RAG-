package com.kgqa.controller;

import com.kgqa.service.DataImportService;
import com.kgqa.service.TextBookImportService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
public class DataImportController {

    private final DataImportService dataImportService;
    private final TextBookImportService textBookImportService;

    public DataImportController(DataImportService dataImportService,
                               TextBookImportService textBookImportService) {
        this.dataImportService = dataImportService;
        this.textBookImportService = textBookImportService;
    }

    /**
     * 从 TDB 导入知识图谱
     */
    @PostMapping("/tdb")
    public Map<String, Object> importFromTDB(
            @RequestParam String path,
            @RequestParam(defaultValue = "知识图谱") String title) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = dataImportService.importFromTDB(path, title);
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

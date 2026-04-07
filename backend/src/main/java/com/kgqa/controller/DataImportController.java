package com.kgqa.controller;

import com.kgqa.service.DataImportService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
public class DataImportController {

    private final DataImportService dataImportService;

    public DataImportController(DataImportService dataImportService) {
        this.dataImportService = dataImportService;
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
}

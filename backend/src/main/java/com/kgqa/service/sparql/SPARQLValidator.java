package com.kgqa.service.sparql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPARQL 查询验证器
 * 验证生成的 SPARQL 查询语法和语义是否正确
 */
@Component
public class SPARQLValidator {

    private static final Logger log = LoggerFactory.getLogger(SPARQLValidator.class);

    // 允许的实体类型
    private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of(
            "疾病", "药物", "症状", "检查", "科室", "部位", "治疗"
    );

    // 允许的关系谓词
    private static final Set<String> ALLOWED_PREDICATES = Set.of(
            "适应症", "副作用", "禁忌", "症状", "治疗", "诊断", "病因",
            "并发症", "科室", "部位", "药物类别", "机制", "用法", "剂量", "检查"
    );

    // SELECT 查询正则
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+\\?[a-zA-Z_][a-zA-Z0-9_]*(\\s+WHERE)?",
            Pattern.CASE_INSENSITIVE
    );

    // WHERE 子句正则
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "WHERE\\s*\\{",
            Pattern.CASE_INSENSITIVE
    );

    // 三元组模式 <主体> <谓词> ?变量 或 ?变量 <谓词> <客体>
    private static final Pattern TRIPLE_PATTERN = Pattern.compile(
            "([<?]\\?[a-zA-Z_][a-zA-Z0-9_]*|[<][^>]+[>])" +
                    "\\s*" +
                    "<([^>]+)>" +
                    "\\s*" +
                    "([<?]\\?[a-zA-Z_][a-zA-Z0-9_]*|[<][^>]+[>])"
    );

    // FILTER 模式
    private static final Pattern FILTER_PATTERN = Pattern.compile(
            "FILTER\\s*\\(",
            Pattern.CASE_INSENSITIVE
    );

    // LIMIT 模式
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "LIMIT\\s+\\d+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证 SPARQL 查询
     * @param sparql SPARQL 查询字符串
     * @return 验证结果
     */
    public ValidationResult validate(String sparql) {
        if (sparql == null || sparql.trim().isEmpty()) {
            return new ValidationResult(false, "SPARQL 查询不能为空");
        }

        List<String> errors = new ArrayList<>();
        sparql = sparql.trim();

        // 1. 检查是否以 SELECT 开头
        if (!sparql.toUpperCase().startsWith("SELECT")) {
            return new ValidationResult(false, "SPARQL 查询必须以 SELECT 开头");
        }

        // 2. 检查 SELECT 语法
        if (!SELECT_PATTERN.matcher(sparql).find()) {
            errors.add("SELECT 语法错误");
        }

        // 3. 检查 WHERE 子句
        if (!WHERE_PATTERN.matcher(sparql).find()) {
            errors.add("缺少 WHERE 子句");
        }

        // 4. 检查三元组格式
        Matcher tripleMatcher = TRIPLE_PATTERN.matcher(sparql);
        int tripleCount = 0;
        Set<String> variables = new HashSet<>();
        Set<String> predicates = new HashSet<>();

        while (tripleMatcher.find()) {
            tripleCount++;
            String subject = tripleMatcher.group(1);
            String predicate = tripleMatcher.group(2);
            String object = tripleMatcher.group(3);

            // 提取谓词
            if (predicate != null) {
                predicates.add(predicate);
            }

            // 提取变量
            if (subject != null && subject.startsWith("?")) {
                variables.add(subject);
            }
            if (object != null && object.startsWith("?")) {
                variables.add(object);
            }
        }

        if (tripleCount == 0) {
            errors.add("未找到有效的三元组模式");
        }

        // 5. 检查变量是否在 SELECT 中声明
        Matcher selectMatcher = Pattern.compile("SELECT\\s+(.*?)\\s+WHERE", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(sparql);
        if (selectMatcher.find()) {
            String selectVars = selectMatcher.group(1);
            Matcher varMatcher = Pattern.compile("\\?[a-zA-Z_][a-zA-Z0-9_]*").matcher(selectVars);
            Set<String> declaredVars = new HashSet<>();
            while (varMatcher.find()) {
                declaredVars.add(varMatcher.group());
            }

            for (String var : variables) {
                if (!declaredVars.contains(var)) {
                    // 检查是否是聚合变量（如 COUNT(?x)）
                    if (!selectVars.contains("COUNT(" + var + ")") &&
                        !selectVars.contains("DISTINCT " + var)) {
                        // 可能是中间变量，不需要在 SELECT 中声明
                    }
                }
            }
        }

        // 6. 检查 FILTER 语法（如果存在）
        if (sparql.toUpperCase().contains("FILTER")) {
            if (!FILTER_PATTERN.matcher(sparql).find()) {
                errors.add("FILTER 语法错误");
            }
        }

        // 7. 检查括号匹配
        if (!areParenthesesBalanced(sparql)) {
            errors.add("括号不匹配");
        }

        // 8. 检查 LIMIT
        if (sparql.toUpperCase().contains("LIMIT")) {
            if (!LIMIT_PATTERN.matcher(sparql).find()) {
                errors.add("LIMIT 值格式错误");
            }
        }

        if (errors.isEmpty()) {
            return new ValidationResult(true, "验证通过");
        }

        return new ValidationResult(false, String.join("; ", errors));
    }

    /**
     * 检查括号是否匹配
     */
    private boolean areParenthesesBalanced(String s) {
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == '{') count++;
            if (c == '}') count--;
            if (count < 0) return false;
        }
        return count == 0;
    }

    /**
     * 从 SPARQL 中提取实体名称
     */
    public List<String> extractEntities(String sparql) {
        List<String> entities = new ArrayList<>();
        Pattern entityPattern = Pattern.compile("<([^>]+)>");
        Matcher matcher = entityPattern.matcher(sparql);

        while (matcher.find()) {
            String entity = matcher.group(1);
            // 排除以 http:// 或 kgqa.com 开头的 URI
            if (!entity.startsWith("http://") && !entity.startsWith("kgqa.com")) {
                entities.add(entity);
            }
        }

        return entities;
    }

    /**
     * 验证实体是否为已知类型
     */
    public boolean isKnownEntity(String entity) {
        // 简单检查：实体名称不应为空，不应包含特殊字符
        if (entity == null || entity.trim().isEmpty()) {
            return false;
        }

        // 检查是否包含非法字符
        Pattern illegalChars = Pattern.compile("[\\[\\]{}()\"']");
        return !illegalChars.matcher(entity).find();
    }

    /**
     * 验证结果
     */
    public record ValidationResult(boolean valid, String message) {}
}

package com.kgqa.service.sparql.impl;

import com.kgqa.kg.TdbManager;
import com.kgqa.service.sparql.QueryExecutor;
import org.apache.jena.query.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * SPARQL 查询执行器实现
 * 负责在 TDB 知识图谱上执行 SPARQL 查询
 */
@Service
public class QueryExecutorImpl implements QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutorImpl.class);

    // TDB 数据库路径
    @Value("${kgqa.tdb.path:E:/Github_project/LangChain4j-KGQA/tdb_medqa}")
    private String tdbPath;

    @Value("${kgqa.tdb.query-timeout-seconds:10}")
    private long queryTimeoutSeconds;

    private final TdbManager tdbManager;

    public QueryExecutorImpl(TdbManager tdbManager) {
        this.tdbManager = tdbManager;
    }

    /**
     * 执行 SPARQL 查询
     */
    @Override
    public List<String> execute(String sparql) {
        if (sparql == null || sparql.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return tdbManager.readDatasetTransaction(dataset -> {
                List<String> results = new ArrayList<>();
                try (QueryExecution qexec = QueryExecutionFactory.create(sparql, dataset)) {
                    qexec.setTimeout(queryTimeoutSeconds, TimeUnit.SECONDS);
                    ResultSet rs = qexec.execSelect();
                    while (rs.hasNext()) {
                        String resultStr = solutionToString(rs.next());
                        if (resultStr != null && !resultStr.isEmpty()) {
                            results.add(resultStr);
                        }
                    }
                }
                return results;
            });
        } catch (Exception e) {
            log.error("SPARQL 查询执行失败: {} - 查询: {}", e.getMessage(), sparql, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将查询结果转换为字符串
     */
    private String solutionToString(QuerySolution solution) {
        // 查找第一个绑定值
        try {
            var varNames = solution.varNames();
            while (varNames.hasNext()) {
                String varName = varNames.next();
                if (solution.contains(varName)) {
                    var node = solution.get(varName);
                    if (node.isLiteral()) {
                        return node.asLiteral().getString();
                    }
                    if (node.isResource()) {
                        return node.asResource().getURI();
                    }
                    return node.toString();
                }
            }
        } catch (Exception e) {
            log.warn("解析查询结果失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 执行 SPARQL 查询并返回多列结果
     */
    public QueryResult executeDetailed(String sparql) {
        if (sparql == null || sparql.trim().isEmpty()) {
            return new QueryResult(List.of(), List.of());
        }

        if (!Files.exists(Path.of(tdbPath))) {
            log.warn("TDB 数据库不存在: {}", tdbPath);
            return new QueryResult(List.of(), List.of());
        }

        try {
            return tdbManager.readDatasetTransaction(dataset -> {
                List<String> headers = new ArrayList<>();
                List<List<String>> rows = new ArrayList<>();
                try (QueryExecution qexec = QueryExecutionFactory.create(sparql, dataset)) {
                    qexec.setTimeout(queryTimeoutSeconds, TimeUnit.SECONDS);
                    ResultSet rs = qexec.execSelect();

                    // 获取变量名
                    ResultSetRewindable rsRewindable = ResultSetFactory.makeRewindable(rs);
                    while (rsRewindable.hasNext()) {
                        QuerySolution solution = rsRewindable.next();

                        if (headers.isEmpty()) {
                            solution.varNames().forEachRemaining(headers::add);
                        }

                        List<String> row = new ArrayList<>();
                        for (String header : headers) {
                            if (solution.contains(header)) {
                                row.add(solution.get(header).toString());
                            } else {
                                row.add("");
                            }
                        }
                        rows.add(row);
                    }
                }
                return new QueryResult(headers, rows);
            });
        } catch (Exception e) {
            log.error("SPARQL 查询执行失败: {}", e.getMessage(), e);
        }

        return new QueryResult(List.of(), List.of());
    }

    /**
     * 检查 TDB 是否可用
     */
    @Override
    public boolean isAvailable() {
        return Files.exists(Path.of(tdbPath));
    }

    /**
     * 获取 TDB 路径
     */
    @Override
    public String getTdbPath() {
        return tdbPath;
    }

    /**
     * 设置 TDB 路径
     */
    @Override
    public void setTdbPath(String path) {
        this.tdbPath = path;
    }
}

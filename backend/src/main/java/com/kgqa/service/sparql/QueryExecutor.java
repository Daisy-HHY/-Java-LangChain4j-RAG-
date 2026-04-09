package com.kgqa.service.sparql;

import java.util.List;

/**
 * SPARQL 查询执行器接口
 * 负责在 TDB 知识图谱上执行 SPARQL 查询
 */
public interface QueryExecutor {

    /**
     * 执行 SPARQL 查询
     * @param sparql SPARQL 查询语句
     * @return 查询结果列表
     */
    List<String> execute(String sparql);

    /**
     * 检查 TDB 是否可用
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取 TDB 路径
     * @return TDB 路径
     */
    String getTdbPath();

    /**
     * 设置 TDB 路径
     * @param path TDB 路径
     */
    void setTdbPath(String path);

    /**
     * 查询结果
     */
    record QueryResult(List<String> headers, List<List<String>> rows) {}
}

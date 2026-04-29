package com.kgqa.kg;

import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.tdb.TDBFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * TDB 连接管理器
 */
@Slf4j
@Component
public class TdbManager {

    @Value("${kgqa.tdb.path:./data/tdb2}")
    private String tdbPath;

    private Dataset dataset;

    @PostConstruct
    public void init() {
        Path path = Path.of(tdbPath);
        if (!Files.exists(path)) {
            log.warn("TDB 路径不存在，将创建空数据集: {}", tdbPath);
        }
        dataset = TDBFactory.createDataset(tdbPath);
        log.info("TDB 数据集已连接: {}, 三元组数量: {}", tdbPath, countTriples());
    }

    @PreDestroy
    public void close() {
        if (dataset != null) {
            dataset.close();
            log.info("TDB 数据集已关闭");
        }
    }

    /**
     * 写事务
     */
    public void writeTransaction(Consumer<Model> action) {
        dataset.begin(ReadWrite.WRITE);
        try {
            action.accept(dataset.getDefaultModel());
            dataset.commit();
        } catch (Exception e) {
            dataset.abort();
            log.error("TDB 写事务失败，已回滚", e);
            throw new RuntimeException("TDB 写事务失败", e);
        } finally {
            dataset.end();
        }
    }

    /**
     * 读事务
     */
    public <T> T readTransaction(Function<Model, T> action) {
        dataset.begin(ReadWrite.READ);
        try {
            return action.apply(dataset.getDefaultModel());
        } finally {
            dataset.end();
        }
    }

    /**
     * 读事务，可访问完整 Dataset（默认图和命名图）
     */
    public <T> T readDatasetTransaction(Function<Dataset, T> action) {
        dataset.begin(ReadWrite.READ);
        try {
            return action.apply(dataset);
        } finally {
            dataset.end();
        }
    }

    public Dataset getDataset() {
        return dataset;
    }

    /**
     * 查询图谱中的三元组总数
     */
    public long countTriples() {
        return readDatasetTransaction(ds -> {
            long total = ds.getDefaultModel().size();
            var graphNames = ds.listNames();
            while (graphNames.hasNext()) {
                total += ds.getNamedModel(graphNames.next()).size();
            }
            return total;
        });
    }

    /**
     * 判断图谱是否为空
     */
    public boolean isEmpty() {
        return countTriples() == 0;
    }
}

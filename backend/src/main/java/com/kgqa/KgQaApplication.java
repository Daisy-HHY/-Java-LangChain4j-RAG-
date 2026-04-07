package com.kgqa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.kgqa.repository")
public class KgQaApplication {
    public static void main(String[] args) {
        SpringApplication.run(KgQaApplication.class, args);
    }
}

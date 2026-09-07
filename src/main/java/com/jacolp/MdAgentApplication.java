package com.jacolp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * md-agent 应用的 Spring Boot 启动入口。
 */
@SpringBootApplication
@EnableScheduling
public class MdAgentApplication {

    /**
     * 启动 Spring Boot 应用上下文。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MdAgentApplication.class, args);
    }

}

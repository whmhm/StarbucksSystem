package com.whm.starbucks;


import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 星巴克系统启动类
 */
@SpringBootApplication(scanBasePackages = "com.whm.starbucks")
@MapperScan("com.whm.starbucks.dao")
public class StarbucksApplication {

    private static final Logger log = LoggerFactory.getLogger(StarbucksApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(StarbucksApplication.class, args);
        logStartupInfo(context.getEnvironment());
    }

    /**
     * 启动成功后打印访问地址，便于确认服务是否正常
     */
    private static void logStartupInfo(Environment env) {
        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "/");
        String ip = "127.0.0.1";
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {
            // 获取本机 IP 失败不影响启动
        }
        log.info("\n----------------------------------------------------------\n\t" +
                        "【StarbucksSystem】启动成功！\n\t" +
                        "Local:     \thttp://localhost:{}{}\n\t" +
                        "External:  \thttp://{}:{}{}\n\t" +
                        "Profile:   \t{}\n" +
                        "----------------------------------------------------------",
                port, contextPath, ip, port, contextPath,
                String.join(",", env.getActiveProfiles()));
    }
}

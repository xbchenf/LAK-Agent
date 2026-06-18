package com.lak.ai.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Mybatis-Plus 配置 — 分页插件 + 审计日志动态表名。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 审计日志表名前缀。
     */
    private static final String AUDIT_LOG_PREFIX = "audit_log";

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 审计日志按月分表 — 运行时替换 audit_log → audit_log_yyyyMM
        DynamicTableNameInnerInterceptor dynamicTableName = new DynamicTableNameInnerInterceptor();
        dynamicTableName.setTableNameHandler((sql, tableName) -> {
            if (AUDIT_LOG_PREFIX.equals(tableName)) {
                return AUDIT_LOG_PREFIX + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            }
            return tableName;
        });
        interceptor.addInnerInterceptor(dynamicTableName);

        // 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}

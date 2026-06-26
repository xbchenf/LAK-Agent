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
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 审计日志查询时，可通过此 ThreadLocal 覆盖目标月份（如 "202606"）。
     * 设置后在 Service 层 finally 块中必须清理。
     */
    private static final ThreadLocal<String> AUDIT_MONTH_SUFFIX = new ThreadLocal<>();

    /** 设置审计日志查询目标月份（yyyyMM），查询前调用。 */
    public static void setAuditMonth(String monthSuffix) {
        AUDIT_MONTH_SUFFIX.set(monthSuffix);
    }

    /** 清理审计日志月份覆盖，查询后调用。 */
    public static void clearAuditMonth() {
        AUDIT_MONTH_SUFFIX.remove();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 审计日志按月分表 — 运行时替换 audit_log → audit_log_yyyyMM
        DynamicTableNameInnerInterceptor dynamicTableName = new DynamicTableNameInnerInterceptor();
        dynamicTableName.setTableNameHandler((sql, tableName) -> {
            if (AUDIT_LOG_PREFIX.equals(tableName)) {
                String suffix = AUDIT_MONTH_SUFFIX.get();
                if (suffix != null) {
                    return AUDIT_LOG_PREFIX + "_" + suffix;
                }
                return AUDIT_LOG_PREFIX + "_" + LocalDate.now().format(MONTH_FMT);
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

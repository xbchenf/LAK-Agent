package com.lak.ai.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Mybatis-Plus 字段自动填充 — createTime / updateTime。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 禁止手动 new Date()，由 MetaObjectHandler 统一填充。
 */
@Slf4j
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        // 部分实体有 updateTime，插入时也填充
        if (metaObject.hasSetter("updateTime")) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}

---
name: langchain4j-version
description: LangChain4j 使用 1.14.0-beta24 版本，dashscope 坐标为 community 版
metadata:
  type: reference
---

# LangChain4j 版本注意事项

项目使用 **1.14.0-beta24**（非设计文档中的 1.14.0 GA），原因: 阿里云 Maven 镜像未同步 1.14.0 GA 的 spring-boot-starter/dashscope/qdrant 模块 jar。

关键坐标:
- `dev.langchain4j:langchain4j-spring-boot-starter:1.14.0-beta24`
- `dev.langchain4j:langchain4j-community-dashscope-spring-boot-starter:1.14.0-beta24`
- `dev.langchain4j:langchain4j-qdrant:1.14.0-beta24`

**Why:** 本地仓库 D:\maven-repository 已有 beta24 完整 jar，GA 版仅部分模块已下载。beta24 POM 指向 langchain4j core 1.14.0，功能等价。
**How to apply:** 如果需要切换到 GA 版，需要先确保 Maven Central 可直连（非阿里云镜像），然后改 `<langchain4j.version>` 和 dashscope artifactId。

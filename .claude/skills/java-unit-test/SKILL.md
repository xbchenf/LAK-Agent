---
name: java-unit-test
description: LAK-Agent 单元测试规范 — JUnit5 + Mockito + SpringBootTest
---

# 单元测试规范（LAK-Agent）

## 测试分层

| 层次 | 框架 | 关注点 |
|------|------|--------|
| Service 层 | JUnit5 + Mockito | 业务逻辑正确性 |
| Controller 层 | @WebMvcTest + MockMvc | 接口契约 + 参数校验 |
| Repository 层 | @DataJpaTest / @MybatisPlusTest | SQL 正确性 |
| 集成测试 | @SpringBootTest + Testcontainers | 端到端流程 |

## Service 层测试模板

```java
@ExtendWith(MockitoExtension.class)
class PolicyAgentTest {

    @Mock
    private RagEngine ragEngine;

    @Mock
    private ChatLanguageModel chatModel;

    @InjectMocks
    private PolicyAgent policyAgent;

    @Test
    void shouldReturnAnswerWithSources_whenConfidenceIsHigh() {
        // given
        String userMessage = "《行政复议法》第9条申请期限";
        List<RagFragment> fragments = List.of(
            new RagFragment("第十五条...", "A-001", "XX省执法规定", 0.89)
        );
        when(ragEngine.hybridSearch(userMessage)).thenReturn(fragments);
        when(chatModel.generate(anyString())).thenReturn("根据《行政复议法》第9条...");

        // when
        AgentResponse response = policyAgent.process(new AgentRequest(userMessage));

        // then
        assertThat(response.getAnswer()).contains("行政复议法");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getConfidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void shouldFallback_whenRetrievalConfidenceIsLow() {
        // given
        when(ragEngine.hybridSearch(anyString())).thenReturn(Collections.emptyList());

        // when
        AgentResponse response = policyAgent.process(new AgentRequest("模糊问题"));

        // then
        assertThat(response.getAnswer()).contains("人工客服");
    }
}
```

## 强制约束

- 新功能必须带测试，测试类命名: `XxxTest`
- 方法命名: `should预期行为_when触发条件()`
- 使用 AssertJ 断言（`assertThat`），不用 JUnit 原生 `assertEquals`
- 不在测试中调用真实外部服务（大模型 API、数据库）— 使用 Mock

package com.kgqa.service.qa.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kgqa.model.dto.ChatRequest;
import com.kgqa.model.dto.ChatResponse;
import com.kgqa.model.dto.SourceItem;
import com.kgqa.model.entity.ChatMessageEntity;
import com.kgqa.model.entity.ChatSession;
import com.kgqa.repository.ChatMessageMapper;
import com.kgqa.repository.ChatSessionRepository;
import com.kgqa.service.qa.HybridQAService;
import com.kgqa.service.qa.IntentDetectionService;
import com.kgqa.service.qa.MedicalEntityExtractor;
import com.kgqa.service.rag.ChatMemoryService;
import com.kgqa.service.rag.RAGPipeline;
import com.kgqa.service.sparql.QueryExecutor;
import com.kgqa.service.sparql.SPARQLGenerator;
import com.kgqa.service.sparql.SPARQLTemplateMatcher;
import com.kgqa.util.AnswerFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 混合问答服务实现
 * 结合 SPARQL 知识图谱查询和 RAG 向量检索
 */
@Service
public class HybridQAServiceImpl implements HybridQAService {

    private static final Logger log = LoggerFactory.getLogger(HybridQAServiceImpl.class);

    // 各组件依赖
    private final RAGPipeline ragPipeline;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageMapper messageMapper;
    private final ChatMemoryService chatMemoryService;
    private final ObjectMapper objectMapper;

    // 混合系统组件
    private final IntentDetectionService intentDetectionService;
    private final MedicalEntityExtractor entityExtractor;
    private final SPARQLTemplateMatcher templateMatcher;
    private final SPARQLGenerator sparqlGenerator;
    private final QueryExecutor queryExecutor;

    public HybridQAServiceImpl(
            // RAG 组件
            RAGPipeline ragPipeline,
            ChatSessionRepository sessionRepository,
            ChatMessageMapper messageMapper,
            ChatMemoryService chatMemoryService,
            // 混合系统组件
            IntentDetectionService intentDetectionService,
            MedicalEntityExtractor entityExtractor,
            SPARQLTemplateMatcher templateMatcher,
            SPARQLGenerator sparqlGenerator,
            QueryExecutor queryExecutor) {
        this.ragPipeline = ragPipeline;
        this.sessionRepository = sessionRepository;
        this.messageMapper = messageMapper;
        this.chatMemoryService = chatMemoryService;
        this.objectMapper = new ObjectMapper();
        // 混合系统
        this.intentDetectionService = intentDetectionService;
        this.entityExtractor = entityExtractor;
        this.templateMatcher = templateMatcher;
        this.sparqlGenerator = sparqlGenerator;
        this.queryExecutor = queryExecutor;
    }

    /**
     * 处理用户问答
     */
    @Override
    public ChatResponse chat(ChatRequest request, Long userId) {
        String question = request.getQuestion();
        String sessionIdStr = request.getSessionId();

        log.info("收到问题: {}", question);

        // 获取或创建会话
        Long sessionId = getOrCreateSession(sessionIdStr, userId);

        // 获取对话历史（带角色信息）
        List<ChatMessageEntity> history = chatMemoryService.getChatHistory(sessionId);

        // 执行混合问答
        Result result = answer(question, history);

        // 保存用户消息
        chatMemoryService.saveMessage(sessionId, "USER", question, null);

        // 保存助手回复
        try {
            String sourcesJson = objectMapper.writeValueAsString(result.sources());
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), sourcesJson);
        } catch (Exception e) {
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), null);
        }

        // 更新会话标题为问题内容（如果标题是默认的"新会话"）
        updateSessionTitle(sessionId, question);
        touchSession(sessionId);

        String actualSessionId = sessionRepository.selectById(sessionId).getSessionId();
        return new ChatResponse(result.answer(), result.sources(), actualSessionId);
    }

    /**
     * 执行混合问答
     */
    @Override
    public Result answer(String question, List<ChatMessageEntity> chatHistory) {
        // 1. 意图识别
        IntentDetectionService.QuestionType questionType = intentDetectionService.detect(question);
        log.debug("意图识别结果: {}", questionType);

        // 2. 实体抽取
        MedicalEntityExtractor.ExtractionResult entityResult = entityExtractor.extract(question);
        log.debug("实体抽取结果: {} - {}", entityResult.entities(), entityResult.intent());

        // 3. 路由决策：识别到 kgdrug 实体时优先尝试知识图谱。
        if (!entityResult.entities().isEmpty()) {
            Result sparqlResult = trySPARQLQuery(question, entityResult);
            if (sparqlResult != null) {
                return sparqlResult;
            }
        }

        // 4. Fallback 到 RAG
        return answerByRAG(question, chatHistory);
    }

    /**
     * 尝试 SPARQL 查询
     */
    private Result trySPARQLQuery(String question,
                                  MedicalEntityExtractor.ExtractionResult entityResult) {
        // 4a1. 尝试模板匹配
        SPARQLTemplateMatcher.MatchResult matchResult = templateMatcher.match(question, entityResult);

        if (matchResult != null) {
            log.info("模板匹配成功: {}", matchResult.sparql());
            List<String> kgResults = queryExecutor.execute(matchResult.sparql());

            if (!kgResults.isEmpty()) {
                // 使用 LLM 生成自然语言回答
                String answer = generateAnswerFromKG(question, kgResults, matchResult.predicate());
                return new Result(answer, toSourceItems(kgResults));
            }
        }

        // 4a2. 尝试 LLM 生成 SPARQL
        String generatedSparql = sparqlGenerator.generate(question, entityResult);

        if (generatedSparql != null && !generatedSparql.isEmpty()) {
            log.info("LLM 生成 SPARQL: {}", generatedSparql);
            List<String> kgResults = queryExecutor.execute(generatedSparql);

            if (!kgResults.isEmpty()) {
                String answer = generateAnswerFromKG(question, kgResults, null);
                return new Result(answer, toSourceItems(kgResults));
            }
        }

        // SPARQL 查询失败，返回 null 触发 RAG fallback
        log.debug("SPARQL 查询无结果，fallback 到 RAG");
        return null;
    }

    /**
     * 使用知识图谱结果生成回答
     */
    private String generateAnswerFromKG(String question, List<String> kgResults, String predicate) {
        if (kgResults.isEmpty()) {
            return "抱歉，我在知识库中没有找到相关信息。";
        }

        // 构建提示词
        String resultsText = formatKgResultsForPrompt(kgResults);

        String prompt = String.format("""
                你是一个专业的医学知识问答助手。请根据知识图谱查询结果回答用户问题。

                你必须**严格基于**以下查询结果回答，不要添加查询结果中没有的信息。
                如果结果不足，请明确说"资料中未提及"，不要编造。

                输出格式要求：
                1. 第一行直接给出简短结论。
                2. 如果查询结果有多条，必须使用编号列表，每条结果单独一行。
                3. 如果查询结果是长文本，按语义拆成 2-4 个短段落。
                4. 不要把所有内容堆在一个段落里。
                5. 不要输出 Markdown 表格。
        
                用户问题：%s

                查询结果：
                %s

                请用简洁、专业、分段清晰的语言回答。
                """, question, resultsText);

        try {
            // 使用 ragPipeline 中的 chatModel
            String answer = ragPipeline.getChatModel().chat(prompt);
            return AnswerFormatter.format(answer);
        } catch (Exception e) {
            // 如果 LLM 调用失败，直接返回结果列表
            if (predicate != null) {
                return AnswerFormatter.format(String.format("根据查询，%s：\n%s",
                        predicate, resultsText));
            }
            return AnswerFormatter.format("查询结果：\n" + resultsText);
        }
    }

    private String formatKgResultsForPrompt(List<String> kgResults) {
        if (kgResults.size() == 1) {
            return kgResults.get(0);
        }

        return IntStream.range(0, kgResults.size())
                .mapToObj(i -> (i + 1) + ". " + kgResults.get(i))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * 使用 RAG 回答
     */
    private Result answerByRAG(String question, List<ChatMessageEntity> chatHistory) {
        log.debug("使用 RAG 向量检索回答");
        RAGPipeline.Result result = ragPipeline.answer(question, chatHistory);
        return new Result(result.answer(), result.sources());
    }

    /**
     * 获取或创建会话
     */
    private Long getOrCreateSession(String sessionIdStr, Long userId) {
        if (sessionIdStr == null || sessionIdStr.isEmpty()) {
            // 创建新会话
            ChatSession session = new ChatSession();
            session.setUserId(userId);
            session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
            session.setTitle("新会话");
            LocalDateTime now = LocalDateTime.now();
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.insert(session);
            return session.getId();
        }

        // 查找现有会话
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionIdStr)
                        .eq(ChatSession::getUserId, userId)
        );

        if (session == null) {
            session = new ChatSession();
            session.setUserId(userId);
            session.setSessionId(sessionIdStr);
            session.setTitle("会话 " + sessionIdStr.substring(0, Math.min(8, sessionIdStr.length())));
            LocalDateTime now = LocalDateTime.now();
            session.setCreatedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.insert(session);
        }

        return session.getId();
    }

    /**
     * 更新会话标题为用户第一个问题
     */
    private void updateSessionTitle(Long sessionId, String question) {
        ChatSession session = sessionRepository.selectById(sessionId);
        if (session != null && "新会话".equals(session.getTitle())) {
            // 截取问题前20个字符作为标题
            String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
            session.setTitle(title);
            sessionRepository.updateById(session);
        }
    }

    private void touchSession(Long sessionId) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepository.updateById(session);
    }

    /**
     * 获取所有会话
     */
    @Override
    public List<ChatSession> getSessions(Long userId) {
        return sessionRepository.selectSessionsOrderByLatestActivity(userId);
    }

    /**
     * 删除会话
     */
    @Override
    public boolean deleteSession(String sessionId, Long userId) {
        ChatSession session = sessionRepository.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId)
                        .eq(ChatSession::getUserId, userId)
        );
        if (session != null) {
            messageMapper.delete(
                    new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getSessionId, session.getId())
            );
            sessionRepository.deleteById(session.getId());
            return true;
        }
        return false;
    }

    /**
     * 将字符串列表转换为来源项列表
     * SPARQL 查询结果没有分数信息,默认设为 1.0
     */
    private List<SourceItem> toSourceItems(List<String> strings) {
        return strings.stream()
                .map(s -> new SourceItem(s, 1.0))
                .toList();
    }
}

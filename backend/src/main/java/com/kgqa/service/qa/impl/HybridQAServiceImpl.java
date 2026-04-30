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
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
    private final StreamingChatModel streamingChatModel;

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
            StreamingChatModel streamingChatModel,
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
        this.streamingChatModel = streamingChatModel;
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

    @Override
    public void streamChat(ChatRequest request, Long userId, StreamHandler handler) {
        String question = request.getQuestion();
        String sessionIdStr = request.getSessionId();

        log.info("收到流式问题: {}", question);

        Long sessionId = getOrCreateSession(sessionIdStr, userId);
        String actualSessionId = sessionRepository.selectById(sessionId).getSessionId();
        handler.onSession(actualSessionId);

        List<ChatMessageEntity> history = chatMemoryService.getChatHistory(sessionId);
        Result result = answerStreaming(question, history, handler::onToken);

        chatMemoryService.saveMessage(sessionId, "USER", question, null);

        try {
            String sourcesJson = objectMapper.writeValueAsString(result.sources());
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), sourcesJson);
        } catch (Exception e) {
            chatMemoryService.saveMessage(sessionId, "ASSISTANT", result.answer(), null);
        }

        updateSessionTitle(sessionId, question);
        touchSession(sessionId);

        handler.onSources(result.sources());
        handler.onComplete(result.answer(), result.sources(), actualSessionId);
    }

    /**
     * 执行混合问答
     */
    @Override
    public Result answer(String question, List<ChatMessageEntity> chatHistory) {
        String resolvedQuestion = resolveQuestionWithMemory(question, chatHistory);
        if (!resolvedQuestion.equals(question)) {
            log.info("基于多轮记忆改写问题: [{}] -> [{}]", question, resolvedQuestion);
        }

        // 1. 意图识别
        IntentDetectionService.QuestionType questionType = intentDetectionService.detect(resolvedQuestion);
        log.debug("意图识别结果: {}", questionType);

        // 2. 实体抽取
        MedicalEntityExtractor.ExtractionResult entityResult = entityExtractor.extract(resolvedQuestion);
        log.debug("实体抽取结果: {} - {}", entityResult.entities(), entityResult.intent());

        // 3. 路由决策：识别到 kgdrug 实体时优先尝试知识图谱。
        if (!entityResult.entities().isEmpty()) {
            Result sparqlResult = trySPARQLQuery(resolvedQuestion, entityResult);
            if (sparqlResult != null) {
                return sparqlResult;
            }
        }

        // 4. Fallback 到 RAG
        return answerByRAG(resolvedQuestion, chatHistory);
    }

    private Result answerStreaming(String question, List<ChatMessageEntity> chatHistory, Consumer<String> tokenConsumer) {
        String resolvedQuestion = resolveQuestionWithMemory(question, chatHistory);
        if (!resolvedQuestion.equals(question)) {
            log.info("基于多轮记忆改写流式问题: [{}] -> [{}]", question, resolvedQuestion);
        }

        IntentDetectionService.QuestionType questionType = intentDetectionService.detect(resolvedQuestion);
        log.debug("流式意图识别结果: {}", questionType);

        MedicalEntityExtractor.ExtractionResult entityResult = entityExtractor.extract(resolvedQuestion);
        log.debug("流式实体抽取结果: {} - {}", entityResult.entities(), entityResult.intent());

        if (!entityResult.entities().isEmpty()) {
            Result sparqlResult = trySPARQLQueryStreaming(resolvedQuestion, entityResult, tokenConsumer);
            if (sparqlResult != null) {
                return sparqlResult;
            }
        }

        log.debug("使用 RAG 向量检索流式回答");
        RAGPipeline.Result result = ragPipeline.answerStreaming(resolvedQuestion, chatHistory, tokenConsumer);
        return new Result(result.answer(), result.sources());
    }

    private String resolveQuestionWithMemory(String question, List<ChatMessageEntity> chatHistory) {
        if (question == null || question.isBlank() || chatHistory == null || chatHistory.isEmpty()) {
            return question;
        }

        MedicalEntityExtractor.ExtractionResult currentEntityResult = entityExtractor.extract(question);
        if (!currentEntityResult.entities().isEmpty()) {
            return question;
        }

        if (!isContextualFollowUp(question)) {
            return question;
        }

        MedicalEntityExtractor.MedicalEntity recentEntity = findRecentUserEntity(chatHistory);
        if (recentEntity == null) {
            return question;
        }

        return rewriteFollowUpQuestion(question, recentEntity);
    }

    private boolean isContextualFollowUp(String question) {
        String q = question.trim();
        return q.contains("什么药")
                || q.contains("哪些药")
                || q.contains("怎么治")
                || q.contains("怎么治疗")
                || q.contains("如何治疗")
                || q.contains("治疗")
                || q.contains("用药")
                || q.contains("吃什么")
                || q.contains("有什么症状")
                || q.contains("有哪些症状")
                || q.contains("病因")
                || q.contains("原因")
                || q.contains("它")
                || q.contains("这个")
                || q.contains("该病")
                || q.contains("这种病")
                || q.contains("上述");
    }

    private MedicalEntityExtractor.MedicalEntity findRecentUserEntity(List<ChatMessageEntity> chatHistory) {
        int start = Math.max(0, chatHistory.size() - 8);
        for (int i = chatHistory.size() - 1; i >= start; i--) {
            ChatMessageEntity message = chatHistory.get(i);
            if (!"USER".equals(message.getRole()) || message.getContent() == null) {
                continue;
            }

            MedicalEntityExtractor.ExtractionResult result = entityExtractor.extract(message.getContent());
            if (!result.entities().isEmpty()) {
                return preferDiseaseEntity(result.entities());
            }
        }

        return null;
    }

    private String rewriteFollowUpQuestion(String question, MedicalEntityExtractor.MedicalEntity entity) {
        String entityName = entity.value();
        String q = question.trim();

        if (q.contains("什么药") || q.contains("哪些药") || q.contains("用药") || q.contains("吃什么")) {
            return entityName + "用什么药";
        }
        if (q.contains("怎么治") || q.contains("怎么治疗") || q.contains("如何治疗") || q.contains("治疗")) {
            return entityName + "的治疗方法";
        }
        if (q.contains("有什么症状") || q.contains("有哪些症状") || q.contains("症状") || q.contains("表现")) {
            return entityName + "有什么症状";
        }
        if (q.contains("病因") || q.contains("原因")) {
            return entityName + "的病因";
        }
        if (q.contains("预防")) {
            return entityName + "的预防方法";
        }

        return entityName + "：" + q;
    }

    private MedicalEntityExtractor.MedicalEntity preferDiseaseEntity(List<MedicalEntityExtractor.MedicalEntity> entities) {
        return entities.stream()
                .filter(entity -> "疾病".equals(entity.type()))
                .findFirst()
                .orElse(entities.get(0));
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

    private Result trySPARQLQueryStreaming(String question,
                                           MedicalEntityExtractor.ExtractionResult entityResult,
                                           Consumer<String> tokenConsumer) {
        SPARQLTemplateMatcher.MatchResult matchResult = templateMatcher.match(question, entityResult);

        if (matchResult != null) {
            log.info("流式模板匹配成功: {}", matchResult.sparql());
            List<String> kgResults = queryExecutor.execute(matchResult.sparql());

            if (!kgResults.isEmpty()) {
                String answer = generateAnswerFromKGStreaming(question, kgResults, matchResult.predicate(), tokenConsumer);
                return new Result(answer, toSourceItems(kgResults));
            }
        }

        String generatedSparql = sparqlGenerator.generate(question, entityResult);

        if (generatedSparql != null && !generatedSparql.isEmpty()) {
            log.info("流式 LLM 生成 SPARQL: {}", generatedSparql);
            List<String> kgResults = queryExecutor.execute(generatedSparql);

            if (!kgResults.isEmpty()) {
                String answer = generateAnswerFromKGStreaming(question, kgResults, null, tokenConsumer);
                return new Result(answer, toSourceItems(kgResults));
            }
        }

        log.debug("流式 SPARQL 查询无结果，fallback 到 RAG");
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

    private String generateAnswerFromKGStreaming(String question,
                                                 List<String> kgResults,
                                                 String predicate,
                                                 Consumer<String> tokenConsumer) {
        if (kgResults.isEmpty()) {
            String answer = "抱歉，我在知识库中没有找到相关信息。";
            tokenConsumer.accept(answer);
            return answer;
        }

        String prompt = buildKgAnswerPrompt(question, kgResults);
        StringBuilder answerBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                answerBuilder.append(partialResponse);
                tokenConsumer.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            latch.await();
            if (errorRef.get() == null) {
                return AnswerFormatter.format(answerBuilder.toString());
            }
            throw new IllegalStateException(errorRef.get());
        } catch (Exception e) {
            if (answerBuilder.isEmpty()) {
                String fallback = predicate != null
                        ? String.format("根据查询，%s：\n%s", predicate, formatKgResultsForPrompt(kgResults))
                        : "查询结果：\n" + formatKgResultsForPrompt(kgResults);
                String formattedFallback = AnswerFormatter.format(fallback);
                tokenConsumer.accept(formattedFallback);
                return formattedFallback;
            }
            return AnswerFormatter.format(answerBuilder.toString());
        }
    }

    private String buildKgAnswerPrompt(String question, List<String> kgResults) {
        String resultsText = formatKgResultsForPrompt(kgResults);

        return String.format("""
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

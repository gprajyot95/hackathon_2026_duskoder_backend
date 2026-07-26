package com.example.backend.service;

import com.example.backend.model.GeminiResponse;
import com.example.backend.model.QueryResultResponse;
import com.example.backend.model.UserQuestionRequest;
import com.example.backend.validation.SqlValidationService;
import com.example.backend.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AiQueryService {

    private static final Logger logger = LoggerFactory.getLogger(AiQueryService.class);

    private final LlmService llmService;
    private final PromptBuilderService promptBuilderService;
    private final SchemaMetadataService schemaMetadataService;
    private final SqlValidationService sqlValidationService;
    private final SqlExecutionService sqlExecutionService;

    public AiQueryService(LlmService llmService,
                           PromptBuilderService promptBuilderService,
                           SchemaMetadataService schemaMetadataService,
                           SqlValidationService sqlValidationService,
                           SqlExecutionService sqlExecutionService) {
        this.llmService = llmService;
        this.promptBuilderService = promptBuilderService;
        this.schemaMetadataService = schemaMetadataService;
        this.sqlValidationService = sqlValidationService;
        this.sqlExecutionService = sqlExecutionService;
    }

    /**
     * Main AI Query Orchestrator supporting Two-Stage Gemini Pipeline.
     */
    public QueryResultResponse processUserQuestion(UserQuestionRequest request) {
        long totalStartTime = System.currentTimeMillis();

        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            logger.warn("Received empty user question request");
            return QueryResultResponse.errorResponse("", "Question must not be empty.");
        }

        String question = request.getQuestion().trim();
        logger.info("==========================================================================");
        logger.info("START AI QUERY REQUEST: '{}'", question);
        logger.info("==========================================================================");

        // Step 1: Read schema metadata from Caffeine cache
        logger.info("Reading database schema metadata from Caffeine cache...");
        String schemaMetadata = schemaMetadataService.getCachedSchemaMetadata();
        if (schemaMetadata == null || schemaMetadata.isBlank()) {
            logger.error("Caffeine schema metadata cache is empty or unavailable!");
            return QueryResultResponse.errorResponse(question, "Database schema metadata is currently unavailable in cache.");
        }

        // Step 2: Read instruction.md
        String systemInstruction = promptBuilderService.getSystemInstruction();

        // Step 3: Stage 1 Gemini Call (Query Generation Mode)
        logger.info("--> STAGE 1: Invoking Gemini (Query Generation Mode)...");
        long stage1StartTime = System.currentTimeMillis();
        GeminiResponse stage1Response;
        try {
            stage1Response = llmService.generateQuery(systemInstruction, schemaMetadata, question);
        } catch (Exception e) {
            logger.error("Failed Stage 1 LLM service call: {}", e.getMessage(), e);
            return QueryResultResponse.errorResponse(question, "AI Stage 1 error: " + e.getMessage());
        }
        long stage1Duration = System.currentTimeMillis() - stage1StartTime;
        logger.info("<-- STAGE 1 COMPLETED in {}ms", stage1Duration);

        if (stage1Response == null) {
            logger.error("Stage 1 returned null GeminiResponse object!");
            return QueryResultResponse.errorResponse(question, "AI service returned null response in Stage 1.");
        }

        String effectiveSql = stage1Response.getEffectiveSql();
        boolean requiresDatabase = Boolean.TRUE.equals(stage1Response.getRequiresDatabase());
        boolean isQueryType = "query".equalsIgnoreCase(stage1Response.getType()) || requiresDatabase || (effectiveSql != null && !effectiveSql.isBlank());

        logger.info("[DIAGNOSTIC LOG] Stage 1 Parsing Completed:");
        logger.info("  - type:              '{}'", stage1Response.getType());
        logger.info("  - requiresDatabase:  {}", requiresDatabase);
        logger.info("  - isQueryType:       {}", isQueryType);
        logger.info("  - effectiveSql:      '{}'", effectiveSql);

        // Step 4: Branch Processing & Decision Logic
        if (!isQueryType) {
            // Category 1: Schema / Text Question (requiresDatabase = false)
            long totalDuration = System.currentTimeMillis() - totalStartTime;
            logger.info("[DECISION] Schema / Text Question detected (requiresDatabase=false). Skipping Stage 2 and returning Stage 1 response directly.");
            logger.info("PERFORMANCE METRICS: Stage1={}ms, Total={}ms", stage1Duration, totalDuration);
            logger.info("==========================================================================");
            return QueryResultResponse.textResponse(question, stage1Response);
        }

        // Database execution branch required
        if (effectiveSql == null || effectiveSql.isBlank()) {
            logger.error("[DECISION ERROR] Query execution was requested (requiresDatabase=true or type=query), but no valid SQL was generated by Stage 1!");
            return QueryResultResponse.errorResponse(question, "AI model determined a database query was required, but failed to produce a valid SQL statement.");
        }

        // Category 2: Data Question (Requires SQL Validation, Execution & Stage 2 Response Generation)
        logger.info("[DECISION] Data Question detected (requiresDatabase=true). Proceeding to SQL Execution pipeline with SQL: '{}'", effectiveSql);

        // Step 5: SQL Validation
        logger.info("--> SQL VALIDATION: Validating generated SQL...");
        long valStartTime = System.currentTimeMillis();
        ValidationResult validationResult = sqlValidationService.validate(effectiveSql);
        long valDuration = System.currentTimeMillis() - valStartTime;

        if (!validationResult.isValid()) {
            logger.warn("<-- SQL VALIDATION FAILED in {}ms: {}", valDuration, validationResult.getErrorMessage());
            return QueryResultResponse.errorResponse(question, validationResult.getErrorMessage());
        }
        logger.info("<-- SQL VALIDATION PASSED in {}ms", valDuration);

        // Step 6: SQL Execution
        logger.info("--> SQL EXECUTION: Executing query on PostgreSQL database...");
        long sqlStartTime = System.currentTimeMillis();
        List<Map<String, Object>> queryResults;
        try {
            queryResults = sqlExecutionService.executeSelect(effectiveSql);
        } catch (Exception e) {
            logger.error("<-- SQL EXECUTION FAILED for query '{}': {}", effectiveSql, e.getMessage(), e);
            return QueryResultResponse.errorResponse(question, "Failed to execute database query: " + e.getMessage());
        }
        long sqlDuration = System.currentTimeMillis() - sqlStartTime;
        int rowCount = (queryResults != null) ? queryResults.size() : 0;
        logger.info("<-- SQL EXECUTION COMPLETED in {}ms (Rows returned: {})", sqlDuration, rowCount);

        // Step 7: Stage 2 Gemini Call (Response Generation Mode)
        logger.info("--> STAGE 2: Invoking Gemini (Response Generation Mode with {} rows context)...", rowCount);
        long stage2StartTime = System.currentTimeMillis();
        GeminiResponse stage2Response;
        try {
            stage2Response = llmService.generateFormattedResponse(systemInstruction, question, effectiveSql, queryResults);
        } catch (Exception e) {
            logger.error("Failed Stage 2 LLM service call: {}", e.getMessage(), e);
            return QueryResultResponse.errorResponse(question, "AI Stage 2 response formatting error: " + e.getMessage());
        }
        long stage2Duration = System.currentTimeMillis() - stage2StartTime;
        logger.info("<-- STAGE 2 COMPLETED in {}ms", stage2Duration);

        long totalDuration = System.currentTimeMillis() - totalStartTime;

        // Structured Performance Log
        logger.info("==========================================================================");
        logger.info("PERFORMANCE SUMMARY FOR QUERY: '{}'", question);
        logger.info("  1. Stage 1 (Query Gen): {} ms", stage1Duration);
        logger.info("  2. SQL Validation:      {} ms", valDuration);
        logger.info("  3. SQL Execution:       {} ms (Rows: {})", sqlDuration, rowCount);
        logger.info("  4. Stage 2 (Resp Gen):  {} ms", stage2Duration);
        logger.info("  TOTAL REQUEST TIME:    {} ms", totalDuration);
        logger.info("==========================================================================");

        logger.info("--> RETURNING STAGE 2 FORMATTED RESPONSE TO CONTROLLER");
        return QueryResultResponse.formattedQueryResponse(question, effectiveSql, queryResults, stage2Response, totalDuration);
    }
}

package com.example.backend.controller;

import com.example.backend.model.QueryResultResponse;
import com.example.backend.model.UserQuestionRequest;
import com.example.backend.service.AiQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiQueryController {

    private static final Logger logger = LoggerFactory.getLogger(AiQueryController.class);

    private final AiQueryService aiQueryService;

    public AiQueryController(AiQueryService aiQueryService) {
        this.aiQueryService = aiQueryService;
    }

    /**
     * AI-Powered Natural Language Database Assistant Query Endpoint.
     *
     * @param request Payload containing {"question": "..."}
     * @return QueryResultResponse containing text answer or SQL query execution results
     */
    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QueryResultResponse> processQuery(@RequestBody UserQuestionRequest request) {
        logger.info("Received POST /api/ai/query request");
        QueryResultResponse response = aiQueryService.processUserQuestion(request);

        if (response.getError() != null) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}

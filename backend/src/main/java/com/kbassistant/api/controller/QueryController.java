package com.kbassistant.api.controller;

import com.kbassistant.api.dto.QueryRequest;
import com.kbassistant.api.dto.QueryResponse;
import com.kbassistant.application.service.QueryService;
import com.kbassistant.domain.model.DocumentId;
import com.kbassistant.domain.model.QueryResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/queries")
@Tag(name = "Query", description = "RAG query API — ask questions against ingested documents")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Ask a question", description =
            "Embeds the question, searches for relevant chunks, and generates a grounded answer.")
    public QueryResponse query(@RequestBody @Valid QueryRequest request) {
        List<DocumentId> filter = request.documentIds() == null ? List.of()
                : request.documentIds().stream()
                        .map(id -> DocumentId.of(UUID.fromString(id)))
                        .toList();

        QueryResult result = queryService.query(request.question(), filter);
        return QueryResponse.from(result);
    }
}

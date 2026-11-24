package com.example.querytranslator.controller;

import com.example.querytranslator.dto.TranslationRequest;
import com.example.querytranslator.dto.TranslationResponse;
import com.example.querytranslator.exception.InvalidRequestException;
import com.example.querytranslator.exception.UnsupportedQueryException;
import com.example.querytranslator.service.QueryTranslationService;
import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/query")
@Tag(name = "Query translation")
public class QueryTranslationController {
    private static final Set<String> REQUEST_PROPERTIES = Set.of("table", "query");
    private final QueryTranslationService service;

    public QueryTranslationController(QueryTranslationService service) { this.service = service; }

    @PostMapping(value = "/translate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Translate OpenSearch Query DSL into parameterized ClickHouse SQL",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true, content = @Content(schema = @Schema(implementation = TranslationRequest.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Translated query"),
                    @ApiResponse(responseCode = "400", description = "Malformed JSON or invalid API contract"),
                    @ApiResponse(responseCode = "413", description = "Request body exceeds the configured limit"),
                    @ApiResponse(responseCode = "422", description = "Unsupported or disallowed query"),
                    @ApiResponse(responseCode = "500", description = "Unexpected internal error")
            })
    public TranslationResponse translate(@RequestBody JsonNode body) {
        if (body == null || !body.isObject()) throw new InvalidRequestException("Request body must be an object", null);
        for (String name : body.propertyNames()) {
            if (!REQUEST_PROPERTIES.contains(name)) {
                throw new UnsupportedQueryException("Request property '" + name + "' is not supported", name);
            }
        }
        JsonNode table = body.get("table");
        if (table == null || !table.isString() || table.stringValue().isBlank()) {
            throw new InvalidRequestException("table must be a non-empty string", "table");
        }
        JsonNode query = body.get("query");
        if (query == null || !query.isObject()) throw new InvalidRequestException("query must be an object", "query");
        return service.translate(new TranslationRequest(table.stringValue(), query));
    }
}

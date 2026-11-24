package com.example.querytranslator.service;

import com.example.querytranslator.dto.TranslationRequest;
import com.example.querytranslator.dto.TranslationResponse;
import com.example.querytranslator.generator.ClickHouseSqlGenerator;
import com.example.querytranslator.parser.OpenSearchQueryParser;
import com.example.querytranslator.schema.TableRegistry;
import org.springframework.stereotype.Service;

@Service
public class QueryTranslationService {
    private final OpenSearchQueryParser parser;
    private final ClickHouseSqlGenerator generator;
    private final TableRegistry tableRegistry;

    public QueryTranslationService(OpenSearchQueryParser parser, ClickHouseSqlGenerator generator, TableRegistry tableRegistry) {
        this.parser = parser;
        this.generator = generator;
        this.tableRegistry = tableRegistry;
    }

    public TranslationResponse translate(TranslationRequest request) {
        var table = tableRegistry.requireTable(request.table());
        var query = parser.parse(table, request.query());
        var result = generator.generate(table, query);
        return new TranslationResponse(result.sql(), result.parameters());
    }
}

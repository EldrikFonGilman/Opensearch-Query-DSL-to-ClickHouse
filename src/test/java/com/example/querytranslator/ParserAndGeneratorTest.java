package com.example.querytranslator;

import com.example.querytranslator.ast.QueryNode;
import com.example.querytranslator.ast.ComparisonNode;
import com.example.querytranslator.config.QueryTranslatorProperties;
import com.example.querytranslator.exception.TranslationException;
import com.example.querytranslator.generator.ClickHouseSqlGenerator;
import com.example.querytranslator.generator.SqlBuildResult;
import com.example.querytranslator.parser.OpenSearchQueryParser;
import com.example.querytranslator.schema.FieldType;
import com.example.querytranslator.schema.TableDefinition;
import com.example.querytranslator.schema.TableRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class mmvParserAndGeneratorTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private TableRegistry registry;
    private OpenSearchQueryParser parser;
    private ClickHouseSqlGenerator generator;
    private TableDefinition table;
    private QueryTranslatorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new QueryTranslatorProperties();
        QueryTranslatorProperties.TableProperties definition = new QueryTranslatorProperties.TableProperties();
        definition.setFields(new LinkedHashMap<>(Map.of(
                "source", FieldType.STRING, "message", FieldType.STRING,
                "EventID", FieldType.NUMBER, "timestamp", FieldType.TIMESTAMP,
                "active", FieldType.BOOLEAN)));
        properties.setTables(Map.of("windows_logs", definition));
        registry = new TableRegistry(properties);
        parser = new OpenSearchQueryParser(registry, properties);
        generator = new ClickHouseSqlGenerator();
        table = registry.requireTable("windows_logs");
    }

    @Test
    void termUsesTypedValueParameterAndDoesNotInterpolateInjectionText() throws Exception {
        QueryNode ast = parse("{\"term\":{\"source\":\"x' OR 1=1 --\"}}");
        SqlBuildResult result = generator.generate(table, ast);
        assertEquals("SELECT * FROM windows_logs WHERE source = ?", result.sql());
        assertEquals(List.of("x' OR 1=1 --"), result.parameters());
    }

    @Test
    void termsAndRangePreservePlaceholderParameterOrder() throws Exception {
        QueryNode ast = parse("{\"bool\":{\"must\":[{\"terms\":{\"EventID\":[4624,4625]}},{\"range\":{\"timestamp\":{\"gte\":\"2026-11-11T14:30:00Z\",\"lt\":\"2026-11-12T00:00:00+02:00\"}}}]}}");
        SqlBuildResult result = generator.generate(table, ast);
        assertEquals("SELECT * FROM windows_logs WHERE (EventID IN (?, ?) AND timestamp >= ? AND timestamp < ?)", result.sql());
        assertEquals(List.of(4624, 4625, "2026-11-11T14:30:00Z", "2026-11-11T22:00:00Z"), result.parameters());
    }

    @Test
    void shouldIsAlwaysARequiredOrGroupAndMustNotIsNegated() throws Exception {
        QueryNode ast = parse("{\"bool\":{\"must\":[{\"term\":{\"source\":\"a\"}}],\"filter\":[{\"term\":{\"source\":\"b\"}}],\"should\":[{\"term\":{\"EventID\":1}},{\"exists\":{\"field\":\"message\"}}],\"must_not\":[{\"term\":{\"active\":false}}]}}");
        SqlBuildResult result = generator.generate(table, ast);
        assertEquals("SELECT * FROM windows_logs WHERE (source = ? AND source = ? AND (EventID = ? OR message IS NOT NULL) AND NOT (active = ?))", result.sql());
        assertEquals(List.of("a", "b", 1, false), result.parameters());
    }

    @Test
    void wildcardEscapesSqlLikeMetacharactersAndKeepsPatternAsParameter() throws Exception {
        QueryNode ast = parse("{\"wildcard\":{\"message\":\"*a%_\\\\b?\"}}");
        SqlBuildResult result = generator.generate(table, ast);
        assertEquals("SELECT * FROM windows_logs WHERE message LIKE ? ESCAPE '\\\\'", result.sql());
        assertEquals(List.of("%a\\%\\_\\\\b_"), result.parameters());
    }

    @Test
    void exactExistsAndScalarTypesAreSupported() throws Exception {
        QueryNode ast = parse("{\"bool\":{\"must\":[{\"exists\":{\"field\":\"message\"}},{\"term\":{\"active\":true}}]}}");
        assertEquals(List.of(true), generator.generate(table, ast).parameters());
    }

    @Test
    void rejectsUnknownFieldsOperatorsAndUnsupportedOptions() throws Exception {
        assertTranslationError("{\"term\":{\"other\":\"x\"}}", "query.term.other");
        assertTranslationError("{\"fuzzy\":{\"message\":\"x\"}}", "query.fuzzy");
        assertTranslationError("{\"bool\":{\"should\":[{\"term\":{\"source\":\"x\"}}],\"minimum_should_match\":1}}", "query.bool.minimum_should_match");
        assertTranslationError("{\"wildcard\":{\"message\":{\"value\":\"x*\",\"case_insensitive\":true}}}", "query.wildcard.message.case_insensitive");
    }

    @Test
    void rejectsEmptyTermsNullMixedTypesInvalidRangesAndBadTimestamps() throws Exception {
        assertTranslationError("{\"terms\":{\"source\":[]}}", "query.terms.source");
        assertTranslationError("{\"terms\":{\"EventID\":[1,null]}}", "query.terms.EventID[1]");
        assertTranslationError("{\"term\":{\"EventID\":\"1\"}}", "query.term.EventID");
        assertTranslationError("{\"range\":{\"EventID\":{\"gt\":1,\"gte\":2}}}", "query.range.EventID");
        assertTranslationError("{\"range\":{\"timestamp\":{\"gte\":\"2026-11-11T14:30:00\"}}}", "query.range.timestamp.gte");
    }

    @Test
    void rejectsUnknownTable() {
        assertEquals("UNKNOWN_TABLE", assertThrows(TranslationException.class,
                () -> parser.parse("not_allowed", json("{\"term\":{\"source\":\"x\"}}"))).getError().name());
    }

    @Test
    void generatorRefusesAstIdentifiersOutsideTheTableAllowlist() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(table, new ComparisonNode("source; DROP TABLE windows_logs", "x")));
    }

    @Test
    void enforcesConfiguredDepthTermsAndWildcardLimits() throws Exception {
        properties.getLimits().setNestingDepth(1);
        properties.getLimits().setTermsValues(1);
        properties.getLimits().setWildcardLength(1);
        parser = new OpenSearchQueryParser(registry, properties);

        assertTranslationError("{\"bool\":{\"must\":[{\"term\":{\"source\":\"x\"}}]}}", "query.bool.must[0]");
        assertTranslationError("{\"terms\":{\"source\":[\"x\",\"y\"]}}", "query.terms.source");
        assertTranslationError("{\"wildcard\":{\"message\":\"x?\"}}", "query.wildcard.message");
    }

    private QueryNode parse(String query) throws Exception { return parser.parse(table, mapper.readTree(query)); }
    private JsonNode json(String query) {
        try { return mapper.readTree(query); } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private void assertTranslationError(String query, String expectedPath) throws Exception {
        TranslationException error = assertThrows(TranslationException.class, () -> parse(query));
        assertEquals(expectedPath, error.getPath());
    }
}

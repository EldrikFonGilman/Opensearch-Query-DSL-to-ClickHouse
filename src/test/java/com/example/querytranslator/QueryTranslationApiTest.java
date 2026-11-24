package com.example.querytranslator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueryTranslationApiTest {
    @Autowired MockMvc mockMvc;

    @Test
    void rootRedirectsToSwaggerAndUnknownPathsReturn404() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/swagger-ui.html"));
        mockMvc.perform(get("/no-such-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void translatesTermRequest() throws Exception {
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"table\":\"windows_logs\",\"query\":{\"term\":{\"source\":\"192.168.1.10\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value("SELECT * FROM windows_logs WHERE source = ?"))
                .andExpect(jsonPath("$.parameters[0]").value("192.168.1.10"));
    }

    @Test
    void mapsMalformedJsonTo400AndUnsupportedDslTo422() throws Exception {
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_JSON"));
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"table\":\"windows_logs\",\"query\":{\"fuzzy\":{\"message\":\"x\"}}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_OPERATOR"))
                .andExpect(jsonPath("$.path").value("query.fuzzy"));
    }

    @Test
    void mapsUnknownTableAndPropertiesTo422AndMalformedContractTo400() throws Exception {
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"table\":\"unknown\",\"query\":{\"term\":{\"source\":\"x\"}}}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error").value("UNKNOWN_TABLE"));
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"table\":\"windows_logs\",\"query\":{\"term\":{\"source\":\"x\"}},\"typo\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.path").value("typo"));
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"table\":\"windows_logs\",\"query\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestBodyOverLimitReturns413() throws Exception {
        String body = " ".repeat(1_048_577);
        mockMvc.perform(post("/api/v1/query/translate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("REQUEST_TOO_LARGE"));
    }
}

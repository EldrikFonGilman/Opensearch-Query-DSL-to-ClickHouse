package com.example.querytranslator.web;

import com.example.querytranslator.config.QueryTranslatorProperties;
import com.example.querytranslator.dto.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;
    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(QueryTranslatorProperties properties, ObjectMapper objectMapper) {
        this.maxBytes = properties.getLimits().getRequestSizeBytes();
        this.objectMapper = objectMapper;
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) throw new IllegalStateException("request-size-bytes must be between 1 and 2147483646");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI().substring(contextPath.length());
        return !"POST".equals(request.getMethod()) || !"/api/v1/query/translate".equals(requestPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
            throws jakarta.servlet.ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            writeTooLarge(response);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) {
            writeTooLarge(response);
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse("REQUEST_TOO_LARGE", "Request body exceeds the configured size limit", null));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private CachedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    try {
                        if (input.available() > 0) listener.onDataAvailable();
                        if (input.available() == 0) listener.onAllDataRead();
                    } catch (IOException exception) {
                        listener.onError(exception);
                    }
                }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}

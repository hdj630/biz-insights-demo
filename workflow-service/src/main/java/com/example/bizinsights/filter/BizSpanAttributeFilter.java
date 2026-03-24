package com.example.bizinsights.filter;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class BizSpanAttributeFilter implements Filter {

    private final String stageName;

    public BizSpanAttributeFilter(@Value("${stage.name}") String stageName) {
        this.stageName = stageName;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpReq) {
            String orderId = httpReq.getHeader("X-Order-Id");
            Span currentSpan = Span.current();
            if (orderId != null && currentSpan.getSpanContext().isValid()) {
                currentSpan.setAttribute("aws.tracing.biz.id", orderId);
                currentSpan.setAttribute("aws.tracing.biz.workflow", "ecommerce-purchase");
                currentSpan.setAttribute("aws.tracing.biz.stage", stageName);
            }
        }
        chain.doFilter(request, response);
    }
}

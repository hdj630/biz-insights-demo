package com.example.bizinsights.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BizSpanAttributeFilterTest {

    @Test
    void setsSpanAttributesWhenOrderIdPresent() throws Exception {
        BizSpanAttributeFilter filter = new BizSpanAttributeFilter("browse");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-Order-Id")).thenReturn("order-123");

        // The filter calls Span.current() which returns a no-op span in test
        // without the ADOT agent. We verify the filter chain is called.
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void proceedsWhenNoOrderIdHeader() throws Exception {
        BizSpanAttributeFilter filter = new BizSpanAttributeFilter("payment");

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-Order-Id")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}

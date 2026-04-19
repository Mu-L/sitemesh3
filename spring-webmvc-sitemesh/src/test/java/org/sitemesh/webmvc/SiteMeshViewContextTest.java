/*
 *    Copyright 2009-2024 SiteMesh authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 */
package org.sitemesh.webmvc;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.sitemesh.content.ContentProcessor;
import org.sitemesh.content.tagrules.TagBasedContentProcessor;
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle;
import org.sitemesh.webapp.contentfilter.ResponseMetaData;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Tests for {@link SiteMeshViewContext}.
 */
public class SiteMeshViewContextTest extends TestCase {

    private ContentProcessor contentProcessor;
    private ServletContext servletContext;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ResponseMetaData metaData;

    @Override
    protected void setUp() {
        contentProcessor = new TagBasedContentProcessor(new CoreHtmlTagRuleBundle());
        servletContext = new MockServletContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        metaData = new ResponseMetaData();
    }

    public void testDispatchResolvesViewAndRendersIt() throws Exception {
        final AtomicBoolean rendered = new AtomicBoolean();
        View resolvedView = new View() {
            public String getContentType() { return "text/html"; }
            public void render(Map<String, ?> model, HttpServletRequest req, HttpServletResponse resp) {
                rendered.set(true);
            }
        };
        ViewResolver resolver = (name, loc) -> "decorator".equals(name) ? resolvedView : null;

        SiteMeshViewContext ctx = new SiteMeshViewContext("text/html", request, response, servletContext,
                contentProcessor, metaData, false, resolver, Locale.ENGLISH);

        ctx.dispatch(request, response, "decorator");

        assertTrue("Inner view was not rendered", rendered.get());
    }

    public void testDispatchThrowsServletExceptionWhenViewMissing() {
        ViewResolver resolver = (name, loc) -> null;
        SiteMeshViewContext ctx = new SiteMeshViewContext("text/html", request, response, servletContext,
                contentProcessor, metaData, false, resolver, Locale.ENGLISH);
        try {
            ctx.dispatch(request, response, "missing");
            fail("Expected ServletException for missing view");
        } catch (ServletException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing"));
        } catch (Exception e) {
            fail("Expected ServletException, got " + e);
        }
    }

    public void testDispatchWrapsResolverExceptionInServletException() {
        ViewResolver resolver = (name, loc) -> { throw new IllegalStateException("boom"); };
        SiteMeshViewContext ctx = new SiteMeshViewContext("text/html", request, response, servletContext,
                contentProcessor, metaData, false, resolver, Locale.ENGLISH);
        try {
            ctx.dispatch(request, response, "foo");
            fail("Expected ServletException wrapping resolver failure");
        } catch (ServletException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        } catch (Exception e) {
            fail("Expected ServletException, got " + e);
        }
    }

    public void testDispatchWrapsRenderExceptionInServletException() {
        View throwingView = new View() {
            public String getContentType() { return "text/html"; }
            public void render(Map<String, ?> m, HttpServletRequest r, HttpServletResponse s) {
                throw new RuntimeException("render-fail");
            }
        };
        ViewResolver resolver = (name, loc) -> throwingView;
        SiteMeshViewContext ctx = new SiteMeshViewContext("text/html", request, response, servletContext,
                contentProcessor, metaData, false, resolver, Locale.ENGLISH);
        try {
            ctx.dispatch(request, response, "foo");
            fail("Expected ServletException wrapping render failure");
        } catch (ServletException e) {
            assertNotNull(e.getCause());
            assertEquals("render-fail", e.getCause().getMessage());
        } catch (Exception e) {
            fail("Expected ServletException, got " + e);
        }
    }
}

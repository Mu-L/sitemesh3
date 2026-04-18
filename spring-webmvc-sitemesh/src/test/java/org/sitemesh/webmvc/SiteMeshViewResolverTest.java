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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.sitemesh.DecoratorSelector;
import org.sitemesh.SiteMeshContext;
import org.sitemesh.content.ContentProcessor;
import org.sitemesh.content.tagrules.TagBasedContentProcessor;
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**
 * Tests for {@link SiteMeshViewResolver}.
 */
public class SiteMeshViewResolverTest extends TestCase {

    private ContentProcessor contentProcessor;
    private DecoratorSelector<SiteMeshContext> decoratorSelector;
    private MockServletContext servletContext;

    @SuppressWarnings("unchecked")
    @Override
    protected void setUp() {
        contentProcessor = new TagBasedContentProcessor(new CoreHtmlTagRuleBundle());
        decoratorSelector = (c, x) -> new String[0];
        servletContext = new MockServletContext();
    }

    private static View plainView() {
        return new View() {
            public String getContentType() { return "text/html"; }
            public void render(Map<String, ?> m, HttpServletRequest r, HttpServletResponse s) { }
        };
    }

    private static View redirectView() {
        class RedirectView implements View, SmartView {
            public String getContentType() { return "text/html"; }
            public void render(Map<String, ?> m, HttpServletRequest r, HttpServletResponse s) { }
            public boolean isRedirectView() { return true; }
        }
        return new RedirectView();
    }

    private SiteMeshViewResolver newResolver(ViewResolver inner) {
        return new SiteMeshViewResolver(inner, contentProcessor, decoratorSelector, servletContext);
    }

    public void testReturnsNullWhenInnerReturnsNull() throws Exception {
        ViewResolver inner = (name, locale) -> null;
        assertNull(newResolver(inner).resolveViewName("anything", Locale.ENGLISH));
    }

    public void testPassesThroughRedirectSmartView() throws Exception {
        View redirect = redirectView();
        ViewResolver inner = (name, locale) -> redirect;
        View resolved = newResolver(inner).resolveViewName("go", Locale.ENGLISH);
        assertSame(redirect, resolved);
    }

    public void testPassesThroughAlreadyWrappedSiteMeshView() throws Exception {
        View wrapped = new SiteMeshView(plainView(), contentProcessor, decoratorSelector, servletContext, null);
        ViewResolver inner = (name, locale) -> wrapped;
        View resolved = newResolver(inner).resolveViewName("some/view", Locale.ENGLISH);
        assertSame(wrapped, resolved);
    }

    public void testLayoutPathExactMatchIsPassThrough() throws Exception {
        View raw = plainView();
        ViewResolver inner = (name, locale) -> raw;
        View resolved = newResolver(inner).resolveViewName("/layouts", Locale.ENGLISH);
        assertSame("exact '/layouts' must not be wrapped", raw, resolved);
    }

    public void testLayoutPathChildIsPassThrough() throws Exception {
        View raw = plainView();
        ViewResolver inner = (name, locale) -> raw;
        View resolved = newResolver(inner).resolveViewName("/layouts/foo", Locale.ENGLISH);
        assertSame(raw, resolved);
    }

    public void testSiblingOfLayoutsPrefixIsWrapped() throws Exception {
        View raw = plainView();
        ViewResolver inner = (name, locale) -> raw;
        View resolved = newResolver(inner).resolveViewName("/layoutsManagement/foo", Locale.ENGLISH);
        assertTrue("sibling path starting with 'layouts' must be wrapped, got " + resolved.getClass(),
                resolved instanceof SiteMeshView);
    }

    public void testNormalViewIsWrapped() throws Exception {
        final Map<String, View> views = new HashMap<>();
        View raw = plainView();
        views.put("home", raw);
        ViewResolver inner = (name, locale) -> views.get(name);

        View resolved = newResolver(inner).resolveViewName("home", Locale.ENGLISH);
        assertTrue(resolved instanceof SiteMeshView);
        assertSame(raw, ((SiteMeshView) resolved).getInnerView());
    }

    public void testCreateSiteMeshViewHookIsUsed() throws Exception {
        final View raw = plainView();
        ViewResolver inner = (name, locale) -> raw;
        class CustomView extends SiteMeshView {
            CustomView(View in) {
                super(in, contentProcessor, decoratorSelector, servletContext, null);
            }
        }
        SiteMeshViewResolver resolver = new SiteMeshViewResolver(inner, contentProcessor, decoratorSelector, servletContext) {
            @Override
            protected SiteMeshView createSiteMeshView(View innerView) {
                return new CustomView(innerView);
            }
        };
        View resolved = resolver.resolveViewName("home", Locale.ENGLISH);
        assertTrue("expected CustomView from hook, got " + resolved.getClass(), resolved instanceof CustomView);
    }

    public void testLayoutPathPrefixIsConfigurable() throws Exception {
        View raw = plainView();
        ViewResolver inner = (name, locale) -> raw;
        SiteMeshViewResolver resolver = newResolver(inner);
        resolver.setLayoutPathPrefix("/decorators");

        assertSame(raw, resolver.resolveViewName("/decorators/main", Locale.ENGLISH));
        assertTrue(resolver.resolveViewName("/layouts/main", Locale.ENGLISH) instanceof SiteMeshView);
    }
}

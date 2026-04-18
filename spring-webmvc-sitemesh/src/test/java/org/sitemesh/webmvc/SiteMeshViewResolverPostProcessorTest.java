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

import junit.framework.TestCase;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Tests for {@link SiteMeshViewResolverPostProcessor}.
 */
public class SiteMeshViewResolverPostProcessorTest extends TestCase {

    private DefaultListableBeanFactory registry;

    @Override
    protected void setUp() {
        registry = new DefaultListableBeanFactory();
    }

    private void registerTarget(String name) {
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(InternalResourceViewResolver.class);
        registry.registerBeanDefinition(name, def);
    }

    public void testTargetRenamedAndPrimaryWrapperRegistered() {
        registerTarget("jspViewResolver");

        SiteMeshViewResolverPostProcessor pp = new SiteMeshViewResolverPostProcessor();
        pp.postProcessBeanDefinitionRegistry(registry);

        assertTrue("original bean should be renamed to jspViewResolverInner",
                registry.containsBeanDefinition("jspViewResolverInner"));

        assertTrue("wrapper should be registered under the original name",
                registry.containsBeanDefinition("jspViewResolver"));

        BeanDefinition wrapper = registry.getBeanDefinition("jspViewResolver");
        assertEquals(SiteMeshViewResolver.class.getName(), wrapper.getBeanClassName());
        assertTrue("wrapper must be primary", wrapper.isPrimary());

        Object arg0 = wrapper.getConstructorArgumentValues().getIndexedArgumentValue(0, null).getValue();
        assertTrue(arg0 instanceof RuntimeBeanReference);
        assertEquals("jspViewResolverInner", ((RuntimeBeanReference) arg0).getBeanName());
    }

    public void testGracefulWhenTargetMissing() {
        SiteMeshViewResolverPostProcessor pp = new SiteMeshViewResolverPostProcessor();
        pp.postProcessBeanDefinitionRegistry(registry); // must not throw

        assertFalse(registry.containsBeanDefinition("jspViewResolver"));
        assertFalse(registry.containsBeanDefinition("jspViewResolverInner"));
    }

    public void testCustomNames() {
        registerTarget("myViewResolver");

        SiteMeshViewResolverPostProcessor pp = new SiteMeshViewResolverPostProcessor();
        pp.setTargetViewResolverBeanName("myViewResolver");
        pp.setInnerBeanName("myInner");
        pp.setSiteMeshViewResolverBeanName("smViewResolver");
        pp.setContentProcessorBeanName("cp");
        pp.setDecoratorSelectorBeanName("ds");
        pp.setServletContextBeanName("sc");
        pp.postProcessBeanDefinitionRegistry(registry);

        assertTrue(registry.containsBeanDefinition("myInner"));
        assertTrue(registry.containsBeanDefinition("smViewResolver"));
        // alias from custom wrapper name back to the original target name
        assertTrue("alias from smViewResolver to myViewResolver should exist",
                registry.isAlias("myViewResolver"));
        BeanDefinition wrapper = registry.getBeanDefinition("smViewResolver");
        Object arg1 = wrapper.getConstructorArgumentValues().getIndexedArgumentValue(1, null).getValue();
        assertEquals("cp", ((RuntimeBeanReference) arg1).getBeanName());
    }
}

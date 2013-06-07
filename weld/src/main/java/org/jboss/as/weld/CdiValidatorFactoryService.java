/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.weld;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.Set;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.validation.ValidatorFactory;

import org.jboss.as.ee.beanvalidation.BeanValidationAttachments;
import org.jboss.as.ee.beanvalidation.LazyValidatorFactory;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.weld.literal.AnyLiteral;
import org.hibernate.validator.cdi.HibernateValidator;

/**
 * Service that replaces the delegate of LazyValidatorFactory with a CDI-enabled
 * ValidatorFactory.
 *
 * @author Farah Juma
 */
public class CdiValidatorFactoryService implements Service<CdiValidatorFactoryService> {

    public static final ServiceName SERVICE_NAME = ServiceName.of("CdiValidatorFactoryService");
    private final InjectedValue<WeldBootstrapService> weldContainer = new InjectedValue<WeldBootstrapService>();
    private final ClassLoader classLoader;
    private final DeploymentUnit deploymentUnit;

    private BeanManager beanManager;

    public CdiValidatorFactoryService(DeploymentUnit deploymentUnit, ClassLoader classLoader) {
        this.deploymentUnit = deploymentUnit;
        this.classLoader = classLoader;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final ClassLoader cl = SecurityActions.getContextClassLoader();
        try {
            SecurityActions.setContextClassLoader(classLoader);
            beanManager = weldContainer.getValue().getBeanManager();

            // Get the CDI-enabled ValidatorFactory
            ValidatorFactory validatorFactory = getReference(ValidatorFactory.class);

            // Replace the delegate of LazyValidatorFactory
            LazyValidatorFactory lazyValidatorFactory = (LazyValidatorFactory)(deploymentUnit.getAttachment(BeanValidationAttachments.VALIDATOR_FACTORY));
            lazyValidatorFactory.replaceDelegate(validatorFactory);
            deploymentUnit.putAttachment(BeanValidationAttachments.VALIDATOR_FACTORY,lazyValidatorFactory);
        } finally {
            SecurityActions.setContextClassLoader(cl);
        }
    }

    private <T> T getReference(Class<T> clazz) {
        Set<Bean<?>> beans = beanManager.getBeans(clazz, AnyLiteral.INSTANCE);
        Iterator<Bean<?>> i = beans.iterator();

        while(i.hasNext()) {
            Bean<?> bean = i.next();
            for (Annotation annotation : bean.getQualifiers()) {
                if (annotation.annotationType().getName().equals(HibernateValidator.class.getName())) {
                    CreationalContext<?> context = beanManager.createCreationalContext( bean );
                    return (T) beanManager.getReference( bean, clazz, context );
                }
            }
        }
        return null;
    }

    @Override
    public synchronized void stop(final StopContext context) {
    }

    @Override
    public synchronized CdiValidatorFactoryService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<WeldBootstrapService> getWeldContainer() {
        return weldContainer;
    }
}

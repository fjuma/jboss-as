/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import org.hibernate.validator.cdi.HibernateValidator;
import org.jboss.as.ee.beanvalidation.BeanValidationAttachments;
import org.jboss.as.ee.beanvalidation.LazyValidatorFactory;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.modules.Module;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.weld.literal.AnyLiteral;
import org.wildfly.security.manager.WildFlySecurityManager;

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

    /**
     * Create the CdiValidatorFactoryService instance.
     *
     * @param deploymentUnit the deployment unit
     */
    public CdiValidatorFactoryService(DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
        final Module module = this.deploymentUnit.getAttachment(Attachments.MODULE);
        this.classLoader = module.getClassLoader();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final ClassLoader cl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
        try {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(classLoader);
            beanManager = weldContainer.getValue().getBeanManager();

            // Get the CDI-enabled ValidatorFactory
            ValidatorFactory validatorFactory = getReference(ValidatorFactory.class);

            // Replace the delegate of LazyValidatorFactory
            LazyValidatorFactory lazyValidatorFactory = (LazyValidatorFactory)(deploymentUnit.getAttachment(BeanValidationAttachments.VALIDATOR_FACTORY));
            lazyValidatorFactory.replaceDelegate(validatorFactory);
        } finally {
            WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(cl);
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void stop(final StopContext context) {
    }

    /** {@inheritDoc} */
    @Override
    public synchronized CdiValidatorFactoryService getValue()
            throws IllegalStateException, IllegalArgumentException {
        return this;
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

    public InjectedValue<WeldBootstrapService> getWeldContainer() {
        return weldContainer;
    }
}

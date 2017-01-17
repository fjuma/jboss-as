/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

package org.jboss.as.ejb3.remote;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.as.ejb3.deployment.DeploymentRepository;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.ejb.client.Affinity;
import org.jboss.ejb.server.Association;
import org.jboss.ejb.server.ListenerHandle;
import org.jboss.ejb.server.ModuleAvailabilityListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceRegistration;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.LocalRegistryAndDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.discovery.spi.RegistryProvider;

/**
 * The EJB server association service.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class AssociationService implements Service<AssociationService> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("ejb", "association");

    private final InjectedValue<DeploymentRepository> deploymentRepositoryInjector = new InjectedValue<>();
    private final InjectedValue<RegistryCollector> registryCollectorInjector = new InjectedValue<>();
    private final InjectedValue<SuspendController> suspendControllerInjector = new InjectedValue<>();

    private final LocalRegistryAndDiscoveryProvider localRegistry = new LocalRegistryAndDiscoveryProvider();

    private Association value;
    private ListenerHandle handle;

    public AssociationService() {
    }

    public void start(final StartContext context) throws StartException {
        // todo clusterRegistryCollector
        // todo suspendController
        value = new AssociationImpl(deploymentRepositoryInjector.getValue());

        // track deployments at an association level for local dispatchers to utilize
        handle = value.registerModuleAvailabilityListener(new ModuleAvailabilityListener() {

            private final ConcurrentHashMap<ModuleIdentifier, ServiceRegistration> map = new ConcurrentHashMap<ModuleIdentifier, ServiceRegistration>();

            public void moduleAvailable(final List<ModuleIdentifier> modules) {
                for (ModuleIdentifier module : modules) {
                    final String appName = module.getAppName();
                    final String moduleName = module.getModuleName();
                    final String distinctName = module.getDistinctName();
                    final ServiceURL.Builder builder = new ServiceURL.Builder();
                    builder.setUri(Affinity.LOCAL.getUri());
                    builder.setAbstractType("ejb");
                    builder.setAbstractTypeAuthority("jboss");
                    if (! appName.isEmpty()) {
                        builder.addAttribute("ejb-app", AttributeValue.fromString(appName));
                    }
                    builder.addAttribute("ejb-module", AttributeValue.fromString(appName + "/" + moduleName));
                    if (! distinctName.isEmpty()) {
                        builder.addAttribute("ejb-distinct", AttributeValue.fromString(distinctName));
                        if (! appName.isEmpty()) {
                            builder.addAttribute("ejb-app-distinct", AttributeValue.fromString(appName + "/" + distinctName));
                        }
                        builder.addAttribute("ejb-module-distinct", AttributeValue.fromString(appName + "/" + moduleName + "/" + distinctName));
                    }
                    final ServiceRegistration serviceRegistration = localRegistry.registerService(builder.create());
                    // should never conflict normally!
                    map.putIfAbsent(module, serviceRegistration);
                }
            }

            public void moduleUnavailable(final List<ModuleIdentifier> modules) {
                for (ModuleIdentifier module : modules) {
                    map.computeIfPresent(module, (i, old) -> { old.close(); return null; });
                }
            }
        });
    }

    public void stop(final StopContext context) {
        value = null;
        handle.close();
    }

    public AssociationService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public InjectedValue<DeploymentRepository> getDeploymentRepositoryInjector() {
        return deploymentRepositoryInjector;
    }

    public InjectedValue<RegistryCollector> getRegistryCollectorInjector() {
        return registryCollectorInjector;
    }

    public InjectedValue<SuspendController> getSuspendControllerInjector() {
        return suspendControllerInjector;
    }

    public RegistryProvider getLocalRegistryProvider() {
        return localRegistry;
    }

    public DiscoveryProvider getLocalDiscoveryProvider() {
        return localRegistry;
    }

    public Association getAssociation() {
        return value;
    }
}

/*
 * Copyright 2018 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.messaging.activemq;

import java.util.Collections;
import java.util.Map;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 *
 * @author Emmanuel Hugonnet (c) 2018 Red Hat, inc.
 */
public class ExternalBrokerConfigurationService implements Service<ExternalBrokerConfigurationService> {
    private final Map<String, TransportConfiguration> connectors;
    private final Map<String, DiscoveryGroupConfiguration> discoveryGroupConfigurations;
    private final Map<String, ServiceName> socketBindings;
    private final Map<String, ServiceName> outboundSocketBindings;
    private final Map<String, ServiceName> groupBindings;
    // mapping between the {broadcast|discovery}-groups and the cluster names they use
    private final Map<String, String> clusterNames;
    // mapping between the {broadcast|discovery}-groups and the command dispatcher factory they use
    private final Map<String, ServiceName> commandDispatcherFactories;

    public ExternalBrokerConfigurationService(final Map<String, TransportConfiguration> connectors,
            Map<String, DiscoveryGroupConfiguration> discoveryGroupConfigurations,
            Map<String, ServiceName> socketBindings,
            Map<String, ServiceName> outboundSocketBindings,
            Map<String, ServiceName> groupBindings,
            Map<String, ServiceName> commandDispatcherFactories,
            Map<String, String> clusterNames) {
        this.connectors = connectors;
        this.discoveryGroupConfigurations = discoveryGroupConfigurations;
        this.clusterNames = clusterNames;
        this.commandDispatcherFactories = commandDispatcherFactories;
        this.groupBindings = groupBindings;
        this.outboundSocketBindings = outboundSocketBindings;
        this.socketBindings = socketBindings;
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    public Map<String, TransportConfiguration> getConnectors() {
        return Collections.unmodifiableMap(connectors);
    }

    public Map<String, ServiceName> getSocketBindings() {
        return Collections.unmodifiableMap(socketBindings);
    }

    public Map<String, ServiceName> getOutboundSocketBindings() {
        return Collections.unmodifiableMap(outboundSocketBindings);
    }

    public Map<String, ServiceName> getGroupBindings() {
        return Collections.unmodifiableMap(groupBindings);
    }

    public Map<String, String> getClusterNames() {
        return Collections.unmodifiableMap(clusterNames);
    }

    public Map<String, ServiceName> getCommandDispatcherFactories() {
        return Collections.unmodifiableMap(commandDispatcherFactories);
    }

    public Map<String, DiscoveryGroupConfiguration> getDiscoveryGroupConfigurations() {
        return Collections.unmodifiableMap(discoveryGroupConfigurations);
    }

    @Override
    public ExternalBrokerConfigurationService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

}

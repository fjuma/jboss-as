/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.messaging.activemq;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Removes a discovery group.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class JGroupDiscoveryGroupRemove extends AbstractRemoveStepHandler {

    public static final JGroupDiscoveryGroupRemove INSTANCE = new JGroupDiscoveryGroupRemove(true);
    public static final JGroupDiscoveryGroupRemove LEGACY_INSTANCE = new JGroupDiscoveryGroupRemove(false);

    private final boolean needLegacyCall;

    private JGroupDiscoveryGroupRemove(boolean needLegacyCall) {
        super();
        this.needLegacyCall = needLegacyCall;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if(needLegacyCall) {
            PathAddress target = context.getCurrentAddress().getParent().append(CommonAttributes.DISCOVERY_GROUP, context.getCurrentAddressValue());
            try {
                context.readResourceFromRoot(target);
                ModelNode op = operation.clone();
                op.get(OP_ADDR).set(target.toModelNode());
                // Fabricate a channel resource if it is missing
                context.addStep(op, DiscoveryGroupRemove.LEGACY_INSTANCE, OperationContext.Stage.MODEL, true);
            } catch( Resource.NoSuchResourceException ex) {
                // Legacy resource doesn't exist
            }
        }
        super.execute(context, operation);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.reloadRequired();
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.revertReloadRequired();
    }
}
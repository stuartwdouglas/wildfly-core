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

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Stuart Douglas
 */
public class ServerSuspendHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ModelDescriptionConstants.SUSPEND;
    private static final AttributeDefinition TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TIMEOUT, ModelType.INT, true)
            .setDefaultValue(new ModelNode(0))
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .build();

    public static final OperationDefinition DEFINITION = getOperationDefinition();

    private final ServerInventory serverInventory;


    public ServerSuspendHandler(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final PathElement element = address.getLastElement();
        final String serverName = element.getValue();
        final int suspendTimeout = TIMEOUT.resolveModelAttribute(context, operation).asInt(); //timeout in seconds, by default is 0
        final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

        context.addStep((context1, operation1) -> {
            // WFLY-2189 trigger a write-runtime authz check
            context1.getServiceRegistry(true);
            final List<ModelNode> errorResponses  = serverInventory.suspendServers(Collections.singleton(serverName), suspendTimeout, blockingTimeout);
            if ( !errorResponses.isEmpty() ){
                context1.getFailureDescription().set(errorResponses.get(0));
            }
        }, OperationContext.Stage.RUNTIME);
    }

    static OperationDefinition getOperationDefinition() {
        assert TIMEOUT != null; //this can happen if the order of the contents is wrong
        return new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("host.server"))
                .setParameters(TIMEOUT)
                .setRuntimeOnly()
                .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
                .build();
    }
}

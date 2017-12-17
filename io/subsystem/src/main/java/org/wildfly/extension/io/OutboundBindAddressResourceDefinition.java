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

package org.wildfly.extension.io;

import static org.wildfly.extension.io.OutboundBindAddressUtils.getBindAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getCidrAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getWorkerService;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.AddHandlerResourceDefinition;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.RemoveHandlerResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.InetAddressValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.MaskedAddressValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.common.net.Inet;
import org.wildfly.extension.io.logging.IOLogger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class OutboundBindAddressResourceDefinition extends PersistentResourceDefinition implements AddHandlerResourceDefinition, RemoveHandlerResourceDefinition{
    static final SimpleAttributeDefinition MATCH = new SimpleAttributeDefinitionBuilder("match", ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setValidator(new MaskedAddressValidator(false, true))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition BIND_ADDRESS = new SimpleAttributeDefinitionBuilder("bind-address", ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setValidator(new InetAddressValidator(false, true))
        .setDefaultValue(new ModelNode(0))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition BIND_PORT = new SimpleAttributeDefinitionBuilder("bind-port", ModelType.INT)
        .setRequired(false)
        .setAllowExpression(true)
        .setValidator(new IntRangeValidator(0, 65535, false, true))
        .setRestartAllServices()
        .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = Collections.unmodifiableList(Arrays.asList( MATCH, BIND_ADDRESS, BIND_PORT));

    private static final String RESOURCE_NAME = "outbound-bind-address";

    static final OutboundBindAddressResourceDefinition INSTANCE = new OutboundBindAddressResourceDefinition();

    private OutboundBindAddressResourceDefinition() {
        super(new Parameters(PathElement.pathElement(RESOURCE_NAME), IOExtension.getResolver(RESOURCE_NAME)));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return ATTRIBUTES;
    }

    public static OutboundBindAddressResourceDefinition getInstance() {
        return INSTANCE;
    }


    public void performRuntimeForAdd(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        final CidrAddressTable<InetSocketAddress> bindingsTable = getWorkerService(context).getBindingsTable();
        if (bindingsTable != null) {
            final CidrAddress cidrAddress = getCidrAddress(operation, context);
            final InetSocketAddress bindAddress = getBindAddress(operation, context);
            final InetSocketAddress existing = bindingsTable.putIfAbsent(cidrAddress, bindAddress);
            if (existing != null) {
                throw IOLogger.ROOT_LOGGER.unexpectedBindAddressConflict(context.getCurrentAddress(), cidrAddress, bindAddress, existing);
            }
        }
    }

    public void rollbackRuntimeForAdd(final OperationContext context, final ModelNode operation, final Resource resource) {
        getWorkerService(context).getBindingsTable().removeExact(
                Inet.parseCidrAddress(operation.require("match").asString()),
                new InetSocketAddress(
                        Inet.parseInetAddress(operation.require("bind-address").asString()),
                        operation.get("bind-port").asInt(0)
                )
        );
    }

    @Override
    public void performRuntimeForRemove(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final CidrAddressTable<InetSocketAddress> bindingsTable = getWorkerService(context).getBindingsTable();
        if (bindingsTable != null) {
            final CidrAddress cidrAddress = getCidrAddress(model, context);
            final InetSocketAddress bindAddress = getBindAddress(model, context);
            bindingsTable.removeExact(cidrAddress, bindAddress);
        }
    }
}

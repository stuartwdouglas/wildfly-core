/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.jmx;

import static org.jboss.as.jmx.CommonAttributes.JMX;
import static org.jboss.as.jmx.CommonAttributes.REMOTING_CONNECTOR;
import static org.jboss.as.jmx.JMXSubsystemAdd.getDomainName;

import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.AddHandlerResourceDefinition;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.RemoveHandlerResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.remoting.management.ManagementRemotingServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceName;
import org.jboss.remoting3.Endpoint;

/**
 *
 * @author Stuart Douglas
 */
public class RemotingConnectorResource extends SimpleResourceDefinition implements AddHandlerResourceDefinition, RemoveHandlerResourceDefinition {

    static final PathElement REMOTE_CONNECTOR_CONFIG_PATH = PathElement.pathElement(REMOTING_CONNECTOR, JMX);
    static final SimpleAttributeDefinition USE_MANAGEMENT_ENDPOINT
            = new SimpleAttributeDefinitionBuilder(CommonAttributes.USE_MANAGEMENT_ENDPOINT, ModelType.BOOLEAN, true)
            .setDefaultValue(new ModelNode(true))
            .setAllowExpression(true)
            .build();

    static final String REMOTING_CAPABILITY = "org.wildfly.remoting.endpoint";
    static final RuntimeCapability<Void> REMOTE_JMX_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.management.jmx.remote")
            .addRequirements(JMXSubsystemRootResource.JMX_CAPABILITY_NAME)
            .build();

    static final RemotingConnectorResource INSTANCE = new RemotingConnectorResource();

    private RemotingConnectorResource() {
        super(new Parameters(REMOTE_CONNECTOR_CONFIG_PATH,
                JMXExtension.getResourceDescriptionResolver(CommonAttributes.REMOTING_CONNECTOR))
                .addCapabilities(REMOTE_JMX_CAPABILITY));
    }
    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(USE_MANAGEMENT_ENDPOINT) {
            @Override
            protected void recordCapabilitiesAndRequirements(OperationContext context, AttributeDefinition attributeDefinition, ModelNode newValue, ModelNode oldValue) {
                super.recordCapabilitiesAndRequirements(context, attributeDefinition, newValue, oldValue);

                Boolean needRemoting = needRemoting(context, newValue);
                if (needRemoting != null) {
                    if (needRemoting) {
                        context.registerAdditionalCapabilityRequirement(REMOTING_CAPABILITY,
                                REMOTE_JMX_CAPABILITY.getName(),
                                USE_MANAGEMENT_ENDPOINT.getName());
                    } else {
                        context.deregisterCapabilityRequirement(REMOTING_CAPABILITY, REMOTE_JMX_CAPABILITY.getName());
                    }
                }
            }

            private Boolean needRemoting(OperationContext context, ModelNode attributeValue) {
                // Set up a fake model to resolve the USE_MANAGEMENT_ENDPOINT value
                ModelNode model = new ModelNode();
                model.get(USE_MANAGEMENT_ENDPOINT.getName()).set(attributeValue);
                try {
                    return !USE_MANAGEMENT_ENDPOINT.resolveModelAttribute(context, model).asBoolean();
                } catch (OperationFailedException ofe) {
                    if (model.get(USE_MANAGEMENT_ENDPOINT.getName()).getType() == ModelType.EXPRESSION) {
                        // Must be a vault expression or something we can't resolve in Stage.MODEL.
                        // So we can only do nothing and hope for the best when they reload
                        return null;
                    }
                    throw new IllegalStateException(ofe);
                }
            }
        };
        resourceRegistration.registerReadWriteAttribute(USE_MANAGEMENT_ENDPOINT, null, writeHandler);
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Collections.singleton(USE_MANAGEMENT_ENDPOINT);
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(REMOTE_JMX_CAPABILITY);
    }

    @Override
    public boolean requiresRuntimeForAdd(OperationContext context) {
        return context.isDefaultRequiresRuntime() && (context.getProcessType() != ProcessType.EMBEDDED_HOST_CONTROLLER);
    }

    @Override
    public void performRuntimeForAdd(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {

        boolean useManagementEndpoint = RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.resolveModelAttribute(context, model).asBoolean();

        ServiceName remotingCapability;
        if (!useManagementEndpoint) {
            // Use the remoting capability
            // if (context.getProcessType() == ProcessType.DOMAIN_SERVER) then DomainServerCommunicationServices
            // installed the "remoting subsystem" endpoint and we don't even necessarily *have to* have a remoting
            // subsystem and possibly we could skip adding the requirement for its capability. But really, specifying
            // not to use the management endpoint and then not configuring a remoting subsystem is a misconfiguration,
            // and we should treat it as such. So, we add the requirement no matter what.
            context.requireOptionalCapability(RemotingConnectorResource.REMOTING_CAPABILITY, RemotingConnectorResource.REMOTE_JMX_CAPABILITY.getName(),
                    RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.getName());
            remotingCapability = context.getCapabilityServiceName(RemotingConnectorResource.REMOTING_CAPABILITY, Endpoint.class);
        } else {
            remotingCapability = ManagementRemotingServices.MANAGEMENT_ENDPOINT;
        }
        // Read the model for the JMX subsystem to find the domain name for the resolved/expressions models (if they are exposed).
        PathAddress address = PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR));
        PathAddress parentAddress = address.subAddress(0, address.size() - 1);
        ModelNode jmxSubsystemModel = Resource.Tools.readModel(context.readResourceFromRoot(parentAddress, true));
        String resolvedDomain = getDomainName(context, jmxSubsystemModel, CommonAttributes.RESOLVED);
        String expressionsDomain = getDomainName(context, jmxSubsystemModel, CommonAttributes.EXPRESSION);

        RemotingConnectorService.addService(context.getServiceTarget(), remotingCapability, resolvedDomain, expressionsDomain);
    }


    public void performRuntimeForRemove(OperationContext context, ModelNode operation, ModelNode model) {
        context.removeService(RemotingConnectorService.SERVICE_NAME);
    }

    public void recoverServicesForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        performRuntimeForAdd(context, operation, model);
    }
}

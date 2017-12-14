/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AddHandlerResourceDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RemoveHandlerResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AuditLogHandlerReferenceResourceDefinition extends SimpleResourceDefinition implements AddHandlerResourceDefinition, RemoveHandlerResourceDefinition{

    static final PathElement PATH_ELEMENT = PathElement.pathElement(HANDLER);
    private final ManagedAuditLogger auditLogger;

    public AuditLogHandlerReferenceResourceDefinition(ManagedAuditLogger auditLogger) {
        super(new Parameters(PATH_ELEMENT,
                DomainManagementResolver.getDeprecatedResolver(AccessAuditResourceDefinition.DEPRECATED_MESSAGE_CATEGORY, "core.management.audit-log.handler-reference")));
        setDeprecated(ModelVersion.create(1, 7));
        this.auditLogger = auditLogger;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
    }

    @Override
    public void populateModelForAdd(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
        // TODO use capability based reference validation
        final PathAddress addr = PathAddress.pathAddress(operation.require(OP_ADDR));
        String name = addr.getLastElement().getValue();
        if (!HandlerUtil.lookForHandler(context, addr, name)) {
            throw DomainManagementLogger.ROOT_LOGGER.noHandlerCalled(name);
        }
        resource.getModel().setEmptyObject();
    }

    @Override
    public boolean requiresRuntimeForAdd(OperationContext context){
        return auditLogger != null;
    }

    @Override
    public void performRuntimeForAdd(OperationContext context, ModelNode operation, ModelNode model)
            throws OperationFailedException {
        auditLogger.getUpdater().addHandlerReference(PathAddress.pathAddress(operation.require(OP_ADDR)));
    }

    @Override
    public void rollbackRuntimeForAdd(OperationContext context, ModelNode operation, Resource resource) {
        auditLogger.getUpdater().rollbackChanges();
    }

    @Override
    public boolean requiresRuntimeForRemove(OperationContext context){
        return auditLogger != null;
    }

    @Override
    public void performRuntimeForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        auditLogger.getUpdater().removeHandlerReference(PathAddress.pathAddress(operation.require(OP_ADDR)));
    }

    @Override
    public void recoverServicesForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        auditLogger.getUpdater().rollbackChanges();
    }

}

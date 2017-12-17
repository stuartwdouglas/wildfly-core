package org.jboss.as.controller;

import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

public interface AddHandlerResourceDefinition {
    default void performRuntimeForAdd(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

    }

    default void populateModelForAdd(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (AttributeDefinition attr : getAttributes()) {
            attr.validateAndSet(operation, resource.getModel());
        }
    }

    default void rollbackRuntimeForAdd(OperationContext context, ModelNode operation, Resource resource) {}

    default boolean recordCapabilitiesAndRequirementsForAdd(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        return true;
    }

    default boolean requiresRuntimeForAdd(OperationContext context) {
        return context.isDefaultRequiresRuntime();
    }

    default Collection<AttributeDefinition> getAttributes() {
        return Collections.emptyList();
    }

    default Resource createResourceForAdd(OperationContext context) {
        return context.createResource(PathAddress.EMPTY_ADDRESS);
    }
}

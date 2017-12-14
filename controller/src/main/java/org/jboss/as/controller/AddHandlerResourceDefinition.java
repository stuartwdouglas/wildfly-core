package org.jboss.as.controller;

import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

public interface AddHandlerResourceDefinition {
    default void performRuntimeForAdd(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        performRuntimeForAdd(context, operation, resource.getModel());
    }

    default void performRuntimeForAdd(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {}

    default void populateModelForAdd(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        populateModelForAdd(context, operation, resource.getModel());
    }

    default void populateModelForAdd(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        populateModelForAdd(operation, model);
    }

    default void populateModelForAdd(ModelNode operation, ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : getAttributes()) {
            attr.validateAndSet(operation, model);
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

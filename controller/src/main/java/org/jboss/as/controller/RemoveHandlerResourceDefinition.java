package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

public interface RemoveHandlerResourceDefinition {
    default boolean requiresRuntimeForRemove(OperationContext context) {
        return context.isDefaultRequiresRuntime();
    }

    default void performRuntimeForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {}

    default void recoverServicesForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {}
}

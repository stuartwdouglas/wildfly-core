package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.OperationEntry.Flag;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A persistent resource definition. This needs to be combined with {@link PersistentResourceXMLDescription} to
 * simplify the process of creating parsers and persisters.
 *
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public abstract class PersistentResourceDefinition extends SimpleResourceDefinition {

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
        super(pathElement, descriptionResolver);
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        super(pathElement, descriptionResolver, addHandler, removeHandler);
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler, OperationStepHandler removeHandler, OperationEntry.Flag addRestartLevel, OperationEntry.Flag removeRestartLevel) {
        super(new Parameters(pathElement, descriptionResolver)
                        .setAddHandler(addHandler)
                        .setRemoveHandler(removeHandler)
                        .setAddRestartLevel(addRestartLevel)
                        .setRemoveRestartLevel(removeRestartLevel)
        );
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler,
                                           OperationStepHandler removeHandler, boolean isRuntime) {
        super(new Parameters(pathElement, descriptionResolver)
                               .setAddHandler(addHandler)
                               .setRemoveHandler(removeHandler)
                               .setRuntime(isRuntime)
               );
    }

    protected PersistentResourceDefinition(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler,
                                           OperationStepHandler removeHandler, OperationEntry.Flag addRestartLevel, OperationEntry.Flag removeRestartLevel, boolean isRuntime) {
        super(new Parameters(pathElement, descriptionResolver)
                        .setAddHandler(addHandler)
                        .setRemoveHandler(removeHandler)
                        .setAddRestartLevel(addRestartLevel)
                        .setRemoveRestartLevel(removeRestartLevel)
                        .setRuntime(isRuntime)
        );
    }

    protected PersistentResourceDefinition(Parameters parameters){
        super(parameters);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
        for (PersistentResourceDefinition child : getChildren()) {
            resourceRegistration.registerSubModel(child);
        }
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        ReloadRequiredWriteAttributeHandler handler = new ReloadRequiredWriteAttributeHandler(getAttributes());
        for (AttributeDefinition attr : getAttributes()) {
            if(!attr.getImmutableFlags().contains(AttributeAccess.Flag.RESTART_ALL_SERVICES)) {
                throw ControllerLogger.ROOT_LOGGER.attributeWasNotMarkedAsReloadRequired(attr.getName(), resourceRegistration.getPathAddress());
            }
            resourceRegistration.registerReadWriteAttribute(attr, null, handler);
        }
    }



    protected List<? extends PersistentResourceDefinition> getChildren() {
        return Collections.emptyList();
    }

    public abstract Collection<AttributeDefinition> getAttributes();

    public static class Parameters extends SimpleResourceDefinition.Parameters {

        /**
         * Creates a Parameters object
         *
         * @param pathElement         the path element of the created ResourceDefinition. Cannot be {@code null}
         * @param descriptionResolver the description provider. Cannot be {@code null}
         */
        public Parameters(PathElement pathElement, ResourceDescriptionResolver descriptionResolver) {
            super(pathElement, descriptionResolver);
        }

        public Parameters setDescriptionResolver(ResourceDescriptionResolver descriptionResolver) {
            super.setDescriptionResolver(descriptionResolver);

            return this;
        }

        public Parameters setAddHandler(OperationStepHandler addHandler) {
            super.setAddHandler(addHandler);

            return this;
        }

        public Parameters setRemoveHandler(OperationStepHandler removeHandler) {
            super.setRemoveHandler(removeHandler);

            return this;
        }

        public Parameters setAddRestartLevel(Flag addRestartLevel) {
            super.setAddRestartLevel(addRestartLevel);

            return this;
        }

        public Parameters setRemoveRestartLevel(Flag removeRestartLevel) {
            super.setRemoveRestartLevel(removeRestartLevel);

            return this;
        }

        public Parameters setRuntime() {
            super.setRuntime();

            return this;
        }

        public Parameters setRuntime(boolean isRuntime) {
            super.setRuntime(isRuntime);

            return this;
        }

        public Parameters setDeprecationData(DeprecationData deprecationData) {
            super.setDeprecationData(deprecationData);

            return this;
        }

        public org.jboss.as.controller.SimpleResourceDefinition.Parameters setDeprecatedSince(ModelVersion deprecatedSince) {
            super.setDeprecatedSince(deprecatedSince);

            return this;
        }

        public Parameters setOrderedChild() {
            super.setOrderedChild();

            return this;
        }

        public Parameters setCapabilities(RuntimeCapability... capabilities) {
            super.setCapabilities(capabilities);

            return this;
        }

    }
}

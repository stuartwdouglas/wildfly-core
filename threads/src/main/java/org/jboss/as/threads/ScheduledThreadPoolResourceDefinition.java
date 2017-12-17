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

package org.jboss.as.threads;

import org.jboss.as.controller.AddHandlerResourceDefinition;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.RemoveHandlerResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a scheduled thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ScheduledThreadPoolResourceDefinition extends PersistentResourceDefinition implements AddHandlerResourceDefinition, RemoveHandlerResourceDefinition {

    static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
            PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY};

    static final AttributeDefinition[] RW_ATTRIBUTES = new AttributeDefinition[]{};

    private final ScheduledThreadPoolWriteAttributeHandler writeAttributeHandler;
    private final ScheduledThreadPoolMetricsHandler metricsHandler;
    private final boolean registerRuntimeOnly;

    private final ThreadFactoryResolver threadFactoryResolver;
    private final ServiceName serviceNameBase;

    public static ScheduledThreadPoolResourceDefinition create(boolean registerRuntimeOnly) {
        return create(CommonAttributes.SCHEDULED_THREAD_POOL, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER, ThreadsServices.EXECUTOR, registerRuntimeOnly);
    }

    public static ScheduledThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        return new ScheduledThreadPoolResourceDefinition(type, serviceNameBase, registerRuntimeOnly, threadFactoryResolver);
    }

    private ScheduledThreadPoolResourceDefinition(String type,
                                                  ServiceName serviceNameBase, boolean registerRuntimeOnly, ThreadFactoryResolver threadFactoryResolver) {
        super(new Parameters(PathElement.pathElement(type),
                new ThreadPoolResourceDescriptionResolver(CommonAttributes.SCHEDULED_THREAD_POOL, ThreadsExtension.RESOURCE_NAME,
                        ThreadsExtension.class.getClassLoader())));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.writeAttributeHandler = new ScheduledThreadPoolWriteAttributeHandler(serviceNameBase);
        this.metricsHandler = new ScheduledThreadPoolMetricsHandler(serviceNameBase);
        this.serviceNameBase = serviceNameBase;
        this.threadFactoryResolver = threadFactoryResolver;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeAttributeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    @Override
    public void performRuntimeForAdd(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {

        final ThreadPoolManagementUtils.BaseThreadPoolParameters params = ThreadPoolManagementUtils.parseScheduledThreadPoolParameters(context, operation, resource.getModel());

        final ScheduledThreadPoolService service = new ScheduledThreadPoolService(params.getMaxThreads(), params.getKeepAliveTime());

        ThreadPoolManagementUtils.installThreadPoolService(service, params.getName(), serviceNameBase,
                params.getThreadFactory(), threadFactoryResolver, service.getThreadFactoryInjector(),
                context.getServiceTarget());
    }

    @Override
    public void performRuntimeForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        final ThreadPoolManagementUtils.BaseThreadPoolParameters params =
                ThreadPoolManagementUtils.parseScheduledThreadPoolParameters(context, operation, model);
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(), serviceNameBase,
                params.getThreadFactory(), threadFactoryResolver,
                context);
    }

    @Override
    public void recoverServicesForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        performRuntimeForAdd(context, operation, context.createResource(PathAddress.EMPTY_ADDRESS));
    }
}

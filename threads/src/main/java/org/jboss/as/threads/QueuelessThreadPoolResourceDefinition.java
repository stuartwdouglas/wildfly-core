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
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.RemoveHandlerResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

import java.util.Arrays;
import java.util.Collection;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a queueless thread pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class QueuelessThreadPoolResourceDefinition extends PersistentResourceDefinition implements AddHandlerResourceDefinition, RemoveHandlerResourceDefinition {


    static final AttributeDefinition[] BLOCKING_ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
            PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY};

    static final AttributeDefinition[] NON_BLOCKING_ATTRIBUTES = new AttributeDefinition[BLOCKING_ATTRIBUTES.length + 1];

    static final AttributeDefinition[] RW_ATTRIBUTES = new AttributeDefinition[] {PoolAttributeDefinitions.KEEPALIVE_TIME,
            PoolAttributeDefinitions.MAX_THREADS};

    static {
        System.arraycopy(BLOCKING_ATTRIBUTES, 0, NON_BLOCKING_ATTRIBUTES, 0, BLOCKING_ATTRIBUTES.length);
        NON_BLOCKING_ATTRIBUTES[NON_BLOCKING_ATTRIBUTES.length - 1] = PoolAttributeDefinitions.HANDOFF_EXECUTOR;
    }

    private final QueuelessThreadPoolWriteAttributeHandler writeHandler;
    private final QueuelessThreadPoolMetricsHandler metricsHandler;
    private final boolean blocking;
    private final boolean registerRuntimeOnly;
    private final ThreadFactoryResolver threadFactoryResolver;
    private final HandoffExecutorResolver handoffExecutorResolver;
    private final ServiceName serviceNameBase;

    public static QueuelessThreadPoolResourceDefinition create(boolean blocking, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(CommonAttributes.BLOCKING_QUEUELESS_THREAD_POOL, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    null, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        } else {
            return create(CommonAttributes.QUEUELESS_THREAD_POOL, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    ThreadsServices.STANDARD_HANDOFF_EXECUTOR_RESOLVER, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static QueuelessThreadPoolResourceDefinition create(boolean blocking, String type, boolean registerRuntimeOnly) {
        if (blocking) {
            return create(type, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    null, ThreadsServices.EXECUTOR, registerRuntimeOnly);

        } else {
            return create(type, ThreadsServices.STANDARD_THREAD_FACTORY_RESOLVER,
                    ThreadsServices.STANDARD_HANDOFF_EXECUTOR_RESOLVER, ThreadsServices.EXECUTOR, registerRuntimeOnly);
        }
    }

    public static QueuelessThreadPoolResourceDefinition create(String type, ThreadFactoryResolver threadFactoryResolver,
                                                               HandoffExecutorResolver handoffExecutorResolver,
                                                               ServiceName serviceNameBase, boolean registerRuntimeOnly) {
        final boolean blocking = handoffExecutorResolver == null;
        final String resolverPrefix = blocking ? CommonAttributes.BLOCKING_QUEUELESS_THREAD_POOL : CommonAttributes.QUEUELESS_THREAD_POOL;
        return new QueuelessThreadPoolResourceDefinition(blocking, registerRuntimeOnly, type, serviceNameBase, resolverPrefix, threadFactoryResolver, handoffExecutorResolver);
    }


    private QueuelessThreadPoolResourceDefinition(boolean blocking, boolean registerRuntimeOnly,
                                                  String type, ServiceName serviceNameBase, String resolverPrefix,
                                                  ThreadFactoryResolver threadFactoryResolver,
                                                  HandoffExecutorResolver handoffExecutorResolver) {
        super(new Parameters(PathElement.pathElement(type),
                new ThreadPoolResourceDescriptionResolver(resolverPrefix, ThreadsExtension.RESOURCE_NAME, ThreadsExtension.class.getClassLoader())));
        this.registerRuntimeOnly = registerRuntimeOnly;
        this.blocking = blocking;
        writeHandler = new QueuelessThreadPoolWriteAttributeHandler(blocking, serviceNameBase);
        metricsHandler = new QueuelessThreadPoolMetricsHandler(serviceNameBase);
        this.threadFactoryResolver = threadFactoryResolver;
        this.handoffExecutorResolver = handoffExecutorResolver;
        this.serviceNameBase = serviceNameBase;
    }


    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(PoolAttributeDefinitions.NAME, ReadResourceNameOperationStepHandler.INSTANCE);
        writeHandler.registerAttributes(resourceRegistration);
        if (registerRuntimeOnly) {
            metricsHandler.registerAttributes(resourceRegistration);
        }
    }

    public boolean isBlocking() {
        return blocking;
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(blocking ? BLOCKING_ATTRIBUTES : NON_BLOCKING_ATTRIBUTES);
    }

    @Override
    public void performRuntimeForAdd(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final ThreadPoolManagementUtils.QueuelessThreadPoolParameters params = ThreadPoolManagementUtils.parseQueuelessThreadPoolParameters(context, operation, model, blocking);

        final QueuelessThreadPoolService service = new QueuelessThreadPoolService(params.getMaxThreads(), blocking, params.getKeepAliveTime());

        ThreadPoolManagementUtils.installThreadPoolService(service, params.getName(), serviceNameBase,
                params.getThreadFactory(), threadFactoryResolver, service.getThreadFactoryInjector(),
                params.getHandoffExecutor(), handoffExecutorResolver, blocking ?  null : service.getHandoffExecutorInjector(),
                context.getServiceTarget());
    }

    @Override
    public void performRuntimeForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        final ThreadPoolManagementUtils.QueuelessThreadPoolParameters params =
                ThreadPoolManagementUtils.parseQueuelessThreadPoolParameters(context, operation, model, blocking);
        ThreadPoolManagementUtils.removeThreadPoolService(params.getName(), serviceNameBase,
                params.getThreadFactory(), threadFactoryResolver,
                params.getHandoffExecutor(), handoffExecutorResolver,
                context);
    }

    @Override
    public void recoverServicesForRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        performRuntimeForAdd(context, operation, model);
    }
}

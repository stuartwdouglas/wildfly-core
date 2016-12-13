/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment.scanner;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.ALL_ATTRIBUTES;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_EXPLODED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_XML;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.AUTO_DEPLOY_ZIPPED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.DEPLOYMENT_TIMEOUT;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.RELATIVE_TO;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.RUNTIME_FAILURE_CAUSES_ROLLBACK;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.SCAN_ENABLED;
import static org.jboss.as.server.deployment.scanner.DeploymentScannerDefinition.SCAN_INTERVAL;

import java.io.File;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.as.controller.ControlledProcessStateService;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.scanner.api.DeploymentOperations;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.threads.JBossThreadFactory;

/**
 * Operation adding a new {@link DeploymentScannerService}.
 *
 * @author John E. Bailey
 * @author Emanuel Muckenhuber
 * @author Stuart Douglas
 */
class DeploymentScannerAdd implements OperationStepHandler {


    private final PathManager pathManager;

    public DeploymentScannerAdd(final PathManager pathManager) {
        this.pathManager = pathManager;
    }

    /**
     * {@inheritDoc
     */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);

        final ModelNode model = resource.getModel();
        for (SimpleAttributeDefinition atr : ALL_ATTRIBUTES) {
            atr.validateAndSet(operation, model);
        }

        boolean stepCompleted = false;

        if (context.isNormalServer()) {

            final boolean enabled = SCAN_ENABLED.resolveModelAttribute(context, operation).asBoolean();
            final boolean bootTimeScan = context.isBooting() && enabled;


            final String path = DeploymentScannerDefinition.PATH.resolveModelAttribute(context, operation).asString();
            final ModelNode relativeToNode = RELATIVE_TO.resolveModelAttribute(context, operation);
            final String relativeTo = relativeToNode.isDefined() ?  relativeToNode.asString() : null;
            final boolean autoDeployZip = AUTO_DEPLOY_ZIPPED.resolveModelAttribute(context, operation).asBoolean();
            final boolean autoDeployExp = AUTO_DEPLOY_EXPLODED.resolveModelAttribute(context, operation).asBoolean();
            final boolean autoDeployXml = AUTO_DEPLOY_XML.resolveModelAttribute(context, operation).asBoolean();
            final long deploymentTimeout = DEPLOYMENT_TIMEOUT.resolveModelAttribute(context, operation).asLong();
            final int scanInterval = SCAN_INTERVAL.resolveModelAttribute(context, operation).asInt();

            final ScheduledExecutorService scheduledExecutorService = createScannerExecutorService();

            final FileSystemDeploymentService bootTimeScanner;
            if (bootTimeScan) {
                final String pathName = pathManager.resolveRelativePathEntry(path, relativeTo);
                File relativePath = null;
                if (relativeTo != null) {
                    relativePath = new File(pathManager.getPathEntry(relativeTo).resolvePath());
                }
                PathAddress ownerAddress = context.getCurrentAddress();
                bootTimeScanner = new FileSystemDeploymentService(ownerAddress, relativeTo, new File(pathName), relativePath, null, scheduledExecutorService,
                        (ControlledProcessStateService) context.getServiceRegistry(false).getService(ControlledProcessStateService.SERVICE_NAME).getValue());
                bootTimeScanner.setAutoDeployExplodedContent(autoDeployExp);
                bootTimeScanner.setAutoDeployZippedContent(autoDeployZip);
                bootTimeScanner.setAutoDeployXMLContent(autoDeployXml);
                bootTimeScanner.setDeploymentTimeout(deploymentTimeout);
                bootTimeScanner.setScanInterval(scanInterval);
            } else {
                bootTimeScanner = null;
            }

            context.addStep((context12, operation12) -> {
                performRuntime(context12, operation12, model, scheduledExecutorService, bootTimeScanner);

                // We count on the context's automatic service removal on rollback
                context12.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
            }, OperationContext.Stage.RUNTIME);


            if (bootTimeScan) {
                final AtomicReference<ModelNode> deploymentOperation = new AtomicReference<ModelNode>();
                final AtomicReference<ModelNode> deploymentResults = new AtomicReference<ModelNode>(new ModelNode());
                final CountDownLatch scanDoneLatch = new CountDownLatch(1);
                final CountDownLatch deploymentDoneLatch = new CountDownLatch(1);
                final DeploymentOperations deploymentOps = new BootTimeScannerDeployment(deploymentOperation, deploymentDoneLatch, deploymentResults, scanDoneLatch);

                scheduledExecutorService.submit(() -> {
                    try {
                        bootTimeScanner.bootTimeScan(deploymentOps);
                    } catch (Throwable t){
                        DeploymentScannerLogger.ROOT_LOGGER.initialScanFailed(t);
                    } finally {
                        scanDoneLatch.countDown();
                    }
                });
                boolean interrupted = false;
                boolean asyncCountDown = false;
                try {
                    scanDoneLatch.await();

                    final ModelNode op = deploymentOperation.get();
                    if (op != null) {
                        final ModelNode result = new ModelNode();
                        final PathAddress opPath = PathAddress.pathAddress(op.get(OP_ADDR));
                        final OperationStepHandler handler = context.getRootResourceRegistration().getOperationHandler(opPath, op.get(OP).asString());
                        context.addStep(result, op, handler, OperationContext.Stage.MODEL);

                        stepCompleted = true;
                        context.completeStep((resultAction, context1, operation1) -> {
                            try {
                                deploymentResults.set(result);
                            } finally {
                                deploymentDoneLatch.countDown();
                            }
                        });
                        asyncCountDown = true;

                    } else {
                        stepCompleted = true;
                        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    throw new RuntimeException(e);
                } finally {
                    if (!asyncCountDown) {
                        deploymentDoneLatch.countDown();
                    }
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (!stepCompleted) {
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }

    static ScheduledExecutorService createScannerExecutorService() {
        final ThreadFactory threadFactory = doPrivileged((PrivilegedAction<ThreadFactory>) () -> new JBossThreadFactory(new ThreadGroup("DeploymentScanner-threads"), Boolean.FALSE, null, "%G - %t", null, null));
        return Executors.newScheduledThreadPool(2, threadFactory);
    }

    static void performRuntime(final OperationContext context, ModelNode operation, ModelNode model,
                                final ScheduledExecutorService executorService,
                                final FileSystemDeploymentService bootTimeScanner) throws OperationFailedException {
        final PathAddress address = context.getCurrentAddress();
        final String path = DeploymentScannerDefinition.PATH.resolveModelAttribute(context, model).asString();
        final Boolean enabled = SCAN_ENABLED.resolveModelAttribute(context, model).asBoolean();
        final Integer interval = SCAN_INTERVAL.resolveModelAttribute(context, model).asInt();
        final String relativeTo = operation.hasDefined(CommonAttributes.RELATIVE_TO) ? RELATIVE_TO.resolveModelAttribute(context, model).asString() : null;
        final Boolean autoDeployZip = AUTO_DEPLOY_ZIPPED.resolveModelAttribute(context, model).asBoolean();
        final Boolean autoDeployExp = AUTO_DEPLOY_EXPLODED.resolveModelAttribute(context, model).asBoolean();
        final Boolean autoDeployXml = AUTO_DEPLOY_XML.resolveModelAttribute(context, model).asBoolean();
        final Long deploymentTimeout = DEPLOYMENT_TIMEOUT.resolveModelAttribute(context, model).asLong();
        final Boolean rollback = RUNTIME_FAILURE_CAUSES_ROLLBACK.resolveModelAttribute(context, model).asBoolean();
        final ServiceTarget serviceTarget = context.getServiceTarget();
        DeploymentScannerService.addService(serviceTarget, address, relativeTo, path, interval, TimeUnit.MILLISECONDS,
                autoDeployZip, autoDeployExp, autoDeployXml, enabled, deploymentTimeout, rollback, bootTimeScanner, executorService);

    }

    private static class BootTimeScannerDeployment implements DeploymentOperations {
        private final AtomicReference<ModelNode> deploymentOperation;
        private final CountDownLatch deploymentDoneLatch;
        private final AtomicReference<ModelNode> deploymentResults;
        private final CountDownLatch scanDoneLatch;

        public BootTimeScannerDeployment(final AtomicReference<ModelNode> deploymentOperation, final CountDownLatch deploymentDoneLatch, final AtomicReference<ModelNode> deploymentResults, final CountDownLatch scanDoneLatch) {
            this.deploymentOperation = deploymentOperation;
            this.deploymentDoneLatch = deploymentDoneLatch;
            this.deploymentResults = deploymentResults;
            this.scanDoneLatch = scanDoneLatch;
        }

        @Override
        public Future<ModelNode> deploy(final ModelNode operation, final ExecutorService executorService) {
            try {
                deploymentOperation.set(operation);
                final FutureTask<ModelNode> task = new FutureTask<ModelNode>(() -> {
                    deploymentDoneLatch.await();
                    return deploymentResults.get();
                });
                executorService.submit(task);
                return task;
            } finally {
                scanDoneLatch.countDown();
            }
        }

        @Override
        public Map<String, Boolean> getDeploymentsStatus() {
            return Collections.emptyMap();
        }

        @Override
        public void close() throws IOException {

        }

        @Override
        public Set<String> getUnrelatedDeployments(ModelNode owner) {
            return Collections.emptySet();
        }
    }
}

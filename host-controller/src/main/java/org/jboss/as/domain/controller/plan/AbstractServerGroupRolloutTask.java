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

package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.security.PrivilegedAction;
import java.util.List;

import javax.security.auth.Subject;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Task responsible for updating a single server-group.
 *
 * @author Emanuel Muckenhuber
 */
// TODO cleanup ServerGroupRolloutTask vs. ServerUpdateTask vs. Concurrent/RollingUpdateTask
abstract class AbstractServerGroupRolloutTask implements Runnable {

    protected final List<ServerUpdateTask> tasks;
    protected final ServerUpdatePolicy updatePolicy;
    protected final ServerTaskExecutor executor;
    protected final Subject subject;
    protected final BlockingTimeout blockingTimeout;

    public AbstractServerGroupRolloutTask(List<ServerUpdateTask> tasks, ServerUpdatePolicy updatePolicy, ServerTaskExecutor executor, Subject subject, BlockingTimeout blockingTimeout) {
        this.tasks = tasks;
        this.updatePolicy = updatePolicy;
        this.executor = executor;
        this.subject = subject;
        this.blockingTimeout = blockingTimeout;
    }

    @Override
    public void run() {
        try {
            // TODO Elytron Revisit and use the Elytron SecurityIdentity instead.
            AccessAuditContext.doAs(null, null, (PrivilegedAction<Void>) () -> {
                execute();
                return null;
            });
        } catch (Throwable t) {
            DomainControllerLogger.HOST_CONTROLLER_LOGGER.debugf(t, "failed to process task %s", tasks.iterator().next().getOperation());
        }
    }

    /**
     * Execute the the rollout task.
     */
    protected abstract void execute();

    /**
     * Record a prepared operation.
     *
     * @param identity the server identity
     * @param prepared the prepared operation
     */
    protected void recordPreparedOperation(final ServerIdentity identity, final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> prepared) {
        final ModelNode preparedResult = prepared.getPreparedResult();
        // Hmm do the server results need to get translated as well as the host one?
        // final ModelNode transformedResult = prepared.getOperation().transformResult(preparedResult);
        updatePolicy.recordServerResult(identity, preparedResult);
        executor.recordPreparedOperation(prepared);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{server-group=" + updatePolicy.getServerGroupName() + "}";
    }

    void handlePreparePhaseTimeout(ServerIdentity identity, ServerUpdateTask task, long timeout) {

        blockingTimeout.proxyTimeoutDetected(identity.toPathAddress());

        // Record a synthetic prepared result so the timeout can impact the updatePolicy and
        // possibly trigger a ServerRequestRestartTask if the overall rollout isn't rolled back
        final ServerTaskExecutor.ServerOperation serverOperation = new ServerTaskExecutor.ServerOperation(identity, task.getOperation(), null, null, OperationResultTransformer.ORIGINAL_RESULT);
        final String failureMsg = ControllerLogger.ROOT_LOGGER.proxiedOperationTimedOut(task.getOperation().get(OP).asString(), identity.toPathAddress(), timeout);
        final ModelNode failureNode = new ModelNode();
        failureNode.get(OUTCOME).set(FAILED);
        failureNode.get(FAILURE_DESCRIPTION).set(failureMsg);
        final BlockingQueueOperationListener.FailedOperation<ServerTaskExecutor.ServerOperation> prepared =
                new BlockingQueueOperationListener.FailedOperation<>(serverOperation, failureNode, true);

        final ModelNode preparedResult = prepared.getPreparedResult();
        updatePolicy.recordServerResult(identity, preparedResult);
        executor.recordOperationPrepareTimeout(prepared);
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.notification.NotificationFilter;
import org.jboss.as.controller.notification.NotificationHandler;
import org.jboss.as.server.logging.ServerLogger;

import java.util.ArrayList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICE;

/**
 * @author Stuart Douglas
 */
class DeploymentCompleteNotifierImpl implements DeploymentCompleteNotifier {

    public static final PathAddress NOTIFICATION_ADDRESS = PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT).append(SERVICE, MANAGEMENT_OPERATIONS);
    private final List<DeploymentCompleteListener> listeners = new ArrayList<>();

    private boolean done;
    private final boolean booting;

    private DeploymentCompleteNotifierImpl(boolean booting) {
        this.booting = booting;
    }

    static DeploymentCompleteNotifierImpl create(final boolean booting, ModelController controller) {
        DeploymentCompleteNotifierImpl ret = new DeploymentCompleteNotifierImpl(booting);
        final NotificationFilter filter = new NotificationFilter() {
            @Override
            public boolean isNotificationEnabled(Notification notification) {
                return notification.getType().equals(ModelDescriptionConstants.RUNTIME_MODIFICATION_COMPLETE);
            }
        };
        NotificationHandler handler = new NotificationHandler() {
            @Override
            public void handleNotification(Notification notification) {
                handleComplete(ret);
                controller.getNotificationRegistry().unregisterNotificationHandler(NOTIFICATION_ADDRESS, this, filter);
            }
        };
        controller.getNotificationRegistry().registerNotificationHandler(NOTIFICATION_ADDRESS, handler, filter);
        return ret;
    }

    @Override
    public boolean isBooting() {
        return booting;
    }

    @Override
    public synchronized void registerCompletionListener(DeploymentCompleteListener listener) {
        if(done) {
            listener.complete();
        } else {
            listeners.add(listener);
        }
    }

    static void handleComplete(final DeploymentCompleteNotifierImpl notifier) {
        synchronized (notifier) {
            notifier.done = true;
            for (DeploymentCompleteListener list : notifier.listeners) {
                try {
                    list.complete();
                } catch (Exception e) {
                    ServerLogger.DEPLOYMENT_LOGGER.deploymentCompleteNotificationFailed(list, e);
                }
            }
        }
    }
}

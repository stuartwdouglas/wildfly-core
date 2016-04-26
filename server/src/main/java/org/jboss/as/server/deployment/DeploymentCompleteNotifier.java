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

/**
 * Interface that allows for notification when a deployment is complete.
 *
 * This notification can be used to perform actions that should not happen till the deployment
 * active. It should not be used for any action that may fail, as by the time the action is invoked
 * the deployment is considered complete (or failed) and any failure in this method will not be reported
 * back to the client.
 *
 * Note that for the purposes of this interface deployment completion is tied to MSC stability, the listener
 * will not be invoked until MSC is stable, so if multiple operations are happening at once the listener will
 * not be invoked until they are all complete.
 *
 * @author Stuart Douglas
 */
public interface DeploymentCompleteNotifier {

    /**
     *
     * @return <code>true</code> if the server is booting
     */
    boolean isBooting();

    /**
     * Registers a listener that is invoked when the deployment is complete.
     *
     * Note that this will be invoked on both sucess and failure.
     *
     * @param listener the listener to invoke
     */
    void registerCompletionListener(DeploymentCompleteListener listener);

    interface DeploymentCompleteListener {

        /**
         * Notification method that is invoked when the deployment is complete. That that this will be called no matter if the deployment succeeded or failed
         *
         */
        void complete();

    }
}

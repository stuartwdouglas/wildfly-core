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

package org.jboss.as.host.controller.ignored;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Handles remove of an ignored domain resource type.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class IgnoredDomainTypeRemoveHandler implements OperationStepHandler {

    IgnoredDomainTypeRemoveHandler() {
    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final IgnoreDomainResourceTypeResource resource =
                IgnoreDomainResourceTypeResource.class.cast(context.removeResource(PathAddress.EMPTY_ADDRESS));

        final boolean booting = context.isBooting();
        if (!booting) {
            context.reloadRequired();
        }

        context.completeStep((resultAction, context1, operation1) -> {
            if (resultAction == OperationContext.ResultAction.KEEP) {
                if (booting) {
                    resource.publish();
                }
            } else if (!booting) {
                context1.revertReloadRequired();
            }
        });
    }
}

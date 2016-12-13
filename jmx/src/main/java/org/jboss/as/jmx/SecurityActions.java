/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.jmx;

import static java.security.AccessController.doPrivileged;

import java.security.PrivilegedAction;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.access.Caller;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Security actions for the 'org.jboss.as.jmx' package.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityActions {

    private SecurityActions() {
    }

    static AccessAuditContext currentAccessAuditContext() {
        return createAccessAuditContextActions().currentContext();
    }

    static Caller createCaller(SecurityIdentity securityIdentity) {

        return createCallerActions().createCaller(securityIdentity);
    }

    private static AccessAuditContextActions createAccessAuditContextActions() {
        return WildFlySecurityManager.isChecking() ? AccessAuditContextActions.PRIVILEGED : AccessAuditContextActions.NON_PRIVILEGED;
    }

    private static CallerActions createCallerActions() {
        return WildFlySecurityManager.isChecking() ? CallerActions.PRIVILEGED : CallerActions.NON_PRIVILEGED;
    }

    private interface AccessAuditContextActions {

        AccessAuditContext currentContext();

        AccessAuditContextActions NON_PRIVILEGED = () -> AccessAuditContext.currentAccessAuditContext();

        AccessAuditContextActions PRIVILEGED = new AccessAuditContextActions() {

            private final PrivilegedAction<AccessAuditContext> PRIVILEGED_ACTION = () -> NON_PRIVILEGED.currentContext();

            @Override
            public AccessAuditContext currentContext() {
                return doPrivileged(PRIVILEGED_ACTION);
            }
        };

    }

    private interface CallerActions {

        Caller createCaller(SecurityIdentity securityIdentity);


        CallerActions NON_PRIVILEGED = securityIdentity -> Caller.createCaller(securityIdentity);

        CallerActions PRIVILEGED = securityIdentity -> doPrivileged((PrivilegedAction<Caller>) () -> NON_PRIVILEGED.createCaller(securityIdentity));

    }

}

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

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;
import static org.jboss.as.domain.management.RealmConfigurationConstants.SUBJECT_CALLBACK_SUPPORTED;
import static org.jboss.as.domain.management.RealmConfigurationConstants.VERIFY_PASSWORD_CALLBACK_SUPPORTED;

import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

import org.jboss.as.core.security.RealmGroup;
import org.jboss.as.core.security.ServerSecurityManager;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.security.SimpleGroup;
import org.wildfly.security.auth.callback.EvidenceVerifyCallback;
import org.wildfly.security.evidence.PasswordGuessEvidence;

/**
 * A CallbackHandler verifying users usernames and passwords by using a JAAS LoginContext.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class JaasCallbackHandler implements Service<CallbackHandlerService>, CallbackHandlerService, CallbackHandler {

    private static final String SERVICE_SUFFIX = "jaas";

    private static final Map<String, String> configurationOptions;

    static {
        Map<String, String> temp = new HashMap<String, String>(2);
        temp.put(SUBJECT_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        temp.put(VERIFY_PASSWORD_CALLBACK_SUPPORTED, Boolean.TRUE.toString());
        configurationOptions = Collections.unmodifiableMap(temp);
    }

    private final String realm;
    private final String name;
    private final boolean assignGroups;

    private final InjectedValue<ServerSecurityManager> securityManagerValue = new InjectedValue<ServerSecurityManager>();

    public JaasCallbackHandler(final String realm, final String name, final boolean assignGroups) {
        this.realm = realm;
        this.name = name;
        this.assignGroups = assignGroups;
    }

    /*
     * CallbackHandlerService Methods
     */

    public AuthMechanism getPreferredMechanism() {
        return AuthMechanism.PLAIN;
    }

    public Set<AuthMechanism> getSupplementaryMechanisms() {
        return Collections.emptySet();
    }

    public Map<String, String> getConfigurationOptions() {
        return configurationOptions;
    }

    public CallbackHandler getCallbackHandler(Map<String, Object> sharedState) {
        return this;
    }

    @Override
    public org.wildfly.security.auth.server.SecurityRealm getElytronSecurityRealm() {
        // TODO Elytron Add support for calling out to JAAS
        return null;
    }

    @Override
    public boolean isReadyForHttpChallenge() {
        // Can't check so assume it is ready.
        return true;
    }

    /*
     * CallbackHandler Method
     */

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        if (callbacks.length == 1 && callbacks[0] instanceof AuthorizeCallback) {
            AuthorizeCallback acb = (AuthorizeCallback) callbacks[0];
            boolean authorized = acb.getAuthenticationID().equals(acb.getAuthorizationID());
            if (authorized == false) {
                SECURITY_LOGGER.tracef(
                        "Checking 'AuthorizeCallback', authorized=false, authenticationID=%s, authorizationID=%s.",
                        acb.getAuthenticationID(), acb.getAuthorizationID());
            }
            acb.setAuthorized(authorized);

            return;
        }

        NameCallback nameCallBack = null;
        EvidenceVerifyCallback evidenceVerifyCallback = null;
        SubjectCallback subjectCallback = null;

        for (Callback current : callbacks) {
            if (current instanceof NameCallback) {
                nameCallBack = (NameCallback) current;
            } else if (current instanceof RealmCallback) {
            } else if (current instanceof EvidenceVerifyCallback) {
                evidenceVerifyCallback = (EvidenceVerifyCallback) current;
            } else if (current instanceof SubjectCallback) {
                subjectCallback = (SubjectCallback) current;
            } else {
                throw new UnsupportedCallbackException(current);
            }
        }

        if (nameCallBack == null) {
            SECURITY_LOGGER.trace("No username supplied in Callbacks.");
            throw DomainManagementLogger.ROOT_LOGGER.noUsername();
        }
        final String userName = nameCallBack.getDefaultName();
        if (userName == null || userName.length() == 0) {
            SECURITY_LOGGER.trace("NameCallback either has no username or is 0 length.");
            throw DomainManagementLogger.ROOT_LOGGER.noUsername();
        }
        if (evidenceVerifyCallback == null || evidenceVerifyCallback.getEvidence() == null) {
            SECURITY_LOGGER.trace("No password to verify.");
            throw DomainManagementLogger.ROOT_LOGGER.noPassword();
        }

        final char[] password;

        if (evidenceVerifyCallback.getEvidence() instanceof PasswordGuessEvidence) {
            password = ((PasswordGuessEvidence) evidenceVerifyCallback.getEvidence()).getGuess();
        } else {
            SECURITY_LOGGER.trace("No password to verify.");
            throw DomainManagementLogger.ROOT_LOGGER.noPassword();
        }

        Subject subject = subjectCallback != null && subjectCallback.getSubject() != null ? subjectCallback.getSubject()
                : new Subject();
        ServerSecurityManager securityManager;
        if ((securityManager = securityManagerValue.getOptionalValue()) != null) {
            try {
                securityManager.push(name, userName, password, subject);
                securityManager.authenticate();
                evidenceVerifyCallback.setVerified(true);
                subject = securityManager.getSubject();
                subject.getPrivateCredentials().add(new PasswordCredential(userName, password));
                if (assignGroups) {
                    Set<Principal> prinicpals = subject.getPrincipals();
                    Set<SimpleGroup> groups = subject.getPrincipals(SimpleGroup.class);
                    for (SimpleGroup current : groups) {
                        if ("Roles".equals(current.getName())) {
                            Enumeration<Principal> members = current.members();
                            while (members.hasMoreElements()) {
                                prinicpals.add(new RealmGroup(realm, members.nextElement().getName()));
                            }
                        }
                    }
                }
                if (subjectCallback != null) {
                    // Only want to deliberately pass it back if authentication completed.
                    subjectCallback.setSubject(subject);
                }
            } catch (SecurityException e) {
                SECURITY_LOGGER.debug("Failed to verify password in JAAS callbackhandler " + this.name, e);
                evidenceVerifyCallback.setVerified(false);
            } finally {
                securityManager.pop();
            }

        } else {
            try {
                LoginContext ctx = new LoginContext(name, subject, callbacks1 -> {
                    for (Callback current : callbacks1) {
                        if (current instanceof NameCallback) {
                            NameCallback ncb = (NameCallback) current;
                            ncb.setName(userName);
                        } else if (current instanceof PasswordCallback) {
                            PasswordCallback pcb = (PasswordCallback) current;
                            pcb.setPassword(password);
                        } else {
                            throw new UnsupportedCallbackException(current);
                        }
                    }
                });
                ctx.login();
                evidenceVerifyCallback.setVerified(true);
                subject.getPrivateCredentials().add(new PasswordCredential(userName, password));
                if (assignGroups) {
                    Set<Principal> prinicpals = subject.getPrincipals();
                    Set<SimpleGroup> groups = subject.getPrincipals(SimpleGroup.class);
                    for (SimpleGroup current : groups) {
                        if ("Roles".equals(current.getName())) {
                            Enumeration<Principal> members = current.members();
                            while (members.hasMoreElements()) {
                                prinicpals.add(new RealmGroup(realm, members.nextElement().getName()));
                            }
                        }
                    }
                }
                if (subjectCallback != null) {
                    // Only want to deliberately pass it back if authentication completed.
                    subjectCallback.setSubject(subject);
                }
            } catch (LoginException e) {
                SECURITY_LOGGER.debug("Login failed in JAAS callbackhandler " + this.name, e);
                evidenceVerifyCallback.setVerified(false);
            }
        }
    }

    /*
     * Service Methods
     */

    public void start(final StartContext context) throws StartException {
    }

    public void stop(final StopContext context) {
    }

    public InjectedValue<ServerSecurityManager> getSecurityManagerValue() {
        return securityManagerValue;
    }

    public CallbackHandlerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public static final class ServiceUtil {

        private ServiceUtil() {
        }

        public static ServiceName createServiceName(final String realmName) {
            return SecurityRealm.ServiceUtil.createServiceName(realmName).append(SERVICE_SUFFIX);
        }
    }

}

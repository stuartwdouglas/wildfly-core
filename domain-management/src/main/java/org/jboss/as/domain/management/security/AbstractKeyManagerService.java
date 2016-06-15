/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service to handle the creation of the KeyManager[].
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class AbstractKeyManagerService implements Service<AbstractKeyManagerService> {

    private volatile char[] keystorePassword;
    private volatile char[] keyPassword;


    AbstractKeyManagerService(final char[] keystorePassword, final char[] keyPassword) {
        this.keystorePassword = keystorePassword;
        this.keyPassword = keyPassword;
    }

    public char[] getKeystorePassword() {
        return keystorePassword;
    }


    public void setKeystorePassword(char[] keystorePassword) {
        this.keystorePassword = keystorePassword;
    }


    public char[] getKeyPassword() {
        return keyPassword;
    }


    public void setKeyPassword(char[] keyPassword) {
        this.keyPassword = keyPassword;
    }

    /*
     * Service Lifecycle Methods
     */

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            createKeyManagers(true);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            throw DomainManagementLogger.ROOT_LOGGER.unableToStart(e);
        }
    }

    @Override
    public void stop(final StopContext context) {
    }

    /*
     * Value Method
     */

    @Override
    public AbstractKeyManagerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public KeyManager[] getKeyManagers() {
        try {
            return createKeyManagers(false);
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract boolean isLazy();

    /**
     * Method to create the KeyManager[]
     *
     * This method returns the created KeyManager[] so that sub classes can have the opportunity to either wrap or replace this
     * call.
     *
     * @param startup If true the keymanagers are being created on startup. If they key manager creation is lazy then it is ok to return null
     * @return The KeyManager[] based on the supplied {@link KeyStore}
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     * @throws UnrecoverableKeyException
     */
    protected KeyManager[] createKeyManagers(boolean startup) throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        KeyStore keyStore = loadKeyStore(startup);
        if(keyStore == null && startup) {
            return null;
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyPassword == null ? keystorePassword : keyPassword);

        return keyManagerFactory.getKeyManagers();
    }

    protected abstract KeyStore loadKeyStore(boolean startup);

    static final class ServiceUtil {

        private static final String SERVICE_SUFFIX = "key-manager";

        public static ServiceName createServiceName(final ServiceName parentService) {
            return parentService.append(SERVICE_SUFFIX);
        }

        public static ServiceBuilder<?> addDependency(final ServiceBuilder<?> sb, final Injector<AbstractKeyManagerService> injector, final ServiceName parentService) {
            sb.addDependency(createServiceName(parentService), AbstractKeyManagerService.class, injector);

            return sb;
        }

    }

}

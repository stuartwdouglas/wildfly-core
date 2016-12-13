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

package org.wildfly.test.undertow;

import io.undertow.server.HttpHandler;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;

/**
 * A default service activator which will create an Undertow service and send a default response to 0.0.0.0 port
 * 8080.
 * <p/>
 * You can override the address with the {@code jboss.bind.address} or {@code management.address} system properties.
 * <p/>
 * To override  the port use the {@code jboss.http.port} system property.
 * <p/>
 * Override any of the getter methods to customize the desired behavior.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class UndertowServiceActivator implements ServiceActivator {

    /**
     * Class dependencies required to use the {@link org.wildfly.test.undertow.UndertowService}.
     */
    public static final Class<?>[] DEPENDENCIES = {
            UndertowService.class,
            UndertowServiceActivator.class,
            TestSuiteEnvironment.class
    };

    public static final String DEFAULT_RESPONSE = "Response sent";
    private static final HttpHandler DEFAULT_HANDLER = exchange -> exchange.getResponseSender().send(DEFAULT_RESPONSE);

    @Override
    public final void activate(final ServiceActivatorContext serviceActivatorContext) throws ServiceRegistryException {
        final String address = getAddress();
        assert address != null : "address cannot be null";
        final int port = getPort();
        assert port > 0 : "port must be greater than 0";
        final HttpHandler handler = getHttpHandler();
        assert handler != null : "A handler is required";
        final UndertowService service = new UndertowService(address, port, handler);
        serviceActivatorContext.getServiceTarget().addService(getServiceName(), service).install();
    }

    /**
     * Returns the service name to use when adding the UndertowService.
     *
     * @return the undertow service name
     */
    protected ServiceName getServiceName() {
        return UndertowService.DEFAULT_SERVICE_NAME;
    }

    /**
     * Returns the {@link io.undertow.server.HttpHandler handler} used to process the request
     *
     * @return the handler to use
     */
    protected HttpHandler getHttpHandler() {
        return DEFAULT_HANDLER;
    }

    /**
     * Returns the address for Undertow to bind to.
     *
     * @return the address
     */
    protected String getAddress() {
        return TestSuiteEnvironment.getHttpAddress();
    }

    /**
     * Returns the port for Undertow to bind to.
     *
     * @return the port
     */
    protected int getPort() {
        return TestSuiteEnvironment.getHttpPort();
    }
}

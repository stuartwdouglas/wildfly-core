/*
 * JBoss, Home of Professional Open Source
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.server.classchange;

import java.lang.instrument.ClassDefinition;

/**
 * Support for deployment class change events. Subsystems can register listeners that will
 * be notified when a class has been modified.
 */
public interface DeploymentClassChangeSupport {

    /**
     * Adds a listener that can react to change change events
     *
     * @param listener The listener
     */
    void addListener(ClassChangeListener listener);

    /**
     * Notifies the container that some classes have been changed and/or added
     *
     * @param changedClasses The changed classes
     * @param newClasses The added classes
     */
    void notifyChangedClasses(ClassDefinition[] changedClasses, NewClass[] newClasses);

    /**
     * Asks the container to scan for changes to an exploded deployment
     *
     */
    void scanForChangedClasses();

    class NewClass {

        private final String name;
        private final byte[] data;
        private final ClassLoader classLoader;

        public NewClass(String name, byte[] data, ClassLoader classLoader) {
            this.name = name;
            this.data = data;
            this.classLoader = classLoader;
        }

        public String getName() {
            return name;
        }

        public byte[] getData() {
            return data;
        }

        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}

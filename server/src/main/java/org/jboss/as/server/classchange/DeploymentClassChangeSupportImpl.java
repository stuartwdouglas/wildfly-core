/*
 * JBoss, Home of Professional Open Source
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.fakereplace.api.ChangedClass;
import org.fakereplace.api.ClassChangeAware;
import org.fakereplace.api.NewClassData;
import org.fakereplace.core.Fakereplace;
import org.fakereplace.replacement.AddedClass;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Indexer;
import org.jboss.modules.Module;

class DeploymentClassChangeSupportImpl implements DeploymentClassChangeSupport, ClassChangeAware {

    private final DeploymentUnit deploymentUnit;

    private final Set<ClassLoader> classLoaders = new CopyOnWriteArraySet<>();
    private final List<ClassChangeListener> listeners = new CopyOnWriteArrayList<>();

    public void addClassLoader(ClassLoader classLoader) {
        classLoaders.add(classLoader);
    }


    DeploymentClassChangeSupportImpl(DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
    }

    @Override
    public void addListener(ClassChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void notifyChangedClasses(ClassDefinition[] changedClasses, NewClass[] newClasses) {
        Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        AddedClass[] added = new AddedClass[newClasses.length];
        for (int i = 0; i < added.length; ++i) {
            added[i] = new AddedClass(newClasses[i].getName(), newClasses[i].getData(), module.getClassLoader());
        }
        Fakereplace.redefine(changedClasses, added);
    }

    @Override
    public void afterChange(List<ChangedClass> changedClasses, List<NewClassData> newClasses) {
        List<Class<?>> filteredChanged = new ArrayList<>();
        List<ClassChangeListener.NewClassDefinition> filteredAdded = new ArrayList<>();
        for (ChangedClass changed : changedClasses) {
            if (classLoaders.contains(changed.getChangedClass().getClassLoader())) {
                filteredChanged.add(changed.getChangedClass());
            }
        }
        Indexer i = new Indexer();
        for (NewClassData added : newClasses) {
            if (classLoaders.contains(added.getClassLoader())) {
                try {
                    ClassInfo classInfo = i.index(new DataInputStream(new ByteArrayInputStream(added.getData())));
                    filteredAdded.add(new ClassChangeListener.NewClassDefinition(added.getClassName(), added.getClassLoader(), added.getData(), classInfo));
                } catch (IOException e) {
                    //should not happen
                    ServerLogger.AS_ROOT_LOGGER.cannotIndexClass(added.getClassName(), added.getClassLoader().toString(), e);
                }
            }
        }
        for (ClassChangeListener listener : listeners) {
            listener.classesReplaced(Collections.unmodifiableList(filteredChanged), Collections.unmodifiableList(filteredAdded));
        }
    }
}

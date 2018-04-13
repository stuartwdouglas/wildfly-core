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
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.jboss.vfs.VirtualFile;

class DeploymentClassChangeSupportImpl implements DeploymentClassChangeSupport, ClassChangeAware {

    private final DeploymentUnit deploymentUnit;
    private final Set<ClassLoader> classLoaders = new CopyOnWriteArraySet<>();
    private final List<ClassChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<VirtualFile, ClassLoader> classLoaderRoots = new ConcurrentHashMap<>();

    private final Map<String, Long> classModificationTimes = new ConcurrentHashMap<>();
    private final VirtualFile deploymentRoot;


    public void addClassLoader(ClassLoader classLoader, Set<VirtualFile> roots) {
        classLoaders.add(classLoader);
        for(VirtualFile root : roots) {
            classLoaderRoots.put(root, classLoader);
        }
    }


    DeploymentClassChangeSupportImpl(DeploymentUnit deploymentUnit) {
        this.deploymentUnit = deploymentUnit;
        deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
    }

    public void doInitialScan()  {
        try {
            VirtualFile root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
            List<VirtualFile> files = root.getChildrenRecursively();
            for (VirtualFile file : files) {
                if (file.getName().endsWith(".class")) {
                    classModificationTimes.put(file.getPathName(), file.getLastModified());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        //we need to update these files on the file system
        //otherwise the changes will not be persistent across restarts

        for(ClassDefinition i : changedClasses) {
            String name = i.getDefinitionClass().getName().replace(".", "/") + ".class";
            for(Map.Entry<VirtualFile, ClassLoader> entry : classLoaderRoots.entrySet()) {
                try {
                    VirtualFile file = entry.getKey().getChild(name);
                    if (file.exists()) {
                        File pf = file.getPhysicalFile();
                        try (FileOutputStream out = new FileOutputStream(pf)) {
                            out.write(i.getDefinitionClassFile());
                        }
                    }
                } catch (IOException e) {
                    ServerLogger.AS_ROOT_LOGGER.failedToReplaceClassFile(i.getDefinitionClass().getName(), e);
                }

            }
        }
        Fakereplace.redefine(changedClasses, added);
    }

    @Override
    public void scanForChangedClasses() {

        try {
            VirtualFile root = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT).getRoot();
            List<VirtualFile> files = root.getChildrenRecursively();
            List<VirtualFile> newFiles = new ArrayList<>();
            List<VirtualFile> modFiles = new ArrayList<>();
            //scan for all modified and new classes
            for (VirtualFile file : files) {
                if (file.getName().endsWith(".class")) {
                    Long modTime = classModificationTimes.get(file.getPathName());
                    if(modTime == null) {
                        newFiles.add(file);
                        classModificationTimes.put(file.getPathName(), file.getLastModified());
                    } else if(modTime != file.getLastModified()) {
                        modFiles.add(file);
                        classModificationTimes.put(file.getPathName(), file.getLastModified());
                    }
                }
            }
            if(modFiles.isEmpty() && newFiles.isEmpty()) {
                //if nothing has changed we just return
                return;
            }
            ClassDefinition[] modifiedClasses = new ClassDefinition[modFiles.size()];
            int count = 0;
            for(VirtualFile i : modFiles) {
                byte[] data = readFile(i);
                VirtualFile closest = deploymentRoot;
                int closeCount = 0;
                for(Map.Entry<VirtualFile, ClassLoader> entry : classLoaderRoots.entrySet()) {
                    String pathName = entry.getKey().getPathName();
                    if(i.getPathName().startsWith(pathName)) {
                        if(closeCount < pathName.length()) {
                            closeCount = pathName.length();
                            closest = entry.getKey();
                        }
                    }
                }
                ClassLoader loader = classLoaderRoots.get(closest);
                //we infer the class name from the file name
                //then try and load it to get the class definition
                String className = i.getPathName().substring(closeCount + 1, i.getPathName().length() - ".class".length()).replace("/", ".");
                Class<?> classDefinition = loader.loadClass(className);
                modifiedClasses[count++] = new ClassDefinition(classDefinition, data);
            }

            AddedClass[] added = new AddedClass[newFiles.size()];
            count = 0;
            for(VirtualFile i : newFiles) {
                byte[] data = readFile(i);
                VirtualFile closest = deploymentRoot;
                int closeCount = 0;
                for(Map.Entry<VirtualFile, ClassLoader> entry : classLoaderRoots.entrySet()) {
                    String pathName = entry.getKey().getPathName();
                    if(i.getPathName().startsWith(pathName)) {
                        if(closeCount < pathName.length()) {
                            closeCount = pathName.length();
                            closest = entry.getKey();
                        }
                    }
                }
                ClassLoader loader = classLoaderRoots.get(closest);
                //we infer the class name from the file name
                //then try and load it to get the class definition
                String className = i.getPathName().substring(closeCount, i.getPathName().length() - ".class".length()).replace("/", ".");
                added[count++] = new AddedClass(className, data, loader);
            }
            Fakereplace.redefine(modifiedClasses, added);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readFile(VirtualFile i) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = i.openStream()){
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        }
        return out.toByteArray();
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

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

package org.jboss.as.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * @author Stuart Douglas
 */
public class HackClassLoaderService implements Service<Void> {

    final InjectedValue<ExecutorService> executor = new InjectedValue<>();

    @Override
    public void start(StartContext startContext) throws StartException {
        List<Entry> entries1 = new ArrayList<>();
        List<Entry> entries2 = new ArrayList<>();
        try (BufferedReader resource = new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream("class-loading-hack.properties")))) {
            String s;
            int i = 0;
            while ((s = resource.readLine()) != null) {
                String[] parts = s.split("=");
                if(i % 2 == 1) {
                    entries1.add(new Entry(parts[0], parts[1]));
                } else {
                    entries2.add(new Entry(parts[0], parts[1]));
                }
            }
            executor.getValue().execute(new MyRunnable(entries1));
            executor.getValue().execute(new MyRunnable(entries2));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static class Entry {
        final String className, moduleName;

        private Entry(String className, String moduleName) {
            this.className = className;
            this.moduleName = moduleName;
        }
    }

    @Override
    public void stop(StopContext stopContext) {

    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    private static class MyRunnable implements Runnable {

        final List<Entry> entries;

        private MyRunnable(List<Entry> entries) {
            this.entries = entries;
        }

        @Override
        public void run() {

            for (Entry e : entries) {
                final String className = e.className;
                if (className.contains("$logger")) {
                    continue;
                }
                try {
                    Module module = Module.getBootModuleLoader().loadModule(e.moduleName);
                    module.getClassLoader().loadClass(className);
                } catch (ModuleLoadException e1) {
                    e1.printStackTrace();
                } catch (ClassNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }
}

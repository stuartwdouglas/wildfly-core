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
import java.util.List;

import org.fakereplace.api.ChangedClass;
import org.fakereplace.api.ClassChangeAware;
import org.fakereplace.api.NewClassData;
import org.fakereplace.core.ClassChangeNotifier;
import org.fakereplace.core.Fakereplace;
import org.fakereplace.replacement.AddedClass;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.ExplodedDeploymentMarker;
import org.jboss.modules.Module;

public class ClassChangeDeploymentUnitProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if(deploymentUnit.getParent() != null) {
            DeploymentClassChangeSupport classChangeSupport = deploymentUnit.getParent().getAttachment(Attachments.DEPLOYMENT_CLASS_CHANGE_SUPPORT);
            if(classChangeSupport != null) {
                deploymentUnit.putAttachment(Attachments.DEPLOYMENT_CLASS_CHANGE_SUPPORT, classChangeSupport);
            }
        } else {
            if (ExplodedDeploymentMarker.isExplodedDeployment(deploymentUnit)) {
                deploymentUnit.putAttachment(Attachments.DEPLOYMENT_CLASS_CHANGE_SUPPORT, new DeploymentClassChangeSupportImpl(deploymentUnit));
            }
        }

    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    private static class DeploymentClassChangeSupportImpl implements DeploymentClassChangeSupport {

        private final DeploymentUnit deploymentUnit;

        private DeploymentClassChangeSupportImpl(DeploymentUnit deploymentUnit) {
            this.deploymentUnit = deploymentUnit;
        }

        @Override
        public void addListener(ClassChangeListener listener) {
            ClassChangeNotifier.instance().add(new ClassChangeAware() {
                @Override
                public void afterChange(List<ChangedClass> list, List<NewClassData> list1) {

                }
            });
        }

        @Override
        public void notifyChangedClasses(ClassDefinition[] changedClasses, NewClass[] newClasses) {
            Module module = deploymentUnit.getAttachment(Attachments.MODULE);
            AddedClass[] added = new AddedClass[newClasses.length];
            for(int i = 0; i < added.length; ++i) {
                added[i] = new AddedClass(newClasses[i].getName(), newClasses[i].getData(), module.getClassLoader());
            }
            Fakereplace.redefine(changedClasses, added);
        }
    }
}

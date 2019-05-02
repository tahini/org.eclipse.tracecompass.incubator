/*******************************************************************************
 * Copyright (c) 2019 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.scripting.ui.project.handlers;

import java.util.Objects;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.TmfProjectNature;

/**
 * Checks if a file is within a project with the tracing nature
 *
 * @author Geneviève Bastien
 */
public class InTracingProjectTester extends PropertyTester {

    @Override
    public boolean test(@Nullable Object receiver, @Nullable String property, Object @Nullable [] args, @Nullable Object expectedValue) {
        if (receiver instanceof IFile) {
            IFile file = (IFile) receiver;
            IContainer parent = file.getParent();
            while (parent != null && !(parent instanceof IProject)) {
                parent = parent.getParent();
            }
            if (parent instanceof IProject) {
                IProject project = (IProject) parent;
                try {
                    String[] natureIds = project.getDescription().getNatureIds();
                    for (int i = 0; i < natureIds.length; i++) {
                        if (Objects.equals(natureIds[i], TmfProjectNature.ID)) {
                            return true;
                        }
                    }
                } catch (CoreException e) {
                    // Ignore
                }
            }
        }
        return false;
    }
}

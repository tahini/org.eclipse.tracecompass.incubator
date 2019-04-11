/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.rcp.ui.application;

import java.io.File;
import java.net.URL;
import java.text.MessageFormat;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.incubator.internal.rcp.ui.Activator;
import org.eclipse.tracecompass.incubator.internal.tracing.rcp.ui.messages.Messages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

/**
 * Eclipse application entry
 *
 * @author Bernd Hufmann
 */
public class Application implements IApplication {

    private Location fInstanceLoc = null;
    private static final String WORKSPACE_NAME = ".tracecompass-webapp"; //$NON-NLS-1$

    @Override
    public Object start(IApplicationContext context) throws Exception {
        Display display = PlatformUI.createDisplay();
        try {
            // fetch the Location that we will be modifying
            fInstanceLoc = Platform.getInstanceLocation();

            // -data @noDefault in <applName>.ini allows us to set the workspace here.
            // If the user wants to change the location then he has to change
            // @noDefault to a specific location or remove -data @noDefault for
            // default location
            if (!fInstanceLoc.allowsDefault() && !fInstanceLoc.isSet()) {
                File workspaceRoot = new File(WORKSPACE_NAME);

                if (!workspaceRoot.exists()) {
                    MessageDialog.openError(display.getActiveShell(),
                            Messages.Application_WorkspaceCreationError,
                            MessageFormat.format(Messages.Application_WorkspaceRootNotExistError, new Object[] { Activator.getWorkspaceRoot() }));
                    return IApplication.EXIT_OK;
                }

                if (!workspaceRoot.canWrite()) {
                    MessageDialog.openError(display.getActiveShell(),
                            Messages.Application_WorkspaceCreationError,
                            MessageFormat.format(Messages.Application_WorkspaceRootPermissionError, new Object[] { Activator.getWorkspaceRoot() }));
                    return IApplication.EXIT_OK;
                }

                String workspace = Activator.getWorkspaceRoot() + File.separator + Activator.WORKSPACE_NAME;
                // set location to workspace
                fInstanceLoc.set(new URL("file", null, workspace), false); //$NON-NLS-1$
            }

            if (!fInstanceLoc.lock()) {
                MessageDialog.openError(display.getActiveShell(),
                        Messages.Application_WorkspaceCreationError,
                        MessageFormat.format(Messages.Application_WorkspaceInUseError, new Object[] { fInstanceLoc.getURL().getPath() }));
                return IApplication.EXIT_OK;
            }

            int returnCode = PlatformUI.createAndRunWorkbench(display, new ApplicationWorkbenchAdvisor());
            if (returnCode == PlatformUI.RETURN_RESTART) {
                return IApplication.EXIT_RESTART;
            }
            return IApplication.EXIT_OK;
        } finally {
            display.dispose();
        }
    }

    @Override
    public void stop() {
        if (!PlatformUI.isWorkbenchRunning()) {
            return;
        }
        final IWorkbench workbench = PlatformUI.getWorkbench();
        final Display display = workbench.getDisplay();
        fInstanceLoc.release();
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                if (!display.isDisposed()) {
                    workbench.close();
                }
            }
        });
    }
}
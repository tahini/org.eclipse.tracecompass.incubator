/*******************************************************************************
 * Copyright (c) 2011, 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Francois Chouinard - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * <b><u>RenameExperimentHandler</u></b>
 * <p>
 */
public class ExecuteScriptHandler extends AbstractHandler {

    // ------------------------------------------------------------------------
    // isEnabled
    // ------------------------------------------------------------------------

    @Override
    public boolean isEnabled() {

      return true;
    }

    // ------------------------------------------------------------------------
    // Execution
    // ------------------------------------------------------------------------

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        // Check if we are closing down
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return false;
        }

        // Get the selection
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        IWorkbenchPart part = page.getActivePart();
        if (part == null) {
            return false;
        }
        ISelectionProvider selectionProvider = part.getSite().getSelectionProvider();
        if (selectionProvider == null) {
            return false;
        }
        ISelection selection = selectionProvider.getSelection();

        // Make sure there is only selection and that it is an experiment
        if (selection instanceof TreeSelection) {
            TreeSelection sel = (TreeSelection) selection;
            Object element = sel.getFirstElement();
            if (element instanceof IFile) {
//                final Script script = new Script(element);
            }
            System.out.println(element);
        }

//        // Check if we are closing down
//        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
//        if (window == null) {
//            return null;
//        }
//
//        // Fire the Rename Experiment dialog
//        Shell shell = window.getShell();
//        SymbolProviderConfigDialog dialog = new SymbolProviderConfigDialog(shell, getProviderPages());
//        if (dialog.open() == IDialogConstants.OK_ID) {
//            // fPresentationProvider.resetFunctionNames();
//            // refresh();
//        }

        return null;
    }

}

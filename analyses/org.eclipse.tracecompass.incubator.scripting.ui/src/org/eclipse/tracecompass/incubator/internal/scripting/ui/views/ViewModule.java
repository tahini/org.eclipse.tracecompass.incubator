/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.ui.views;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.views.timegraph.dataprovider.DataProviderBaseView;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 *
 *
 * @author Geneviève Bastien
 */
public class ViewModule {

    /** Module identifier. */
    public static final String MODULE_ID = "/TraceCompass/Views"; //$NON-NLS-1$

    @WrapToScript
    public void openTimeGraphView(ITimeGraphDataProvider<TimeGraphEntryModel> dataProvider) {

        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    IViewPart view = openView(dataProvider.getId());
                    if (view == null) {
                        return;
                    }
                } catch (final PartInitException e) {
                    // Do nothing
                }
            }
        });
    }

    @SuppressWarnings("restriction")
    private static @Nullable IViewPart openView(String name) throws PartInitException {
        final IWorkbench wb = PlatformUI.getWorkbench();
        final IWorkbenchPage activePage = wb.getActiveWorkbenchWindow().getActivePage();

        return activePage.showView(DataProviderBaseView.BASE_ID, name.replace(":", DataProviderBaseView.COLON), IWorkbenchPage.VIEW_ACTIVATE); //$NON-NLS-1$
    }
}

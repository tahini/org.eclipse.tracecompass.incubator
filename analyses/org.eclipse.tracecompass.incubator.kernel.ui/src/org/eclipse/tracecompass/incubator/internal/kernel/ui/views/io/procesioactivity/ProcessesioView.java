/**********************************************************************
 * Copyright (c) 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Matthew Khouzam - Initial API and implementation
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.procesioactivity;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class ProcessesioView extends TmfView {

    /** ID string */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.processioactivity"; //$NON-NLS-1$
    private ProcessesioViewer graph = null;
    private IOstatistics stat = null;
    /**
     * Constructor
     */
    public ProcessesioView() {
        super(Messages.ProcessesioView_Title);
    }

    @Override
    public void createPartControl(Composite parent) {
        final SashForm sash = new SashForm(parent, SWT.NONE);
        stat = new IOstatistics(sash);
        graph = new ProcessesioViewer(sash);
        stat.addSelectionChangeListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                ISelection selection = event.getSelection();
                if (selection instanceof IStructuredSelection) {
                    Object structSelection = ((IStructuredSelection) selection).getFirstElement();
                    if (structSelection instanceof IOstatisticsEntry) {
                        IOstatisticsEntry entry = (IOstatisticsEntry) structSelection;
                        graph.setSelectedThread(Long.valueOf(entry.getThreadTid()));
                    }
                }
            }
        });
        sash.setLayout(new FillLayout());
        int weights[] = {4,7};
        sash.setWeights(weights);
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace != null) {
            TmfTraceSelectedSignal signal = new TmfTraceSelectedSignal(this, trace);
            graph.traceSelected(signal);
            stat.traceSelected(signal);
        }
    }

    @Override
    public void setFocus() {
    }

    @Override
    public void dispose() {
        super.dispose();
        if (graph != null) {
            graph.dispose();
        }
        if (stat != null) {
            stat.dispose();
        }
    }
}

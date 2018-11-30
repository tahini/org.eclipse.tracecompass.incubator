/*******************************************************************************
 * Copyright (c) 2015, 2017 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.views.weightedtree;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.internal.callstack.ui.views.weightedtree.WeightedTreeViewer.ElementEntry;
import org.eclipse.tracecompass.internal.tmf.ui.viewers.piecharts.TmfPieChartViewer;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractTmfTreeViewer;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * Base view that shows a weighted tree
 *
 * @author Geneviève Bastien
 */
public class WeightedTreeView extends TmfView {

    /**
     * The ID of this view
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.callstack.ui.views.weightedtree"; //$NON-NLS-1$

    private @Nullable AbstractTmfTreeViewer fWeightedTreeViewer = null;
    private @Nullable WeightedTreePieChartViewer fPieChartViewer = null;

    /**
     * Constructor
     */
    public WeightedTreeView() {
        super("WeightedTreeView"); //$NON-NLS-1$
    }

    @Override
    public void createPartControl(@Nullable Composite parent) {
        super.createPartControl(parent);
        String analysisId = NonNullUtils.nullToEmptyString(getViewSite().getSecondaryId());
        // Build the tree viewer
        AbstractTmfTreeViewer weightedTreeViewer = new WeightedTreeViewer(parent, analysisId, this);
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace != null) {
            weightedTreeViewer.loadTrace(trace);
        }
        fWeightedTreeViewer = weightedTreeViewer;

        // Build the pie chart viewer
        WeightedTreePieChartViewer pieChartViewer = new WeightedTreePieChartViewer(parent, analysisId);
        fPieChartViewer = pieChartViewer;
    }

    @Override
    public void setFocus() {
        AbstractTmfTreeViewer treeViewer = fWeightedTreeViewer;
        if (treeViewer != null) {
            treeViewer.getControl().setFocus();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        AbstractTmfTreeViewer treeViewer = fWeightedTreeViewer;
        if (treeViewer != null) {
            treeViewer.dispose();
        }
    }

    /**
     * Dispatches the selection to the viewers
     *
     * @param selection The selected element
     */
    public <E> void elementSelected(E selection) {
        WeightedTreePieChartViewer pieChartViewer = fPieChartViewer;
        if (pieChartViewer != null) {
            pieChartViewer.elementSelected(selection);
        }
    }

}

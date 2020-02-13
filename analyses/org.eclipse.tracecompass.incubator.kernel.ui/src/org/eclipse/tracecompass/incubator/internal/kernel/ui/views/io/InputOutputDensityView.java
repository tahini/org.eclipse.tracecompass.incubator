/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density.AbstractSegmentStoreDensityView;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density.AbstractSegmentStoreDensityViewer;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.table.AbstractSegmentStoreTableViewer;
import org.eclipse.tracecompass.common.core.NonNullUtils;

public class InputOutputDensityView extends AbstractSegmentStoreDensityView {

    /** The view's ID */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.io.density"; //$NON-NLS-1$

    /**
     * Constructs a new density view.
     */
    public InputOutputDensityView() {
        super(ID);
    }


    @Override
    protected @NonNull AbstractSegmentStoreTableViewer createSegmentStoreTableViewer(@NonNull Composite parent) {
        return new InputOutputTableViewer(new TableViewer(parent, SWT.FULL_SELECTION | SWT.VIRTUAL));
    }

    @Override
    protected @NonNull AbstractSegmentStoreDensityViewer createSegmentStoreDensityViewer(@NonNull Composite parent) {
        return new InputOutputDensityViewer(NonNullUtils.checkNotNull(parent));
    }

}

/**********************************************************************
 * Copyright (c) 2020 Draeger, Auriga
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.perprocess;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.core.model.OsStrings;
import org.eclipse.tracecompass.incubator.internal.kernel.core.io.IoAccessDataProvider;
import org.eclipse.tracecompass.incubator.internal.kernel.core.io.IoPerProcessDataProvider;
import org.eclipse.tracecompass.incubator.internal.tmf.ui.multiview.ui.view.AbstractMultiView;
import org.eclipse.tracecompass.incubator.internal.tmf.ui.multiview.ui.view.timegraph.BaseDataProviderTimeGraphMultiViewer;
import org.eclipse.tracecompass.internal.provisional.tmf.ui.widgets.timegraph.BaseDataProviderTimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.signal.TmfDataModelSelectedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITimeDataProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphColorScheme;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

/**
 * The Multiview.
 *
 * @author Ivan Grinenko
 *
 */
@SuppressWarnings("restriction")
public class IoByProcessView extends AbstractMultiView {
    /**
     * The view's ID.
     */
    public static final String VIEW_ID = "org.eclipse.tracecompass.kernel.ui.view.iobyprocess"; //$NON-NLS-1$

    private @NonNull TimeGraphColorScheme fColorScheme = new TimeGraphColorScheme();


    private @Nullable Integer fSelectedTid = null;

    private @Nullable BaseDataProviderTimeGraphMultiViewer fTgViewer = null;

    /**
     * Constructor.
     */
    public IoByProcessView() {
        super(VIEW_ID);
    }

    /**
     * @param signal
     */
    @TmfSignalHandler
    public void modelSelectedSignal(TmfDataModelSelectedSignal signal) {
        Multimap<String, Object> metadata = signal.getMetadata();
        Collection<Object> collection = metadata.get(OsStrings.tid());
        if (!collection.isEmpty()) {
            // Update the view
            Object tidObj = collection.iterator().next();
            BaseDataProviderTimeGraphMultiViewer tgViewer = fTgViewer;
            Integer selectedTid = fSelectedTid;
            if (tidObj instanceof Integer && !tidObj.equals(selectedTid) && tgViewer != null) {
                fSelectedTid = (Integer) tidObj;
                tgViewer.triggerRebuild();
            }
        }
    }

    @Override
    protected void creatingPartControl() {
        // Add an XY lane:
        addChartViewer( IoPerProcessDataProvider.ID);

        // Add a time graph lane
        Composite composite = new Composite(getSashForm(), SWT.NONE);
        composite.setLayout(new FillLayout());
        composite.setBackground(fColorScheme.getColor(TimeGraphColorScheme.BACKGROUND));
        BaseDataProviderTimeGraphMultiViewer tgViewer = new BaseDataProviderTimeGraphMultiViewer(
                composite, new BaseDataProviderTimeGraphPresentationProvider(), getViewSite(), IoAccessDataProvider.ID) {

            @Override
            protected @NonNull Map<@NonNull String, @NonNull Object> getFetchTreeParameters() {
                Integer tid = fSelectedTid;
                ITimeDataProvider timeProvider = IoByProcessView.this.getTimeProvider();
                if (tid == null || timeProvider == null) {
                    return Collections.emptyMap();
                }
                return ImmutableMap.of(IoAccessDataProvider.TID_PARAM, tid,
                        DataProviderParameterUtils.REQUESTED_TIME_KEY, ImmutableList.of(timeProvider.getTime0(), timeProvider.getTime1()));
            }

            @Override
            protected Map<String, Object> getFetchRowModelParameters(long start, long end,
                    long resolution, boolean fullSearch, Collection<Long> items) {

                Map<String, Object> parameters = super.getFetchRowModelParameters(start, end, resolution, fullSearch, items);
                Integer tid = fSelectedTid;
                if (tid != null) {
                    parameters.put(IoAccessDataProvider.TID_PARAM, tid);
                }
                return parameters;
            }

        };
        tgViewer.init();
        addLane(tgViewer);
        fTgViewer  = tgViewer;
    }

}

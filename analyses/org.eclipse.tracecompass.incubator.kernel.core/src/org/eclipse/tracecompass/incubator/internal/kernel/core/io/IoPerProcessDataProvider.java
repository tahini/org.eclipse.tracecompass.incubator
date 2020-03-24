/*******************************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.kernel.core.fileaccess.FileAccessAnalysis;
import org.eclipse.tracecompass.internal.tmf.core.model.tree.AbstractTreeDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.TmfCommonXAxisModel;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 *
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class IoPerProcessDataProvider extends AbstractTreeDataProvider<FileAccessAnalysis, TmfTreeDataModel> implements ITmfTreeXYDataProvider<TmfTreeDataModel> {

    /**
     * Provider unique ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.kernel.core.io.per.process"; //$NON-NLS-1$
    private static final AtomicLong sfAtomicId = new AtomicLong();
    private static final String READ_TITLE = "Read";
    private static final String WRITE_TITLE = "Write";

    private final BiMap<Long, Integer> fIDToDisplayQuark = HashBiMap.create();
    private final Map<Integer, String> fQuarkToString = new HashMap<>();

    /**
     * Constructor
     *
     * @param trace
     *            The trace this data provider is for
     * @param analysisModule
     *            The analysis module
     */
    public IoPerProcessDataProvider(ITmfTrace trace, FileAccessAnalysis analysisModule) {
        super(trace, analysisModule);
        analysisModule.schedule();
    }

    /**
     * Create the time graph data provider
     *
     * @param trace
     *            The trace for which is the data provider
     * @return The data provider
     */
    public static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace) {
        FileAccessAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(trace, FileAccessAnalysis.class, FileAccessAnalysis.ID);
        return module != null ? new IoPerProcessDataProvider(trace, module) : null;
    }


    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected boolean isCacheable() {
        return true;
    }

    @Override
    protected TmfTreeModel<TmfTreeDataModel> getTree(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        // Make an entry for each base quark
        List<TmfTreeDataModel> entryList = new ArrayList<>();
        String traceName = Objects.requireNonNull(getTrace().getName());
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        entryList.add(new TmfTreeDataModel(rootId, -1, Collections.singletonList(traceName)));
        for (Integer quark : ss.getQuarks(IoStateProvider.ATTRIBUTE_TID, "*")) { //$NON-NLS-1$
            String tid = ss.getAttributeName(quark);
            Long id = fIDToDisplayQuark.inverse().computeIfAbsent(quark, q -> sfAtomicId.getAndIncrement());
            entryList.add(new TmfTreeDataModel(id, rootId, tid));
            int readQuark = ss.optQuarkRelative(quark, IoStateProvider.ATTRIBUTE_READ);
            if (readQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                Long readId = fIDToDisplayQuark.inverse().computeIfAbsent(readQuark, q -> sfAtomicId.getAndIncrement());
                entryList.add(new TmfTreeDataModel(readId, id, READ_TITLE));
                fQuarkToString.put(readQuark, traceName + '/' + tid + '/' + READ_TITLE);
            }
            int writeQuark = ss.optQuarkRelative(quark, IoStateProvider.ATTRIBUTE_WRITE);
            if (writeQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                Long writeId = fIDToDisplayQuark.inverse().computeIfAbsent(writeQuark, q -> sfAtomicId.getAndIncrement());
                entryList.add(new TmfTreeDataModel(writeId, id, WRITE_TITLE));
                fQuarkToString.put(writeQuark, traceName + '/' + tid + '/' + WRITE_TITLE);
            }
        }
        return new TmfTreeModel<>(Collections.emptyList(), entryList);
    }

    @Override
    public TmfModelResponse<ITmfXyModel> fetchXY(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        ITmfStateSystem ss = getAnalysisModule().getStateSystem();
        if (ss == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
        }

        Map<Integer, double[]> quarkToValues = new HashMap<>();
        // Prepare the quarks to display
        Collection<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
        if (selectedItems == null) {
            // No selected items, take them all
            selectedItems = fIDToDisplayQuark.keySet();
        }
        List<Long> times = getTimes(ss, DataProviderParameterUtils.extractTimeRequested(fetchParameters));
        for (Long id : selectedItems) {
            Integer quark = fIDToDisplayQuark.get(id);
            // Add only quarks that can be displayed, ie, those in the fQuarkToString map
            if (quark != null && fQuarkToString.containsKey(quark)) {
                quarkToValues.put(quark, new double[times.size()]);
            }
        }
        long[] nativeTimes = new long[times.size()];
        for (int i = 0; i < times.size(); i++) {
            nativeTimes[i] = times.get(i);
        }

        // Query the state system to fill the array of values
        try {
            for (ITmfStateInterval interval : ss.query2D(quarkToValues.keySet(), times)) {
                if (monitor != null && monitor.isCanceled()) {
                    return new TmfModelResponse<>(null, Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
                }
                double[] row = quarkToValues.get(interval.getAttribute());
                Object value = interval.getValue();
                if (row != null && (value instanceof Number)) {
                    Double dblValue = ((Number) value).doubleValue();
                    for (int i = 0; i < times.size(); i++) {
                        Long time = times.get(i);
                        if (interval.getStartTime() <= time && interval.getEndTime() >= time) {
                            row[i] = dblValue;
                        }
                    }
                }
            }
        } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException e) {
            return new TmfModelResponse<>(null, Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }
        List<IYModel> models = new ArrayList<>();
        for (Entry<Integer, double[]> values : quarkToValues.entrySet()) {
            models.add(new YModel(fIDToDisplayQuark.inverse().getOrDefault(values.getKey(), -1L), String.valueOf(fQuarkToString.get(values.getKey())), values.getValue()));
        }

        return new TmfModelResponse<>(new TmfCommonXAxisModel("Example XY data provider", nativeTimes, models), Status.COMPLETED, CommonStatusMessage.COMPLETED); //$NON-NLS-1$
    }

    private static List<Long> getTimes(ITmfStateSystem key, @Nullable List<Long> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<@NonNull Long> times = new ArrayList<>();
        for (long t : list) {
            if (key.getStartTime() <= t && t <= key.getCurrentEndTime()) {
                times.add(t);
            }
        }
        Collections.sort(times);
        return times;
    }

}

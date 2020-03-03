/*******************************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.inputoutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.Attributes;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.DiskUtils;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.RequestStateValue;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.internal.tmf.core.model.timegraph.AbstractTimeGraphDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.dataprovider.X11ColorUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.IOutputStyleProvider;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.OutputStyleModel;
import org.eclipse.tracecompass.tmf.core.model.StyleProperties;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

/**
 *
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class DiskRequestDataProvider extends AbstractTimeGraphDataProvider<InputOutputAnalysisModule, TimeGraphEntryModel> implements IOutputStyleProvider {

    /**
     * Extension point ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.kernel.core.inputoutput.DiskRequestDataProvider"; //$NON-NLS-1$

    private static final String WAITING_QUEUE = Objects.requireNonNull(Messages.DiskRequestsDataProvider_WaitingQueue);
    private static final String DRIVER_QUEUE = Objects.requireNonNull(Messages.DiskRequestsDataProvider_DriverQueue);

    /**
     * The state index for the multiple state
     */
    private static final int NUM_COLORS = 25;
    private static final int MAX_SIZE = 500;
    private static final int NB_SIZE_STYLES = 5;
    private static final String SIZE_STYLE_PREFIX = "size"; //$NON-NLS-1$
    private static final String READ_STYLE = "read"; //$NON-NLS-1$
    private static final String WRITE_STYLE = "write"; //$NON-NLS-1$
    private static final String FLUSH_STYLE = "flush"; //$NON-NLS-1$
    private static final String OTHER_STYLE = "other"; //$NON-NLS-1$

    private static final Map<String, OutputElementStyle> STYLES;
    // Map of styles with the parent
    private static final Map<String, OutputElementStyle> STYLE_MAP = Collections.synchronizedMap(new HashMap<>());

    static {
        ImmutableMap.Builder<String, OutputElementStyle> builder = new ImmutableMap.Builder<>();
        String blackColor = X11ColorUtils.toHexColor("black"); //$NON-NLS-1$
        if (blackColor == null) {
            blackColor = X11ColorUtils.toHexColor(0, 0, 0);
        }
        String brownColor = X11ColorUtils.toHexColor("sienna"); //$NON-NLS-1$
        if (brownColor == null) {
            brownColor = X11ColorUtils.toHexColor(160, 82, 45);
        }
        String otherColor = X11ColorUtils.toHexColor("dark green"); //$NON-NLS-1$
        if (otherColor == null) {
            otherColor = X11ColorUtils.toHexColor(0, 100, 0);
        }
        // Put the request types
        builder.put(READ_STYLE, new OutputElementStyle(null, ImmutableMap.of(
                StyleProperties.STYLE_NAME, READ_STYLE,
                StyleProperties.BACKGROUND_COLOR, X11ColorUtils.toHexColor(0, 0, 200),
                StyleProperties.COLOR, blackColor)));
        builder.put(WRITE_STYLE, new OutputElementStyle(null, ImmutableMap.of(
                StyleProperties.STYLE_NAME, WRITE_STYLE,
                StyleProperties.BACKGROUND_COLOR, X11ColorUtils.toHexColor(200, 0, 0),
                StyleProperties.COLOR, blackColor)));
        builder.put(FLUSH_STYLE, new OutputElementStyle(null, ImmutableMap.of(
                StyleProperties.STYLE_NAME, FLUSH_STYLE,
                StyleProperties.BACKGROUND_COLOR, brownColor,
                StyleProperties.HEIGHT, 0.6f)));
        builder.put(OTHER_STYLE, new OutputElementStyle(null, ImmutableMap.of(
                StyleProperties.STYLE_NAME, OTHER_STYLE,
                StyleProperties.BACKGROUND_COLOR, otherColor,
                StyleProperties.HEIGHT, 0.6f)));
        // Put the styles for size of request
        for (int i = 0; i < NB_SIZE_STYLES; i++) {
            builder.put(SIZE_STYLE_PREFIX + i, new OutputElementStyle(null, ImmutableMap.of(
                    StyleProperties.HEIGHT, (float) (i + 1) / NB_SIZE_STYLES)));
        }

        STYLES = builder.build();
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace this data provider is for
     * @param analysisModule
     *            The input output analysis module, source of the data
     */
    public DiskRequestDataProvider(ITmfTrace trace, InputOutputAnalysisModule analysisModule) {
        super(trace, analysisModule);
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
    protected TmfTreeModel<TimeGraphEntryModel> getTree(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        long start = ss.getStartTime();
        long end = ss.getCurrentEndTime();

        List<TimeGraphEntryModel> nodes = new ArrayList<>();
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        nodes.add(new TimeGraphEntryModel(rootId, -1, Objects.requireNonNull(getTrace().getName()), start, end));

        for (Integer diskQuark : ss.getQuarks(Attributes.DISKS, "*")) { //$NON-NLS-1$
            String diskName = DiskUtils.getDiskName(ss, diskQuark);
            long diskId = getId(diskQuark);

            List<TimeGraphEntryModel> driverQueue = getDiskQueue(ss, diskQuark, Attributes.DRIVER_QUEUE, DRIVER_QUEUE, diskId, start, end);
            List<TimeGraphEntryModel> waitingQueue = getDiskQueue(ss, diskQuark, Attributes.WAITING_QUEUE, WAITING_QUEUE, diskId, start, end);
            if (!driverQueue.isEmpty() && !waitingQueue.isEmpty()) {
                nodes.add(new TimeGraphEntryModel(diskId, rootId, diskName, start, end));
                nodes.addAll(driverQueue);
                nodes.addAll(waitingQueue);
            }
        }
        return new TmfTreeModel<>(Collections.emptyList(), nodes);
    }

    private List<TimeGraphEntryModel> getDiskQueue(ITmfStateSystem ss, Integer diskQuark, String queueAttribute, String queueName, long diskId, long start, long end) {
        // Does the queue exist for the disk
        int queueQuark = ss.optQuarkRelative(diskQuark, queueAttribute);
        if (queueQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            Collections.emptyList();
        }

        // Does the queue have requests
        List<Integer> subAttributes = ss.getSubAttributes(queueQuark, false);
        if (subAttributes.isEmpty()) {
            return Collections.emptyList();
        }

        // Add the queue and its sub-entries to the entry list
        List<TimeGraphEntryModel> entries = new ArrayList<>();
        long queueId = getId(queueQuark);
        entries.add(new TimeGraphEntryModel(queueId, diskId, queueName, start, end));
        for (Integer requestQuark : subAttributes) {
            entries.add(new TimeGraphEntryModel(getId(requestQuark), queueId, ss.getAttributeName(requestQuark), start, end));
        }
        return entries;
    }

    @Override
    protected @Nullable TimeGraphModel getRowModel(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        List<Long> ids = DataProviderParameterUtils.extractSelectedItems(parameters);
        if (ids == null) {
            return null;
        }
        Map<Long, Integer> selectedEntries = getSelectedEntries(ids);
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(parameters);
        if (times == null) {
            // No time specified
            return null;
        }
        Map<Integer, Predicate<Multimap<String, Object>>> predicates = new HashMap<>();
        Multimap<Integer, String> regexesMap = DataProviderParameterUtils.extractRegexFilter(parameters);
        if (regexesMap != null) {
            predicates.putAll(computeRegexPredicate(regexesMap));
        }

        Map<Long, List<ITimeGraphState>> rows = new HashMap<>();
        // Order the intervals by start time to make sure states are ordered
//        TreeMultimap<Integer, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
//                Comparator.comparing(ITmfStateInterval::getStartTime));
//        for (ITmfStateInterval interval : ss.query2D(selectedEntries.values(), times)) {
//            if (monitor != null && monitor.isCanceled()) {
//                return null;
//            }
//            intervals.put(interval.getAttribute(), interval);
//        }
        for (ITmfStateInterval interval : ss.query2D(selectedEntries.values(), times)) {
            long startTime = interval.getStartTime();
            long duration = interval.getEndTime() - startTime + 1;
            long key = getId(interval.getAttribute());
            List<ITimeGraphState> eventList = rows.computeIfAbsent(key, id -> new ArrayList<>());
            Object value = interval.getValue();
            if (value == null) {
                ITimeGraphState timeGraphState = new TimeGraphState(startTime, duration, Integer.MIN_VALUE);
                applyFilterAndAddState(eventList, timeGraphState, key, predicates, monitor);
            } else if (value instanceof RequestStateValue) {
                RequestStateValue rsv = (RequestStateValue) value;
                ITimeGraphState timeGraphState = new TimeGraphState(startTime, duration, "0x" + Long.toHexString(rsv.getSector()), getStyleFor(rsv)); //$NON-NLS-1$
                applyFilterAndAddState(eventList, timeGraphState, key, predicates, monitor);
            } else {
                ITimeGraphState timeGraphState = new TimeGraphState(startTime, duration, String.valueOf(value), getStyleFor(String.valueOf(value)));
                applyFilterAndAddState(eventList, timeGraphState, key, predicates, monitor);
            }

        }
        List<ITimeGraphRowModel> models = new ArrayList<>();
        for (Entry<Long, List<ITimeGraphState>> state : rows.entrySet()) {
            Collections.sort(state.getValue(), Comparator.comparing(ITimeGraphState::getStartTime));
            models.add(new TimeGraphRowModel(state.getKey(), state.getValue()));
        }
        return new TimeGraphModel(models);
    }

    private static OutputElementStyle getStyleFor(String callsite) {
        return STYLE_MAP.computeIfAbsent(String.valueOf(Math.floorMod(callsite.hashCode(), NUM_COLORS)), style -> new OutputElementStyle(style));
    }

    private static @Nullable OutputElementStyle getStyleFor(RequestStateValue request) {
        String typeStyle = null;
        String sizeStyle = null;
        switch(request.getRequestType()) {
        case FLUSH:
            typeStyle = FLUSH_STYLE;
            break;
        case OTHER:
            typeStyle = OTHER_STYLE;
            break;
        case READ:
            typeStyle = READ_STYLE;
            sizeStyle = SIZE_STYLE_PREFIX + Math.min(NB_SIZE_STYLES - 1, (int) (((double) request.getNrSector() / MAX_SIZE) * NB_SIZE_STYLES));
            break;
        case WRITE:
            typeStyle = WRITE_STYLE;
            sizeStyle = SIZE_STYLE_PREFIX + Math.min(NB_SIZE_STYLES - 1, (int) (((double) request.getNrSector() / MAX_SIZE) * NB_SIZE_STYLES));
            break;
        default:
            return null;
        }
        String styleKey = sizeStyle == null ? typeStyle : sizeStyle + ',' + typeStyle;
        return STYLE_MAP.computeIfAbsent(styleKey, style -> new OutputElementStyle(style));
    }

    @Deprecated
    @Override
    public TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.timeQueryToMap(filter);
        return fetchArrows(parameters, monitor);
    }

    @Override
    public TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Deprecated
    @Override
    public TmfModelResponse<Map<String, String>> fetchTooltip(SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(filter);
        return fetchTooltip(parameters, monitor);
    }

    @Override
    public TmfModelResponse<Map<String, String>> fetchTooltip(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        // Extract time and ids from parameters
        List<Long> ids = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
        if (ids == null) {
            // No ids specified
            return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }
        Map<Long, Integer> selectedEntries = getSelectedEntries(ids);
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(fetchParameters);
        if (times == null || times.isEmpty()) {
            // No time specified
            return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }


        ITmfStateSystem ss = getAnalysisModule().getStateSystem();
        long start = times.get(0);
        if (ss == null || selectedEntries.size() != 1 || !getAnalysisModule().isQueryable(start)) {
            /*
             * We need the ss to query, we should only be querying one attribute and the
             * query times should be valid.
             */
            return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        Integer quark = selectedEntries.values().iterator().next();

        try {
            Map<String, String> retMap = new LinkedHashMap<>(1);
            ITmfStateInterval interval = ss.querySingleState(start, quark);
            Object value = interval.getValue();
            if (!(value instanceof RequestStateValue)) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
            }
            RequestStateValue request = (RequestStateValue) value;
            retMap.put(Objects.requireNonNull(Messages.DiskRequestDataProvider_Sector), "0x" + Long.toHexString(request.getSector())); //$NON-NLS-1$
            retMap.put(Objects.requireNonNull(Messages.DiskRequestDataProvider_NbSectors), String.valueOf(request.getNrSector()));
            retMap.put(Objects.requireNonNull(Messages.DiskRequestDataProvider_RequestType), String.valueOf(request.getRequestType()));
            return new TmfModelResponse<>(retMap, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        } catch (StateSystemDisposedException e) {
        }

        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public TmfModelResponse<OutputStyleModel> fetchStyle(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(new OutputStyleModel(STYLES), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

}

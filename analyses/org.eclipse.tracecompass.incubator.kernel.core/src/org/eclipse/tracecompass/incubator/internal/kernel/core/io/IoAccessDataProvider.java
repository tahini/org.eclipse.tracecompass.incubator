/*******************************************************************************
 * Copyright (c) 2018, 2020 Ericsson and others
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLogBuilder;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.IODataPalette;
import org.eclipse.tracecompass.internal.tmf.core.model.AbstractStateSystemAnalysisDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.IOutputStyleProvider;
import org.eclipse.tracecompass.tmf.core.model.OutputElementStyle;
import org.eclipse.tracecompass.tmf.core.model.OutputStyleModel;
import org.eclipse.tracecompass.tmf.core.model.StyleProperties;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

/**
 * Time graph data provider that shows the files read and written to by some selected
 * threads.
 *
 * TODO Support multiple TID selection as the tree allows it
 *
 * @author Matthew Khouzam
 */
@SuppressWarnings("restriction")
public class IoAccessDataProvider extends AbstractStateSystemAnalysisDataProvider implements ITimeGraphDataProvider<TimeGraphEntryModel>, IOutputStyleProvider {

    /**
     * Suffix for dataprovider ID
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.kernel.core.io.access.dataprovider"; //$NON-NLS-1$ ;
    /**
     * String for the tid parameter of this view
     */
    public static final String TID_PARAM = "tid"; //$NON-NLS-1$

    private static final Logger LOGGER = TraceCompassLog.getLogger(IoAccessDataProvider.class);

    private static final String READ_STYLE = "Read"; //$NON-NLS-1$
    private static final String WRITE_STYLE = "Write"; //$NON-NLS-1$

    /* The map of basic styles */
    private static final Map<String, OutputElementStyle> STATE_MAP;
    /*
     * A map of styles names to a style that has the basic style as parent, to
     * avoid returning complete styles for each state
     */
    private static final Map<String, OutputElementStyle> STYLE_MAP = new HashMap<>();

    static {
        /* Build three different styles to use as examples */
        ImmutableMap.Builder<String, OutputElementStyle> builder = new ImmutableMap.Builder<>();
        List<Pair<String, String>> COLOR_LIST = IODataPalette.getColors();
        Pair<String, String> colorPair = COLOR_LIST.get(0);
        builder.put(READ_STYLE, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, READ_STYLE,
                StyleProperties.BACKGROUND_COLOR, colorPair.getFirst())));
        builder.put(WRITE_STYLE, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, WRITE_STYLE,
                StyleProperties.BACKGROUND_COLOR, colorPair.getSecond())));
        STATE_MAP = builder.build();
        STYLE_MAP.put(READ_STYLE, new OutputElementStyle(READ_STYLE));
        STYLE_MAP.put(WRITE_STYLE, new OutputElementStyle(WRITE_STYLE));
    }

    private IoAnalysis fAnalysisModule;
    private final BiMap<Long, String> fIdToFile = HashBiMap.create();
    private final AtomicLong fIdGenerator = new AtomicLong();

    /**
     * Constructor
     *
     * @param trace
     *            the trace this provider represents
     * @param analysisModule
     *            the analysis encapsulated by this provider
     */
    public IoAccessDataProvider(ITmfTrace trace, IoAnalysis analysisModule) {
        super(trace);
        fAnalysisModule = analysisModule;
    }

    @Override
    public TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public TmfModelResponse<TimeGraphModel> fetchRowModel(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        fAnalysisModule.waitForInitialization();
        ITmfStateSystem ss = fAnalysisModule.getStateSystem();
        if (ss == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }

        long currentEnd = ss.getCurrentEndTime();
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(fetchParameters);
        List<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(fetchParameters);

        Object end = Iterables.getLast(times);
        if (!(end instanceof Number)) {
            return new TmfModelResponse<>(null, Status.FAILED, CommonStatusMessage.INCORRECT_QUERY_PARAMETERS);
        }
        boolean complete = ss.waitUntilBuilt(0) || ((Number) end).longValue() <= currentEnd;

        if (monitor != null && monitor.isCanceled()) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
        }
        try (FlowScopeLog scope = new FlowScopeLogBuilder(LOGGER, Level.FINE, "AbstractTimeGraphDataProvider#fetchRowModel") //$NON-NLS-1$
                .setCategory(getClass().getSimpleName()).build()) {

            TimeGraphModel models = getRowModel(ss, fetchParameters, monitor, selectedItems);
            if (models == null) {
                if (monitor != null && monitor.isCanceled()) {
                    return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
                }
                // There was some other failure that returned null
                return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, "Request failed"); //$NON-NLS-1$
            }
            return new TmfModelResponse<>(models, complete ? Status.COMPLETED : Status.RUNNING,
                    complete ? CommonStatusMessage.COMPLETED : CommonStatusMessage.RUNNING);
        } catch (StateSystemDisposedException | TimeRangeException | IndexOutOfBoundsException e) {
            return new TmfModelResponse<>(null, Status.FAILED, String.valueOf(e.getMessage()));
        }
    }

    private @Nullable TimeGraphModel getRowModel(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor, List<Long> selectedItems) throws StateSystemDisposedException {

        Object tidParam = parameters.get(TID_PARAM);
        if (!(tidParam instanceof Integer)) {
            return new TimeGraphModel(Collections.emptyList());
        }

        List<Long> times = DataProviderParameterUtils.extractTimeRequested(parameters);
        if (times == null || times.isEmpty()) {
            // No time specified
            return new TimeGraphModel(Collections.emptyList());
        }

        // Prepare the set of file names being requested
        Map<String, Long> files = new HashMap<>();
        for (Long selectedItem : selectedItems) {
            // Look in the "Resources" section to find the quark for the thread
            // for each requested file
            String filename = fIdToFile.get(selectedItem);
            if (filename == null) {
                continue;
            }
            files.put(filename, selectedItem);
        }

        if (files.isEmpty()) {
            return new TimeGraphModel(Collections.emptyList());
        }

        // Get the current quark for each file, under the resources section
        Map<Integer, Long> quarkToId = new HashMap<>();
        for (Entry<String, Long> fileEntry : files.entrySet()) {
            int quark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_RESOURCES, fileEntry.getKey(), String.valueOf(tidParam), IoStateProvider.ATTRIBUTE_OPERATION);
            if (quark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                // No data for this file, ignore
            }
            quarkToId.put(quark, fileEntry.getValue());
        }

        // Query the operations intervals for the files
        TreeMultimap<Long, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparing(ITmfStateInterval::getStartTime));
        for (ITmfStateInterval interval : ss.query2D(quarkToId.keySet(), times)) {
            intervals.put(Objects.requireNonNull(quarkToId.get(interval.getAttribute())), interval);
        }

        if (monitor != null && monitor.isCanceled()) {
            return new TimeGraphModel(Collections.emptyList());
        }

        // Create the states for each requested file
        List<ITimeGraphRowModel> rows = new ArrayList<>();
        for (Entry<Long, Collection<ITmfStateInterval>> entryIntervals : intervals.asMap().entrySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return new TimeGraphModel(Collections.emptyList());
            }
            List<ITimeGraphState> states = new ArrayList<>();
            for (ITmfStateInterval interval : entryIntervals.getValue()) {
                long startTime = interval.getStartTime();
                long duration = interval.getEndTime() - startTime + 1;
                states.add(new TimeGraphState(startTime, duration, null, getStyleFor(interval)));
            }
            rows.add(new TimeGraphRowModel(entryIntervals.getKey(), states));
        }
        return new TimeGraphModel(rows);
    }

    private static @Nullable OutputElementStyle getStyleFor(ITmfStateInterval interval) {
        Object value = interval.getValue();
        if (!(value instanceof String)) {
            return null;
        }
        String operation = (String) value;
        if (operation.equals(IoStateProvider.ATTRIBUTE_READ)) {
            return STYLE_MAP.get(READ_STYLE);
        }
        return STYLE_MAP.get(WRITE_STYLE);
    }

    @Override
    public TmfModelResponse<Map<String, String>> fetchTooltip(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(Collections.emptyMap(), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public TmfModelResponse<TmfTreeModel<TimeGraphEntryModel>> fetchTree(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        fAnalysisModule.waitForInitialization();
        ITmfStateSystem ss = fAnalysisModule.getStateSystem();
        if (ss == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }

        boolean complete = ss.waitUntilBuilt(0);
        try (FlowScopeLog scope = new FlowScopeLogBuilder(LOGGER, Level.FINE, "AbstractTreeDataProvider#fetchTree") //$NON-NLS-1$
                .setCategory(getClass().getSimpleName()).build()) {
            TmfTreeModel<TimeGraphEntryModel> tree = getTree(ss, fetchParameters, monitor);
            if (monitor != null && monitor.isCanceled()) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            return new TmfModelResponse<>(tree,
                    complete ? ITmfResponse.Status.COMPLETED : ITmfResponse.Status.RUNNING,
                    complete ? CommonStatusMessage.RUNNING : CommonStatusMessage.COMPLETED);

        } catch (StateSystemDisposedException e) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }
    }

    private TmfTreeModel<TimeGraphEntryModel> getTree(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {

        List<ITmfStateInterval> currentOperations = new ArrayList<>();
        Multimap<String, @NonNull ITmfStateInterval> fds = HashMultimap.create();
        boolean gotData = fillQueryIntervals(ss, parameters, monitor, false, currentOperations, fds);
        if (!gotData || (monitor != null && monitor.isCanceled())) {
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }

        Set<String> files = new HashSet<>();
        // For each rw operation, find the file descriptor interval that matches
        // the file name
        for (ITmfStateInterval rwInterval : currentOperations) {
            Object fdObj = rwInterval.getValue();
            if (!(fdObj instanceof Long)) {
                // Not a proper file descriptor
                continue;
            }
            Collection<@NonNull ITmfStateInterval> fileNameIntervals = Objects.requireNonNull(fds.get(String.valueOf(fdObj)));
            ITmfStateInterval overlapping = findOverlapping(rwInterval, fileNameIntervals);
            if (overlapping != null) {
                files.add(String.valueOf(overlapping.getValue()));
            }
        }

        Builder<@NonNull TimeGraphEntryModel> builder = new Builder<>();
        for (String file : files) {
            long id = getId(file);
            builder.add(new TimeGraphEntryModel(id, -1, file, ss.getStartTime(), ss.getCurrentEndTime()));
        }

        return new TmfTreeModel<>(Collections.emptyList(), builder.build());
    }

    /**
     * Fill 2 arrays with intervals: the currentOperations that contains the
     * current non-null reads and writes operations for the thread and the fds
     * map which has the intervals containing the filename for each file
     * descriptor
     *
     * @param discrete
     *            Whether to get only the intervals for the requested times, or
     *            all intervals in the range (for instance to build the tree)
     */
    private static boolean fillQueryIntervals(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor, boolean discrete, List<ITmfStateInterval> currentOperations, Multimap<String, ITmfStateInterval> fds)
            throws IndexOutOfBoundsException, TimeRangeException, StateSystemDisposedException {
        Object tidParam = parameters.get(TID_PARAM);
        if (!(tidParam instanceof Integer)) {
            return false;
        }

        Integer selectedTid = (Integer) tidParam;
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(parameters);
        if (times == null || times.isEmpty()) {
            // No time specified
            return false;
        }
        long start = Long.MAX_VALUE;
        long end = 0;
        for (Long time : times) {
            start = Math.min(start, time);
            end = Math.max(start, time);
        }
        start = Math.max(start, ss.getStartTime());
        end = Math.min(end, ss.getCurrentEndTime());

        // First, get the file descriptor table quark for this thread, all the
        // rest can be 2d queried
        int fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_TID, String.valueOf(selectedTid), IoStateProvider.ATTRIBUTE_FDTBL);
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return false;
        }

        ITmfStateInterval fdLink = StateSystemUtils.queryUntilNonNullValue(ss, fdTblQuark, start, end);
        if (fdLink == null) {
            // No file descriptor table for this time range, return empty
            return false;
        }

        fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_FDTBL, String.valueOf(fdLink.getValue()));
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            // For some reason, the fd table is not available, just return empty
            // list
            return false;
        }

        /*
         * Get the attributes to query: they will be
         *
         * 1- The file descriptors under the thread's FD table, they contain the
         * resource name
         *
         * 2- The CURRENT and FD attributes under the current thread to get the
         * current read/write operations
         */
        List<Integer> toQuery = new ArrayList<>();
        toQuery.addAll(ss.getSubAttributes(fdTblQuark, false));
        int readFd = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_TID, String.valueOf(selectedTid), IoStateProvider.ATTRIBUTE_READ, IoStateProvider.ATTRIBUTE_FD);
        addIfExist(toQuery, readFd);
        int writeFd = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_TID, String.valueOf(selectedTid), IoStateProvider.ATTRIBUTE_WRITE, IoStateProvider.ATTRIBUTE_FD);
        addIfExist(toQuery, writeFd);

        Iterable<ITmfStateInterval> query2d = discrete ? ss.query2D(toQuery, times) : ss.query2D(toQuery, start, end);
        for (ITmfStateInterval interval : query2d) {
            if (monitor != null && monitor.isCanceled()) {
                return false;
            }
            if (interval.getValue() == null) {
                continue;
            }

            if (interval.getAttribute() == readFd || interval.getAttribute() == writeFd) {
                // Save the current operations in a list
                currentOperations.add(interval);
            } else {
                // Put the filename intervals in a map by file descriptor
                fds.put(ss.getAttributeName(interval.getAttribute()), interval);
            }
        }
        return true;

    }

    private static @Nullable ITmfStateInterval findOverlapping(ITmfStateInterval rwInterval, Collection<ITmfStateInterval> intervals) {
        for (ITmfStateInterval interval : intervals) {
            if (!(interval.getStartTime() > rwInterval.getEndTime() || interval.getEndTime() < rwInterval.getStartTime())) {
                return interval;
            }
        }
        return null;
    }

    private static void addIfExist(List<Integer> toQuery, int quark) {
        if (quark != ITmfStateSystem.INVALID_ATTRIBUTE) {
            toQuery.add(quark);
        }
    }

    /**
     * Get (and generate if necessary) a unique id for this quark. Should be
     * called inside {@link #getTree(ITmfStateSystem, Map, IProgressMonitor)},
     * where the write lock is held.
     *
     * @param file
     *            quark to map to
     * @return the unique id for this quark
     */
    private long getId(String file) {
        return fIdToFile.inverse().computeIfAbsent(file, q -> getEntryId());
    }

    /**
     * Get a new unique id, unbound to any quark.
     *
     * @return the unique id
     */
    protected long getEntryId() {
        return fIdGenerator.getAndIncrement();
    }

    // public @Nullable Long getBytesRead(long start, long end, long
    // attributeId) {
    // ITmfStateSystem ss = getAnalysisModule().getStateSystem();
    // if (ss == null) {
    // return null;
    // }
    //
    // Map<Long, Integer> selectedEntries = getSelectedEntries(new
    // SelectionTimeQueryFilter(Arrays.asList(start, end),
    // Collections.singleton(attributeId)));
    // Integer startingNodeQuark = selectedEntries.get(attributeId);
    // if (startingNodeQuark == null || startingNodeQuark >= OFFSET) {
    // return null;
    // }
    // int readQuark = ss.optQuarkRelative(startingNodeQuark,
    // IoStateProvider.ATTRIBUTE_READ);
    // return getdelta(start, end, ss, readQuark);
    // }
    //
    // public @Nullable Long getBytesWrite(long start, long end, long
    // attributeId) {
    // ITmfStateSystem ss = getAnalysisModule().getStateSystem();
    // if (ss == null) {
    // return null;
    // }
    //
    // Map<Long, Integer> selectedEntries = getSelectedEntries(new
    // SelectionTimeQueryFilter(Arrays.asList(start, end),
    // Collections.singleton(attributeId)));
    // Integer startingNodeQuark = selectedEntries.get(attributeId);
    // if (startingNodeQuark == null || startingNodeQuark >= OFFSET) {
    // return null;
    // }
    // int readQuark = ss.optQuarkRelative(startingNodeQuark,
    // IoStateProvider.ATTRIBUTE_WRITE);
    // return getdelta(start, end, ss, readQuark);
    // }

    // private static @Nullable Long getdelta(long start, long end,
    // ITmfStateSystem ss, int readQuark) {
    // if (readQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
    // return null;
    // }
    // try {
    // ITmfStateInterval startInterval = ss.querySingleState(start, readQuark);
    // ITmfStateInterval endInterval = ss.querySingleState(end, readQuark);
    // return endInterval.getValueLong() - startInterval.getValueLong();
    // } catch (StateSystemDisposedException e) {
    // return null;
    // }
    // }

    @Override
    public TmfModelResponse<OutputStyleModel> fetchStyle(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(new OutputStyleModel(STATE_MAP), Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public String getId() {
        return ID;
    }

}

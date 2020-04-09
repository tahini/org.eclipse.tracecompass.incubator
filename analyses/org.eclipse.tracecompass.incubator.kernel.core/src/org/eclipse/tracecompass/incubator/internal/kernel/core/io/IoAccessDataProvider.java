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
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLogBuilder;
import org.eclipse.tracecompass.internal.tmf.core.model.AbstractStateSystemAnalysisDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.dataprovider.X11ColorUtils;
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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeMultimap;

/**
 * <p>
 * File Descriptor data Provider
 * </p>
 * <p>
 * Shows per-file access
 * </p>
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

    private static final String META_IO_NAME = "Meta IO"; //$NON-NLS-1$
    private static final String IO_NAME = "IO"; //$NON-NLS-1$

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

        builder.put(META_IO_NAME, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, META_IO_NAME,
                StyleProperties.BACKGROUND_COLOR, String.valueOf(X11ColorUtils.toHexColor(174, 123, 131)))));
        builder.put(IO_NAME, new OutputElementStyle(null, ImmutableMap.of(StyleProperties.STYLE_NAME, IO_NAME,
                StyleProperties.BACKGROUND_COLOR, String.valueOf(X11ColorUtils.toHexColor(140, 180, 165)))));
        STATE_MAP = builder.build();
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

            TimeGraphModel models = getRowModel(ss, fetchParameters, monitor, times, selectedItems);
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

    private @Nullable TimeGraphModel getRowModel(ITmfStateSystem ss, Map<String, Object> parameters, @Nullable IProgressMonitor monitor, List<Long> times, List<Long> selectedItems) throws StateSystemDisposedException {
        Object tidParam = parameters.get(TID_PARAM);
        if (!(tidParam instanceof Integer)) {
            return new TimeGraphModel(Collections.emptyList());
        }

        // First, get the list of fds that correspond to the files being requested
        // Maps the file quark to the ID
        Map<Integer, Long> fdQuarkToId = new HashMap<>();
        for (Long selectedItem : selectedItems) {
            // Look in the "Resources" section to find the quark for the thread
            // for each requested file
            String filename = fIdToFile.get(selectedItem);
            if (filename == null) {
                continue;
            }
            int fileQuark = ss.optQuarkAbsolute(IoStateProvider.RESOURCES, filename, String.valueOf(tidParam));
            if (fileQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                fdQuarkToId.put(fileQuark, selectedItem);
            }
        }

        Map<ITmfStateInterval, Long> fdIntervals = new HashMap<>();
        Set<Long> queryFds = new TreeSet<>();
        for (ITmfStateInterval interval : ss.query2D(fdQuarkToId.keySet(), times)) {
            // Get the fds for the files that belong to this thread. A file can be opened more than once with different FDs, so we need to get them all
            Object value = interval.getValue();
            if (value instanceof Long) {
                queryFds.add((Long) value);
                fdIntervals.put(interval, Objects.requireNonNull(fdQuarkToId.get(interval.getAttribute())));
            }
        }
        if (monitor != null && monitor.isCanceled()) {
            return null;
        }
        if (queryFds.isEmpty()) {
            return new TimeGraphModel(Collections.emptyList());
        }

        // Get the right file table for this thread
        int fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_TID, String.valueOf(tidParam), IoStateProvider.ATTRIBUTE_FDTBL);
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return new TimeGraphModel(Collections.emptyList());
        }

        ITmfStateInterval fdLink = StateSystemUtils.queryUntilNonNullValue(ss, fdTblQuark, times.get(0), times.get(times.size() - 1));
        if (fdLink == null) {
            // No file descriptor table for this time range, return empty
            return new TimeGraphModel(Collections.emptyList());
        }

        fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_FDTBL, String.valueOf(fdLink.getValue()));
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            // For some reason, the fd table is not available, just return empty list
            return new TimeGraphModel(Collections.emptyList());
        }

        // Get the fd quarks, and read/write to query rw data for the requested files
        List<Integer> toQuery = new ArrayList<>();
        Map<Integer, Long> rwToFd = new HashMap<>();
        for (Long queryFd : queryFds) {
            int fdQuark = ss.optQuarkRelative(fdTblQuark, String.valueOf(queryFd));
            if (fdQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                continue;
            }
            // Also get the running read/write attributes for this, that's where the information we're looking for is stored
            int rwQuark = ss.optQuarkRelative(fdQuark, IoStateProvider.ATTRIBUTE_READ);
            if (rwQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                List<Integer> rwAttributes = ss.getSubAttributes(rwQuark, false);
                toQuery.addAll(rwAttributes);
                rwAttributes.forEach(rw -> rwToFd.put(rw, queryFd));
            }
            rwQuark = ss.optQuarkRelative(fdQuark, IoStateProvider.ATTRIBUTE_WRITE);
            if (rwQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                List<Integer> rwAttributes = ss.getSubAttributes(rwQuark, false);
                toQuery.addAll(rwAttributes);
                rwAttributes.forEach(rw -> rwToFd.put(rw, queryFd));
            }
        }

        TreeMultimap<Long, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
              Comparator.comparing(ITmfStateInterval::getStartTime));
        for (ITmfStateInterval interval : ss.query2D(toQuery, times)) {
            Object value = interval.getValue();
            if (value == null) {
                continue;
            }
            // Add this rw state to the right ID
            Long fd = Objects.requireNonNull(rwToFd.get(interval.getAttribute()));
            // If this file descriptor is in the range of one of the files requested, add it to the right ID
            for (Entry<ITmfStateInterval, Long> intervalEntry : fdIntervals.entrySet()) {
                ITmfStateInterval fdInterval = intervalEntry.getKey();
                if (fd.equals(fdInterval.getValue()) &&
                        interval.getStartTime() >= fdInterval.getStartTime() &&
                        interval.getEndTime() <= fdInterval.getEndTime()) {
                    // Add this state to ID
                    Long id = intervalEntry.getValue();
                    intervals.put(id, fdInterval);
                    break;
                }
            }
        }

        List<ITimeGraphRowModel> rows = new ArrayList<>();
        for (Entry<Long, Collection<ITmfStateInterval>> entryIntervals : intervals.asMap().entrySet()) {
            List<ITimeGraphState> states = new ArrayList<>();
            for (ITmfStateInterval interval : entryIntervals.getValue()) {
                long startTime = interval.getStartTime();
                long duration = interval.getEndTime() - startTime + 1;
                states.add(new TimeGraphState(startTime, duration, ((Long) interval.getValueLong()).intValue()));
            }
            rows.add(new TimeGraphRowModel(entryIntervals.getKey(), states));
        }
        return new TimeGraphModel(rows);
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

        Object tidParam = parameters.get(TID_PARAM);
        if (!(tidParam instanceof Integer)) {
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }

        Integer selectedTid = (Integer) tidParam;
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(parameters);
        if (times == null || times.isEmpty()) {
            // No time specified
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }
        long start = Long.MAX_VALUE;
        long end = 0;
        for (Long time : times) {
            start = Math.min(start, time);
            end = Math.max(start, time);
        }

        int fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_TID, String.valueOf(selectedTid), IoStateProvider.ATTRIBUTE_FDTBL);
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }

        ITmfStateInterval fdLink = StateSystemUtils.queryUntilNonNullValue(ss, fdTblQuark, start, end);
        if (fdLink == null) {
            // No file descriptor table for this time range, return empty
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }

        fdTblQuark = ss.optQuarkAbsolute(IoStateProvider.ATTRIBUTE_FDTBL, String.valueOf(fdLink.getValue()));
        if (fdTblQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            // For some reason, the fd table is not available, just return empty list
            return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
        }

        // Return entries
        List<Integer> toQuery = new ArrayList<>();
        List<Integer> subAttributes = ss.getSubAttributes(fdTblQuark, false);
        Set<String> files = new HashSet<>();
        toQuery.addAll(subAttributes);
        for (ITmfStateInterval interval : ss.query2D(subAttributes, start, end)) {
            if (monitor != null && monitor.isCanceled()) {
                return new TmfTreeModel<>(Collections.emptyList(), Collections.emptyList());
            }
            if (interval.getValue() == null) {
                continue;
            }

            Object filename = interval.getValue();
            // Did we read/write this file in this period? If any of the read/write sub-attribute has a non null value, then it's been accessed
            List<Integer> queryQuarks = new ArrayList<>();
            int rwQuark = ss.optQuarkRelative(interval.getAttribute(), IoStateProvider.ATTRIBUTE_READ);
            if (rwQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                queryQuarks.add(rwQuark);
                queryQuarks.addAll(ss.getSubAttributes(rwQuark, false));
            }
            rwQuark = ss.optQuarkRelative(interval.getAttribute(), IoStateProvider.ATTRIBUTE_WRITE);
            if (rwQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                queryQuarks.add(rwQuark);
                queryQuarks.addAll(ss.getSubAttributes(rwQuark, false));
            }

            for (ITmfStateInterval subInterval : ss.query2D(queryQuarks, interval.getStartTime(), interval.getEndTime())) {
                if (subInterval.getValue() != null) {
                    files.add(String.valueOf(filename));
                    break;
                }
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
     * Get (and generate if necessary) a unique id for this quark. Should be called
     * inside {@link #getTree(ITmfStateSystem, Map, IProgressMonitor)},
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

//    public @Nullable Long getBytesRead(long start, long end, long attributeId) {
//        ITmfStateSystem ss = getAnalysisModule().getStateSystem();
//        if (ss == null) {
//            return null;
//        }
//
//        Map<Long, Integer> selectedEntries = getSelectedEntries(new SelectionTimeQueryFilter(Arrays.asList(start, end), Collections.singleton(attributeId)));
//        Integer startingNodeQuark = selectedEntries.get(attributeId);
//        if (startingNodeQuark == null || startingNodeQuark >= OFFSET) {
//            return null;
//        }
//        int readQuark = ss.optQuarkRelative(startingNodeQuark, IoStateProvider.ATTRIBUTE_READ);
//        return getdelta(start, end, ss, readQuark);
//    }
//
//    public @Nullable Long getBytesWrite(long start, long end, long attributeId) {
//        ITmfStateSystem ss = getAnalysisModule().getStateSystem();
//        if (ss == null) {
//            return null;
//        }
//
//        Map<Long, Integer> selectedEntries = getSelectedEntries(new SelectionTimeQueryFilter(Arrays.asList(start, end), Collections.singleton(attributeId)));
//        Integer startingNodeQuark = selectedEntries.get(attributeId);
//        if (startingNodeQuark == null || startingNodeQuark >= OFFSET) {
//            return null;
//        }
//        int readQuark = ss.optQuarkRelative(startingNodeQuark, IoStateProvider.ATTRIBUTE_WRITE);
//        return getdelta(start, end, ss, readQuark);
//    }

//    private static @Nullable Long getdelta(long start, long end, ITmfStateSystem ss, int readQuark) {
//        if (readQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
//            return null;
//        }
//        try {
//            ITmfStateInterval startInterval = ss.querySingleState(start, readQuark);
//            ITmfStateInterval endInterval = ss.querySingleState(end, readQuark);
//            return endInterval.getValueLong() - startInterval.getValueLong();
//        } catch (StateSystemDisposedException e) {
//            return null;
//        }
//    }

    @Override
    public TmfModelResponse<OutputStyleModel> fetchStyle(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(new OutputStyleModel(STATE_MAP), Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public String getId() {
        return ID;
    }

}

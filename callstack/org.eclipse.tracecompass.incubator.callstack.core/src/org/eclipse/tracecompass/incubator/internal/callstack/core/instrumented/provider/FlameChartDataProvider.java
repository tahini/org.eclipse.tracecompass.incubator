/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLogBuilder;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.flamechart.CallStack;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.IFlameChartProvider;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.CallStackSeries;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.InstrumentedCallStackElement;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel.EntryType;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.AbstractTmfTraceDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.ITmfResponse.Status;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfAnalysisModuleWithStateSystems;
import org.eclipse.tracecompass.tmf.core.symbols.ISymbolProvider;
import org.eclipse.tracecompass.tmf.core.symbols.SymbolProviderManager;
import org.eclipse.tracecompass.tmf.core.symbols.SymbolProviderUtils;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Iterables;

/**
 * This class provides the data from an instrumented callstack analysis, in the
 * form of a flamechart, ie the groups are returned hierarchically and leaf
 * groups return their callstacks.
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class FlameChartDataProvider extends AbstractTmfTraceDataProvider implements ITimeGraphDataProvider<FlameChartEntryModel> {

    /**
     * Provider ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.flamechart"; //$NON-NLS-1$
    private static final AtomicLong ENTRY_ID = new AtomicLong();
    /**
     * Logger for Abstract Tree Data Providers.
     */
    private static final Logger LOGGER = TraceCompassLog.getLogger(FlameChartDataProvider.class);

    private final Map<Long, FlameChartEntryModel> fEntries = new HashMap<>();
    private final BiMap<Long, Long> fKernelToCs = HashBiMap.create();
    private final Collection<ISymbolProvider> fProviders = new ArrayList<>();
    private final BiMap<Long, Integer> fIdToQuark = HashBiMap.create();

    private final LoadingCache<Pair<Integer, ITmfStateInterval>, @Nullable String> fTimeEventNames = Objects.requireNonNull(CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build(new CacheLoader<Pair<Integer, ITmfStateInterval>, @Nullable String>() {
                @Override
                public @Nullable String load(Pair<Integer, ITmfStateInterval> pidInterval) {
                    Integer pid = pidInterval.getFirst();
                    ITmfStateInterval interval = pidInterval.getSecond();

                    Object nameValue = interval.getValue();
                    Long address = null;
                    String name = null;
                    if (nameValue instanceof String) {
                        name = (String) nameValue;
                        try {
                            address = Long.parseLong(name, 16);
                        } catch (NumberFormatException e) {
                            // leave name as null
                        }
                    } else if (nameValue instanceof Integer) {
                        Integer intValue = (Integer) nameValue;
                        name = "0x" + Integer.toUnsignedString(intValue, 16); //$NON-NLS-1$
                        address = intValue.longValue();
                    } else if (nameValue instanceof Long) {
                        address = (long) nameValue;
                        name = "0x" + Long.toUnsignedString(address, 16); //$NON-NLS-1$
                    }
                    if (address != null) {
                        name = SymbolProviderUtils.getSymbolText(fProviders, pid, interval.getStartTime(), address);
                    }
                    return name;
                }
            }));

    private final IFlameChartProvider fFcProvider;

    private final String fAnalysisId;
    private @Nullable TmfModelResponse<List<FlameChartEntryModel>> fCached;
    private final ReentrantReadWriteLock fLock = new ReentrantReadWriteLock(false);

    /**
     * Constructor
     *
     * @param trace
     *            The trace for which this data provider applies
     * @param module
     *            The flame chart provider encapsulated by this provider
     * @param secondaryId
     *            The ID of the flame chart provider
     */
    public FlameChartDataProvider(ITmfTrace trace, IFlameChartProvider module, String secondaryId) {
        super(trace);
        fFcProvider = module;
        fAnalysisId = secondaryId;
        resetFunctionNames(new NullProgressMonitor());
    }

    public static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace, String secondaryId) {
     // The trace can be an experiment, so we need to know if there are multiple analysis modules with the same ID
        Iterable<IFlameChartProvider> modules = TmfTraceUtils.getAnalysisModulesOfClass(trace, IFlameChartProvider.class);
        Iterable<IFlameChartProvider> filteredModules = Iterables.filter(modules, m -> ((IAnalysisModule) m).getId().equals(secondaryId));
        Iterator<IFlameChartProvider> iterator = filteredModules.iterator();
        if (iterator.hasNext()) {
            IFlameChartProvider module = iterator.next();
            if (iterator.hasNext()) {
                // More than one module, must be an experiment, return null so the factory can try with individual traces
                return null;
            }
            ((IAnalysisModule) module).schedule();
            return new FlameChartDataProvider(trace, module, secondaryId);
        }
        return null;
    }

    @Override
    public TmfModelResponse<List<ITimeGraphArrow>> fetchArrows(TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        // TODO Implement
        return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public TmfModelResponse<Map<String, String>> fetchTooltip(SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        // TODO Implement
        return new TmfModelResponse<>(null, Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public String getId() {
        return ID + ':' + fAnalysisId;
    }

    // Get an entry for a quark
    private long getEntryId(int quark) {
        return fIdToQuark.inverse().computeIfAbsent(quark, q -> ENTRY_ID.getAndIncrement());
    }

    // Get a new entry ID
    private static long getEntryId() {
        return ENTRY_ID.getAndIncrement();
    }

    // Get a new entry for a kernel entry ID
    private long getKernelEntryId(long baseId) {
        return fKernelToCs.inverse().computeIfAbsent(baseId, id -> ENTRY_ID.getAndIncrement());
    }

    @Override
    public TmfModelResponse<List<FlameChartEntryModel>> fetchTree(TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        if (fCached != null) {
            return fCached;
        }

        fLock.writeLock().lock();
        try (FlowScopeLog scope = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameChartDataProvider#fetchTree") //$NON-NLS-1$
                .setCategory(getClass().getSimpleName()).build()) {
            IFlameChartProvider fcProvider = fFcProvider;
            boolean complete = fcProvider.isComplete();
            Collection<CallStackSeries> callStacks = fcProvider.getCallStackSeries();
            long start = getTrace().getStartTime().getValue();
            long end = getTrace().getEndTime().getValue();

            // Initialize the first element of the tree
            ImmutableList.Builder<FlameChartEntryModel> builder = ImmutableList.builder();
            long traceId = getEntryId();
            FlameChartEntryModel traceEntry = new FlameChartEntryModel(traceId, -1, getTrace().getName(), start, end, FlameChartEntryModel.EntryType.TRACE, IHostModel.UNKNOWN_TID);
            builder.add(traceEntry);

            for (CallStackSeries callstack : callStacks) {
                @NonNull
                FlameChartEntryModel callStackRoot = traceEntry;
                // If there is more than one callstack objects in the analysis, create a root
                // per series
                if (callStacks.size() > 1) {
                    callStackRoot = new FlameChartEntryModel(getEntryId(), callStackRoot.getId(), callstack.getName(), start, end + 1, FlameChartEntryModel.EntryType.TRACE, IHostModel.UNKNOWN_TID);
                }
                for (ICallStackElement element : callstack.getRootElements()) {
                    if (monitor != null && monitor.isCanceled()) {
                        return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
                    }
                    processCallStackElement(element, builder, callStackRoot);
                }
            }
            List<FlameChartEntryModel> tree = builder.build();
            tree.forEach(entry -> fEntries.put(entry.getId(), entry));
            if (complete) {
                TmfModelResponse<List<FlameChartEntryModel>> response = new TmfModelResponse<>(tree,
                        ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
                fCached = response;
                return response;
            }
            return new TmfModelResponse<>(tree, ITmfResponse.Status.RUNNING, CommonStatusMessage.RUNNING);
        } finally {
            fLock.writeLock().unlock();
        }
    }

    private void processCallStackElement(ICallStackElement element, Builder<FlameChartEntryModel> builder, FlameChartEntryModel parentEntry) {

        long elementId = (element instanceof InstrumentedCallStackElement) ? getEntryId(((InstrumentedCallStackElement) element).getQuark()) : getEntryId();
        FlameChartEntryModel entry = new FlameChartEntryModel(elementId, parentEntry.getId(), element, element.getName(), parentEntry.getStartTime(), parentEntry.getEndTime(), FlameChartEntryModel.EntryType.LEVEL, IHostModel.UNKNOWN_TID);
        builder.add(entry);

        // Is this an intermediate or leaf element
        if ((element instanceof InstrumentedCallStackElement) && element.isLeaf()) {
            // For the leaf element, add the callstack entries
            InstrumentedCallStackElement finalElement = (InstrumentedCallStackElement) element;
            CallStack callStack = finalElement.getCallStack();
            for (int i = 0; i < callStack.getMaxDepth(); i++) {
                FlameChartEntryModel flameChartEntry = new FlameChartEntryModel(getEntryId(callStack.getQuarkAtDepth(i + 1)), entry.getId(), element, String.valueOf(i + 1), parentEntry.getStartTime(), parentEntry.getEndTime(),
                        FlameChartEntryModel.EntryType.FUNCTION, IHostModel.UNKNOWN_TID);
                builder.add(flameChartEntry);
                if (i == 0 && callStack.hasKernelStatuses()) {
                    builder.add(new FlameChartEntryModel(getKernelEntryId(flameChartEntry.getId()), entry.getId(), element, "Kernel statuses", parentEntry.getStartTime(), parentEntry.getEndTime(), FlameChartEntryModel.EntryType.KERNEL, //$NON-NLS-1$
                            IHostModel.UNKNOWN_TID));
                }
            }
            return;
        }
        // Intermediate element, process children
        element.getChildren().stream().forEach(e -> processCallStackElement(e, builder, entry));

    }

    // Get the selected entries with the quark
    private BiMap<Long, FlameChartEntryModel> getSelectedEntries(SelectionTimeQueryFilter filter) {
        fLock.readLock().lock();
        try {
            BiMap<Long, FlameChartEntryModel> selectedEntries = HashBiMap.create();

            for (Long selectedItem : filter.getSelectedItems()) {
                FlameChartEntryModel entryModel = fEntries.get(selectedItem);
                if (entryModel != null) {
                    selectedEntries.put(selectedItem, entryModel);
                }
            }
            return selectedEntries;
        } finally {
            fLock.readLock().unlock();
        }
    }

    private static Collection<Long> getTimes(TimeQueryFilter filter, long start, long end) {
        Collection<Long> times = new HashSet<>();
        for (long time : filter.getTimesRequested()) {
            if (start <= time && time <= end) {
                times.add(time);
            }
        }
        return times;
    }

    @Override
    public TmfModelResponse<List<ITimeGraphRowModel>> fetchRowModel(SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        try (FlowScopeLog scope = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameChartDataProvider#fetchRowModel") //$NON-NLS-1$
                .setCategory(getClass().getSimpleName()).build()) {

            Map<Long, FlameChartEntryModel> entries = getSelectedEntries(filter);
            if (entries.size() == 1 && filter.getTimesRequested().length == 2) {
                // this is a request for a follow event.
                Entry<@NonNull Long, @NonNull FlameChartEntryModel> entry = entries.entrySet().iterator().next();
                if (filter.getStart() == Long.MIN_VALUE) {
                    return new TmfModelResponse<>(getFollowEvent(entry, filter.getEnd(), false), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
                } else if (filter.getEnd() == Long.MAX_VALUE) {
                    return new TmfModelResponse<>(getFollowEvent(entry, filter.getEnd(), true), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
                }
            }
            // For each kernel status entry, add the first row of the callstack
            addRequiredCallstacks(entries);

            SubMonitor subMonitor = SubMonitor.convert(monitor, "FlameChartDataProvider#fetchRowModel", 2); //$NON-NLS-1$
            IFlameChartProvider fcProvider = fFcProvider;
            boolean complete = fcProvider.isComplete();

            Map<Long, List<ITimeGraphState>> csRows = getCallStackRows(filter, entries, subMonitor);
            if (csRows == null) {
                // getRowModel returns null if the query was cancelled.
                return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            List<ITimeGraphRowModel> collect = csRows.entrySet().stream().map(entry -> new TimeGraphRowModel(entry.getKey(), entry.getValue())).collect(Collectors.toList());
            List<ITimeGraphRowModel> kernelRows = getKernelRows(filter, entries, csRows, subMonitor);
            if (kernelRows == null) {
                // getRowModel returns null if the query was cancelled.
                return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            collect.addAll(kernelRows);
            return new TmfModelResponse<>(collect, complete ? Status.COMPLETED : Status.RUNNING,
                    complete ? CommonStatusMessage.COMPLETED : CommonStatusMessage.RUNNING);
        } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException e) {
            return new TmfModelResponse<>(null, Status.FAILED, String.valueOf(e.getMessage()));
        }
    }

    private void addRequiredCallstacks(Map<Long, FlameChartEntryModel> entries) {
        for (Long id : entries.keySet()) {
            Long csId = fKernelToCs.get(id);
            if (csId != null) {
                FlameChartEntryModel entry = fEntries.get(csId);
                if (entry != null) {
                    entries.put(csId, entry);
                }
            }
        }
    }

    private @Nullable List<ITimeGraphRowModel> getKernelRows(SelectionTimeQueryFilter filter, Map<Long, FlameChartEntryModel> entries, Map<Long, List<ITimeGraphState>> csRows, SubMonitor subMonitor) {
        // Create empty row models for entries that are not function level entries (no
        // data for the callstack)
        // TODO: If any aggregation needs to be done, there should be the place, for
        // entries that are taken out
        List<ITimeGraphRowModel> rows = new ArrayList<>();
        for (Entry<Long, @NonNull FlameChartEntryModel> entry : entries.entrySet()) {
            if (!entry.getValue().getEntryType().equals(EntryType.KERNEL)) {
                continue;
            }
            // Get the callstack events that go with this kernel entry
            List<ITimeGraphState> eventList = csRows.get(fKernelToCs.get(entry.getKey()));
            for (ITimeGraphState state : eventList) {
                if (state.getValue() == Integer.MIN_VALUE) {
                    continue;
                }
                // This state is a function, so get statuses for this duration
            }
        }

        // FIXME hackish: Is there a way to hide the state system from even the provider
        // and get the data from the flame chart provider itself?
        ITmfStateSystem ss = getStateSystem();
        ArrayListMultimap<Integer, ITmfStateInterval> intervals = ArrayListMultimap.create();

        /* Do the actual query */
        for (ITmfStateInterval interval : ss.query2D(csEntries.values(), times)) {
            if (subMonitor.isCanceled()) {
                return null;
            }
            intervals.put(interval.getAttribute(), interval);
        }
        subMonitor.worked(1);

        for (Map.Entry<Long, Integer> entry : csEntries.entrySet()) {
            if (subMonitor.isCanceled()) {
                return null;
            }
            Collection<ITmfStateInterval> states = intervals.get(entry.getValue());
            List<ITimeGraphState> eventList = new ArrayList<>(states.size());
            states.forEach(state -> eventList.add(createTimeGraphState(state, fEntries.get(entry.getKey()))));
            eventList.sort(Comparator.comparingLong(ITimeGraphState::getStartTime));
            rows.add(new TimeGraphRowModel(entry.getKey(), eventList));
        }
        subMonitor.worked(1);
        return rows;
    }

    private @Nullable Map<Long, List<ITimeGraphState>> getCallStackRows(SelectionTimeQueryFilter filter, Map<Long, FlameChartEntryModel> entries, SubMonitor subMonitor) throws IndexOutOfBoundsException, TimeRangeException, StateSystemDisposedException {

        // Get the data for the model entries that are of type function
        Map<Long, List<ITimeGraphState>> rows = new HashMap<>();
        Map<Long, Integer> csEntries = new HashMap<>();
        for (Entry<Long, @NonNull FlameChartEntryModel> entry : entries.entrySet()) {
            Integer quark = fIdToQuark.get(entry.getKey());
            if (quark != null && entry.getValue().getEntryType().equals(EntryType.FUNCTION)) {
                csEntries.put(entry.getKey(), quark);
            }
        }

        // FIXME hackish: Is there a way to hide the state system from even the provider
        // and get the data from the flame chart provider itself?
        ITmfStateSystem ss = getStateSystem();
        ArrayListMultimap<Integer, ITmfStateInterval> intervals = ArrayListMultimap.create();
        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());
        /* Do the actual query */
        for (ITmfStateInterval interval : ss.query2D(csEntries.values(), times)) {
            if (subMonitor.isCanceled()) {
                return null;
            }
            intervals.put(interval.getAttribute(), interval);
        }
        subMonitor.worked(1);

        for (Map.Entry<Long, Integer> entry : csEntries.entrySet()) {
            if (subMonitor.isCanceled()) {
                return null;
            }
            Collection<ITmfStateInterval> states = intervals.get(entry.getValue());
            List<ITimeGraphState> eventList = new ArrayList<>(states.size());
            states.forEach(state -> eventList.add(createTimeGraphState(state, fEntries.get(entry.getKey()))));
            eventList.sort(Comparator.comparingLong(ITimeGraphState::getStartTime));
            rows.put(entry.getKey(), eventList);
        }
        subMonitor.worked(1);
        return rows;
    }

    private ITmfStateSystem getStateSystem() {
        if (!(fFcProvider instanceof ITmfAnalysisModuleWithStateSystems)) {
            throw new IllegalStateException("The flame chart should be saved in a state system"); //$NON-NLS-1$
        }
        Iterator<ITmfStateSystem> iterator = ((ITmfAnalysisModuleWithStateSystems) fFcProvider).getStateSystems().iterator();
        // This should be safe to do, otherwise, let the runtime exception be thrown
        return iterator.next();
    }

    private ITimeGraphState createTimeGraphState(ITmfStateInterval interval, @Nullable FlameChartEntryModel entry) {
        long startTime = interval.getStartTime();
        long duration = interval.getEndTime() - startTime + 1;
        Object value = interval.getValue();
        Integer pid = IHostModel.UNKNOWN_TID;
        if (entry != null) {
            pid = entry.getPid(startTime);
        }
        if (value != null) {
            String name = String.valueOf(fTimeEventNames.getUnchecked(new Pair<>(pid, interval)));
            return new TimeGraphState(startTime, duration, value.hashCode(), name);
        }
        return new TimeGraphState(startTime, duration, Integer.MIN_VALUE);
    }

    /**
     * Invalidate the function names cache and load the symbol providers. This
     * function should be used at the beginning of the provider, or whenever new
     * symbol providers are added
     *
     * @param monitor
     *            A progress monitor to follow this operation
     */
    public void resetFunctionNames(IProgressMonitor monitor) {
        fTimeEventNames.invalidateAll();
        synchronized (fProviders) {
            Collection<@NonNull ISymbolProvider> symbolProviders = SymbolProviderManager.getInstance().getSymbolProviders(getTrace());
            SubMonitor sub = SubMonitor.convert(monitor, "CallStackDataProvider#resetFunctionNames", symbolProviders.size()); //$NON-NLS-1$
            fProviders.clear();
            for (ISymbolProvider symbolProvider : symbolProviders) {
                fProviders.add(symbolProvider);
                symbolProvider.loadConfiguration(sub);
                sub.worked(1);
            }
        }
    }

    /**
     * Get the next or previous interval for a call stack entry ID, time and
     * direction
     *
     * @param entry
     *            whose key is the ID and value is the quark for the entry whose
     *            next / previous state we are searching for
     * @param time
     *            selection start time
     * @param forward
     *            if going to next or previous
     * @return the next / previous state encapsulated in a row if it exists, else
     *         null
     */
    private static @Nullable List<ITimeGraphRowModel> getFollowEvent(Entry<Long, FlameChartEntryModel> entry, long time, boolean forward) {
        // TODO Implement for incubator
        return null;

//        int parentQuark = ss.getParentAttributeQuark(entry.getValue());
//        ITmfStateInterval current = ss.querySingleState(Long.max(ss.getStartTime(),
//                Long.min(time, ss.getCurrentEndTime())), parentQuark);
//        ITmfStateInterval interval = null;
//        if (forward && current.getEndTime() + 1 <= ss.getCurrentEndTime()) {
//            interval = ss.querySingleState(current.getEndTime() + 1, parentQuark);
//        } else if (!forward && current.getStartTime() - 1 >= ss.getStartTime()) {
//            interval = ss.querySingleState(current.getStartTime() - 1, parentQuark);
//        }
//        if (interval != null && interval.getValue() instanceof Number) {
//            Object object = interval.getValue();
//            if (object instanceof Number) {
//                long value = ((Number) object).longValue();
//                TimeGraphState state = new TimeGraphState(interval.getStartTime(),
//                        interval.getEndTime() - interval.getStartTime(), value);
//                TimeGraphRowModel row = new TimeGraphRowModel(entry.getKey(),
//                        Collections.singletonList(state));
//                return Collections.singletonList(row);
//            }
//        }
//        return null;
    }

}

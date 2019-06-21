/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLogBuilder;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IDataPalette;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.ITree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider.MetricType;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTree;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel.EntryType;
import org.eclipse.tracecompass.incubator.internal.callstack.core.palette.FlameDefaultPalette;
import org.eclipse.tracecompass.internal.tmf.core.model.AbstractTmfTraceDataProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.IOutputStyleProvider;
import org.eclipse.tracecompass.tmf.core.model.OutputStyleModel;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

/**
 * A data provider for flame graphs, using a {@link IWeightedTreeProvider} as
 * input for the data
 *
 * TODO: Publish the presentation provider
 *
 * TODO: Find a way to advertise extra parameters (group_by, selection range)
 *
 * TODO: Use weighted tree instead of callgraph provider
 *
 * @author Geneviève Bastien
 * @param <N>
 *            The type of objects represented by each node in the tree
 * @param <E>
 *            The type of elements used to group the trees
 * @param <T>
 *            The type of the tree provided
 */
@SuppressWarnings("restriction")
public class FlameGraphDataProvider2<@NonNull N, E, @NonNull T extends WeightedTree<@NonNull N>>  extends AbstractTmfTraceDataProvider implements ITimeGraphDataProvider<FlameChartEntryModel>, IOutputStyleProvider {

    /**
     * Provider ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph.provider"; //$NON-NLS-1$
    /**
     * The key used to specify how to group the entries of the flame graph
     */
    public static final String GROUP_BY_KEY = "group_by"; //$NON-NLS-1$
    /**
     * The key used to specify a time selection to get the callgraph for. It
     * should be a list of 2 longs
     */
    public static final String SELECTION_RANGE_KEY = "selection_range"; //$NON-NLS-1$
    private static final AtomicLong ENTRY_ID = new AtomicLong();
    private final Comparator<@NonNull T> CCT_COMPARATOR = Comparator.comparing(T::getWeight).thenComparing(s -> String.valueOf(s.getObject()));
    private final Comparator<WeightedTree<N>> CCT_COMPARATOR2 = Comparator.comparing(WeightedTree<N>::getWeight).thenComparing(s -> String.valueOf(s.getObject()));
    /**
     * Logger for Abstract Tree Data Providers.
     */
    private static final Logger LOGGER = TraceCompassLog.getLogger(FlameGraphDataProvider2.class);

    private final IWeightedTreeProvider<N, E, T> fWtProvider;
    private final String fAnalysisId;
    private final long fTraceId = ENTRY_ID.getAndIncrement();

    private final ReentrantReadWriteLock fLock = new ReentrantReadWriteLock(false);
    private @Nullable Pair<Map<String, Object>, TmfModelResponse<TmfTreeModel<FlameChartEntryModel>>> fCached;
    private final Map<Long, FlameChartEntryModel> fEntries = new HashMap<>();
    private final Map<Long, WeightedTreeEntry> fCgEntries = new HashMap<>();
    private final Map<Long, Long> fEndTimes = new HashMap<>();
    private final IDataPalette fPalette;

    /** An internal class to describe the data for an entry */
    private class WeightedTreeEntry {
        private final E fElement;
        private final IWeightedTreeProvider<@NonNull N, E, @NonNull T> fCallgraph;
        private final int fDepth;

        private WeightedTreeEntry(E element, IWeightedTreeProvider<@NonNull N, E, @NonNull T> wtProvider, int depth) {
            fElement = element;
            fCallgraph = wtProvider;
            fDepth = depth;
        }
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace for which this data provider applies
     * @param module
     *            The weighted tree provider encapsulated by this provider
     * @param secondaryId
     *            The ID of the weighted tree provider
     */
    public FlameGraphDataProvider2(ITmfTrace trace, IWeightedTreeProvider<N, E, T> module, String secondaryId) {
        super(trace);
        fWtProvider = module;
        fAnalysisId = secondaryId;
        IDataPalette palette = module.getPalette();
        fPalette = palette == null ? FlameDefaultPalette.getInstance() : palette;
    }

    @Override
    public String getId() {
        return fAnalysisId;
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull TmfTreeModel<@NonNull FlameChartEntryModel>> fetchTree(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {

        fLock.writeLock().lock();
        try (FlowScopeLog scope = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphDataProvider#fetchTree") //$NON-NLS-1$
                .setCategory(getClass().getSimpleName()).build()) {
            // Did we cache this tree with those parameters
            Pair<Map<String, Object>, TmfModelResponse<TmfTreeModel<FlameChartEntryModel>>> cached = fCached;
            if (cached != null && cached.getFirst().equals(fetchParameters)) {
                return cached.getSecond();
            }

            fEntries.clear();
            fCgEntries.clear();
            SubMonitor subMonitor = Objects.requireNonNull(SubMonitor.convert(monitor, "FlameGraphDataProvider#fetchRowModel", 2)); //$NON-NLS-1$

            IWeightedTreeProvider<N, E, T> callGraph = getCallGraph(fetchParameters, subMonitor);
            if (subMonitor.isCanceled()) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            if (callGraph == null) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.TASK_CANCELLED);
            }

            long start = 0;

            // Initialize the first element of the tree
            List<FlameChartEntryModel.Builder> builder = new ArrayList<>();
            FlameChartEntryModel.Builder traceEntry = new FlameChartEntryModel.Builder(fTraceId, -1, getTrace().getName(), start, FlameChartEntryModel.EntryType.TRACE, -1);

            buildCallGraphEntries(callGraph, builder, traceEntry);

            ImmutableList.Builder<FlameChartEntryModel> treeBuilder = ImmutableList.builder();
            long end = traceEntry.getEndTime();
            for (FlameChartEntryModel.Builder builderEntry : builder) {
                treeBuilder.add(builderEntry.build());
                end = Math.max(end, builderEntry.getEndTime());
            }
            traceEntry.setEndTime(end);
            treeBuilder.add(traceEntry.build());
            List<FlameChartEntryModel> tree = treeBuilder.build();

            tree.forEach(entry -> {
                fEntries.put(entry.getId(), entry);
                fEndTimes.put(entry.getId(), entry.getEndTime());
            });

            TmfModelResponse<TmfTreeModel<FlameChartEntryModel>> response = new TmfModelResponse<>(new TmfTreeModel<>(Collections.emptyList(), tree),
                    ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
            fCached = new Pair<>(fetchParameters, response);
            return response;

        } finally {
            fLock.writeLock().unlock();
        }
    }

    private @Nullable IWeightedTreeProvider<N, E, T> getCallGraph(Map<String, Object> fetchParameters, SubMonitor subMonitor) {
        // Get the provider and wait for the analysis completion
        IWeightedTreeProvider<N, E, T> wtProvider = fWtProvider;
        if (wtProvider instanceof IAnalysisModule) {
            ((IAnalysisModule) wtProvider).waitForCompletion(subMonitor);
        }
        if (subMonitor.isCanceled()) {
            return null;
        }
        return wtProvider;

        // Get the full or selection callgraph
//        List<Long> selectionRange = DataProviderParameterUtils.extractLongList(fetchParameters, SELECTION_RANGE_KEY);
//        CallGraph callGraph;
//        if (selectionRange == null || selectionRange.size() != 2) {
//            callGraph = wtProvider.getCallGraph();
//        } else {
//            long time0 = selectionRange.get(0);
//            long time1 = selectionRange.get(1);
//            callGraph = wtProvider.getCallGraph(TmfTimestamp.fromNanos(Math.min(time0, time1)), TmfTimestamp.fromNanos(Math.max(time0, time1)));
//        }
//
//        // Look if we need to group the callgraph
//        ICallStackGroupDescriptor groupDescriptor = extractGroupDescriptor(fetchParameters, wtProvider);
//        if (groupDescriptor != null) {
//            callGraph = CallGraphGroupBy.groupCallGraphBy(groupDescriptor, callGraph);
//        }
//
//        return callGraph;
    }

//    private static @Nullable ICallStackGroupDescriptor extractGroupDescriptor(Map<String, Object> fetchParameters, IWeightedTreeProvider<?, ?, ?> fcProvider) {
//        Object groupBy = fetchParameters.get(GROUP_BY_KEY);
//        if (groupBy == null) {
//            return null;
//        }
//        String groupName = String.valueOf(groupBy);
//        // Is it the all group descriptor
//        if (groupName.equals(AllGroupDescriptor.getInstance().getName())) {
//            return AllGroupDescriptor.getInstance();
//        }
//        // Try to find the right group descriptor
//        for (ICallStackGroupDescriptor groupDescriptor : fcProvider.getGroupDescriptors()) {
//            if (groupDescriptor.getName().equals(groupName)) {
//                return groupDescriptor;
//            }
//        }
//        return null;
//    }

    private void buildCallGraphEntries(IWeightedTreeProvider<N, E, T> wtProvider, List<FlameChartEntryModel.Builder> builder, FlameChartEntryModel.Builder traceEntry) {
        Collection<E> elements = wtProvider.getElements();
        for (E element : elements) {
            buildChildrenEntries(element, wtProvider, builder, traceEntry);
        }

    }

    /**
     * Build the entry list for one thread
     *
     * @param element
     *            The node of the aggregation tree
     */
    private void buildChildrenEntries(E element, IWeightedTreeProvider<N, E, T> wtProvider, List<FlameChartEntryModel.Builder> builder, FlameChartEntryModel.Builder parent) {
        // Add the entry
        FlameChartEntryModel.Builder entry = new FlameChartEntryModel.Builder(ENTRY_ID.getAndIncrement(),
                parent.getId(), (element instanceof ITree<?>) ? String.valueOf(((ITree<?>) element).getObject()) : String.valueOf(element), 0, FlameChartEntryModel.EntryType.LEVEL, -1);
        builder.add(entry);

        // Create the hierarchy of children entries if available
        if (element instanceof ITree<?>) {
            for (ITree<?> child : ((ITree<?>) element).getChildren()) {
                buildChildrenEntries((E) child, wtProvider, builder, entry);
            }
        }

        // Update endtime with the children and add them to builder
        long endTime = entry.getEndTime();
        for (FlameChartEntryModel.Builder childEntry : builder) {
            if (childEntry.getParentId() == entry.getId()) {
                endTime = Math.max(childEntry.getEndTime(), endTime);
            }
        }
        entry.setEndTime(endTime);

        List<T> rootTrees = new ArrayList<>(wtProvider.getTreesFor(element));
        // Create the function callsite entries
        if (rootTrees.isEmpty()) {
            return;
        }

        List<FlameChartEntryModel.Builder> childrenEntries = new ArrayList<>();
        List<FlameChartEntryModel.Builder> extraEntries = new ArrayList<>();
        Deque<Long> timestampStack = new ArrayDeque<>();
        timestampStack.push(0L);

        // Sort children by duration
        rootTrees.sort(CCT_COMPARATOR);
        for (T rootFunction : rootTrees) {
            createLevelChildren(element, rootFunction, childrenEntries, timestampStack, entry.getId());
            long currentThreadDuration = timestampStack.pop() + rootFunction.getWeight();
            timestampStack.push(currentThreadDuration);
        }
        for (FlameChartEntryModel.Builder child : childrenEntries) {
            builder.add(child);
            fCgEntries.put(child.getId(), new WeightedTreeEntry(element, wtProvider, child.getDepth()));
        }
        for (FlameChartEntryModel.Builder child : extraEntries) {
            builder.add(child);
            fCgEntries.put(child.getId(), new WeightedTreeEntry(element, wtProvider, child.getDepth()));
        }
        entry.setEndTime(timestampStack.pop());
        return;
    }

    /**
     * Parse the aggregated tree created by the callGraphAnalysis and creates
     * the event list (functions) for each entry (depth)
     *
     * @param element
     *
     * @param rootFunction
     *            The first node of the aggregation tree
     * @param childrenEntries
     *            The list of entries for one thread
     * @param timestampStack
     *            A stack used to save the functions timeStamps
     */
    private void createLevelChildren(E element, @NonNull T rootFunction, List<FlameChartEntryModel.Builder> childrenEntries, Deque<Long> timestampStack, long parentId) {
        long lastEnd = timestampStack.peek();
        // Prepare all the level entries for this callsite
        for (int i = 0; i <= rootFunction.getMaxDepth() - 1; i++) {
            if (i >= childrenEntries.size()) {
                FlameChartEntryModel.Builder entry = new FlameChartEntryModel.Builder(ENTRY_ID.getAndIncrement(), parentId, String.valueOf(i), 0, EntryType.FUNCTION, i);
                childrenEntries.add(entry);
            }
            childrenEntries.get(i).setEndTime(lastEnd + rootFunction.getWeight());
        }
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull TimeGraphModel> fetchRowModel(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        SubMonitor subMonitor = Objects.requireNonNull(SubMonitor.convert(monitor, "FlameGraphDataProvider#fetchRowModel", 2)); //$NON-NLS-1$

        List<Long> times = DataProviderParameterUtils.extractTimeRequested(fetchParameters);
        if (times == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.INCORRECT_QUERY_PARAMETERS);
        }
        List<ITimeGraphRowModel> rowModels = new ArrayList<>();

        // Get the selected entries
        Collection<Long> selected = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
        if (selected == null) {
            // No entry selected, assume all
            selected = fEntries.keySet();
        }
        List<WeightedTreeEntry> selectedEntries = new ArrayList<>();
        Multimap<Pair<IWeightedTreeProvider<@NonNull N, E, @NonNull T>, E>, Pair<Integer, Long>> requested = HashMultimap.create();
        for (Long id : selected) {
            WeightedTreeEntry entry = fCgEntries.get(id);
            if (entry != null) {
                selectedEntries.add(entry);
                requested.put(new Pair<>(entry.fCallgraph, entry.fElement), new Pair<>(entry.fDepth, id));
            }
        }

        // Prepare the regexes
        Map<@NonNull Integer, @NonNull Predicate<@NonNull Multimap<@NonNull String, @NonNull Object>>> predicates = new HashMap<>();
        Multimap<@NonNull Integer, @NonNull String> regexesMap = DataProviderParameterUtils.extractRegexFilter(fetchParameters);
        if (regexesMap != null) {
            predicates.putAll(computeRegexPredicate(regexesMap));
        }

        if (subMonitor.isCanceled()) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
        }

        // For each element and callgraph, get the states
        for (Pair<IWeightedTreeProvider<@NonNull N, E, @NonNull T>, E> element : requested.keySet()) {
            if (subMonitor.isCanceled()) {
                return new TmfModelResponse<>(null, ITmfResponse.Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            Collection<Pair<Integer, Long>> depths = requested.get(element);
            rowModels.addAll(getStatesForElement(times, predicates, subMonitor, element.getFirst(), element.getSecond(), depths));
        }

        return new TmfModelResponse<>(new TimeGraphModel(rowModels), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    private List<ITimeGraphRowModel> getStatesForElement(List<Long> times, Map<Integer, Predicate<Multimap<String, Object>>> predicates, IProgressMonitor monitor,
            IWeightedTreeProvider<@NonNull N, E, @NonNull T> provider, E e,
            Collection<Pair<Integer, Long>> depths) {
        // Get the cct for this element (first level callsites) and sort them
        Collection<T> cct = provider.getTreesFor(e);
        List<T> sortedCct = new ArrayList<>(cct);
        sortedCct.sort(CCT_COMPARATOR);

        // Maps a depth with a pair of entry ID and list of states
        Map<Integer, Pair<Long, List<ITimeGraphState>>> depthIds = new HashMap<>();
        int maxDepth = 0;
        long maxEndTime = 0;
        for (Pair<Integer, Long> depth : depths) {
            maxDepth = Math.max(depth.getFirst(), maxDepth);
            Long endTime = fEndTimes.get(depth.getSecond());
            maxEndTime = endTime != null ? Math.max(endTime, maxEndTime) : maxEndTime;
            depthIds.put(depth.getFirst(), new Pair<>(depth.getSecond(), new ArrayList<>()));
        }

        long currentWeightTime = 0;
        // Start parsing the callgraph
        for (T callsite : sortedCct) {
            if (timeOverlap(currentWeightTime, callsite.getWeight(), times)) {
                recurseAddCallsite(callsite, currentWeightTime, predicates, times, depthIds, 0, maxDepth, monitor);
            }
            currentWeightTime += callsite.getWeight();
        }

        // We may need to fill with null after the last callsite
        if (maxEndTime > currentWeightTime && timeOverlap(currentWeightTime, maxEndTime - currentWeightTime, times)) {
            fillDeeperWithNull(0, maxDepth, depthIds, currentWeightTime, maxEndTime - currentWeightTime);
        }

        List<ITimeGraphRowModel> rowModels = new ArrayList<>();
        for (Pair<Long, List<ITimeGraphState>> states : depthIds.values()) {
            rowModels.add(new TimeGraphRowModel(states.getFirst(), states.getSecond()));
        }
        return rowModels;
    }

    // Recursively adds this callsite states and its children
    private void recurseAddCallsite(@NonNull T weightedTree, long stateStartTime,
            Map<Integer, Predicate<Multimap<String, Object>>> predicates,
            List<Long> times, Map<Integer, Pair<Long, List<ITimeGraphState>>> depthIds,
            int depth, int maxDepth, IProgressMonitor monitor) {
        if (monitor.isCanceled()) {
            return;
        }
        // Add the state if current depth is requested
        Pair<Long, List<ITimeGraphState>> stateList = depthIds.get(depth);
        if (stateList != null) {
            ITimeGraphState timeGraphState = createTimeGraphState(weightedTree, stateStartTime);
            applyFilterAndAddState(stateList.getSecond(), timeGraphState, stateList.getFirst(), predicates, monitor);
        }
        // Stop recursing if there's no more depth requested or if depth is -1
        if (depth >= maxDepth) {
            return;
        }

        /* We can fill with null states all depth deeper than the current site's max depth. Max depth includes the current element, so we -1 */
        int thisMaxDepth = depth + weightedTree.getMaxDepth() - 1;
        fillDeeperWithNull(thisMaxDepth, maxDepth, depthIds, stateStartTime, weightedTree.getWeight());

        // Get and sort the children
        List<WeightedTree<@NonNull N>> children = new ArrayList<>(weightedTree.getChildren());
        if (children.isEmpty()) {
            return;
        }
        children.sort(CCT_COMPARATOR2);

        // Add the required children
        long weightTime = stateStartTime;
        for (WeightedTree<@NonNull N> child : children) {
            if (timeOverlap(weightTime, child.getWeight(), times)) {
                recurseAddCallsite((T) child, weightTime, predicates, times, depthIds, depth + 1, thisMaxDepth, monitor);
            }
            weightTime += child.getWeight();
        }
        // We may need to fill the remaining data with null states
        if (weightedTree.getWeight() > weightTime - stateStartTime && timeOverlap(weightTime, weightedTree.getWeight() - (weightTime - stateStartTime), times)) {
            fillDeeperWithNull(depth, thisMaxDepth, depthIds, weightTime, weightedTree.getWeight() - (weightTime - stateStartTime));
        }

    }

    private static void fillDeeperWithNull(int depth, int depthLimit, Map<Integer, Pair<Long, List<ITimeGraphState>>> depthIds, long time, long duration) {
        if (depthLimit <= depth) {
            return;
        }
        /* Fill with null time graph states all entries deeper than depth */
        for (Entry<Integer, Pair<Long, List<ITimeGraphState>>> depthEntry : depthIds.entrySet()) {
            if (depthEntry.getKey() > depth && depthEntry.getKey() <= depthLimit) {
                depthEntry.getValue().getSecond().add(new TimeGraphState(time, duration, Integer.MIN_VALUE));
            }
        }
    }

    private ITimeGraphState createTimeGraphState(@NonNull T weightedTree, long currentWeightTime) {
        @NonNull N value = weightedTree.getObject();
        TimeGraphState state = new TimeGraphState(currentWeightTime, weightedTree.getWeight(), value.hashCode(), fWtProvider.toDisplayString(weightedTree));
        state.setStyle(fPalette.getStyleFor(weightedTree));
        return state;
    }

    /** Verify if one of the requested time overlaps this callsite */
    private static boolean timeOverlap(long start, long duration, List<Long> times) {
        long end = start + duration;
        for (Long time : times) {
            if (time >= start && time <= end) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphArrow>> fetchArrows(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(Collections.emptyList(), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> fetchTooltip(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        List<Long> times = DataProviderParameterUtils.extractTimeRequested(fetchParameters);
        if (times == null || times.size() != 1) {
            return new TmfModelResponse<>(Collections.emptyMap(), ITmfResponse.Status.FAILED, "Invalid time requested for tooltip"); //$NON-NLS-1$
        }
        List<Long> items = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
        if (items == null || items.size() != 1) {
            return new TmfModelResponse<>(Collections.emptyMap(), ITmfResponse.Status.FAILED, "Invalid selection requested for tooltip"); //$NON-NLS-1$
        }
        Long time = times.get(0);
        Long item = items.get(0);
        WeightedTreeEntry callGraphEntry = fCgEntries.get(item);
        if (callGraphEntry == null) {
            return new TmfModelResponse<>(Collections.emptyMap(), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }
        WeightedTree<@NonNull N> callSite = findCallSite((Collection<WeightedTree<@NonNull N>>) callGraphEntry.fCallgraph.getTreesFor(callGraphEntry.fElement), time, callGraphEntry.fDepth, 0, 0);
        if (callSite != null) {
            return new TmfModelResponse<>(getTooltip(callSite), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
        }

        return new TmfModelResponse<>(Collections.emptyMap(), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    private Map<String, String> getTooltip(WeightedTree<@NonNull N> callSite) {
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        MetricType metric = fWtProvider.getWeightType();
        builder.put(metric.getTitle(), String.valueOf(callSite.getWeight()));
        List<MetricType> additionalMetrics = fWtProvider.getAdditionalMetrics();
        for (int i = 0; i < additionalMetrics.size(); i++) {
            MetricType addMetric = additionalMetrics.get(i);
            // TODO Find a way to get the statistics when available
            builder.put(addMetric.getTitle(), String.valueOf(fWtProvider.getAdditionalMetric((T) callSite, i)));
        }
        return builder.build();
    }

    /** Find the callsite at the time and depth requested */
    private @Nullable WeightedTree<@NonNull N> findCallSite(Collection<WeightedTree<N>> collection, Long time, int depth, long currentTime, int currentDepth) {
        List<WeightedTree<N>> cct = new ArrayList<>(collection);
        cct.sort(CCT_COMPARATOR2);
        long weight = currentTime;
        for (WeightedTree<N> callsite : cct) {
            if (weight + callsite.getWeight() < time) {
                weight += callsite.getWeight();
                continue;
            }
            // This is the right callsite, let's check the depth
            if (currentDepth == depth) {
                return callsite;
            }
            return findCallSite(callsite.getChildren(), time, depth, weight, currentDepth + 1);
        }
        return null;
    }

    @Deprecated
    @Override
    public TmfModelResponse<List<FlameChartEntryModel>> fetchTree(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.timeQueryToMap(filter);
        TmfModelResponse<@NonNull TmfTreeModel<@NonNull FlameChartEntryModel>> response = fetchTree(parameters, monitor);
        TmfTreeModel<@NonNull FlameChartEntryModel> model = response.getModel();
        List<FlameChartEntryModel> treeModel = null;
        if (model != null) {
            treeModel = model.getEntries();
        }
        return new TmfModelResponse<>(treeModel, response.getStatus(),
                response.getStatusMessage());
    }

    @Deprecated
    @Override
    public TmfModelResponse<List<ITimeGraphRowModel>> fetchRowModel(@NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(filter);
        TmfModelResponse<TimeGraphModel> response = fetchRowModel(parameters, monitor);
        TimeGraphModel model = response.getModel();
        List<ITimeGraphRowModel> rows = null;
        if (model != null) {
            rows = model.getRows();
        }
        return new TmfModelResponse<>(rows, response.getStatus(), response.getStatusMessage());
    }

    @Deprecated
    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphArrow>> fetchArrows(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.timeQueryToMap(filter);
        return fetchArrows(parameters, monitor);
    }

    @Deprecated
    @Override
    public @NonNull TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> fetchTooltip(@NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(filter);
        return fetchTooltip(parameters, monitor);
    }

    @Override
    public TmfModelResponse<OutputStyleModel> fetchStyle(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(new OutputStyleModel(fPalette.getStyles()), ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

}

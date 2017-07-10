/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.ui.flamegraph;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.GroupNode;
import org.eclipse.tracecompass.incubator.callstack.core.callstack.ICallStackElement;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.AggregatedCallSite;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.LeafGroupNode;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphContentProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;

/**
 * Content provider for the flame graph view
 *
 * @author Sonia Farrah
 *
 */
public class FlameGraphContentProvider implements ITimeGraphContentProvider {

    private final List<TimeGraphEntry> fFlameGraphEntries = new ArrayList<>();

    private SortOption fSortOption = SortOption.BY_NAME;
    private Comparator<TimeGraphEntry> fThreadComparator = new ThreadNameComparator();

    private final Map<ICallStackElement, TimeGraphEntry> fLevelEntries = new HashMap<>();

    /**
     * Parse the aggregated tree created by the callGraphAnalysis and creates
     * the event list (functions) for each entry (depth)
     *
     * @param firstNode
     *            The first node of the aggregation tree
     * @param childrenEntries
     *            The list of entries for one thread
     * @param timestampStack
     *            A stack used to save the functions timeStamps
     */
    private void setData(AggregatedCallSite firstNode, List<FlamegraphDepthEntry> childrenEntries, Deque<Long> timestampStack) {
        long lastEnd = timestampStack.peek();
        for (int i = 0; i <= firstNode.getMaxDepth() - 1; i++) {
            if (i >= childrenEntries.size()) {
                FlamegraphDepthEntry entry = new FlamegraphDepthEntry(String.valueOf(i), 0, firstNode.getLength(), i, String.valueOf(i));
                childrenEntries.add(entry);
            }
            childrenEntries.get(i).updateEndTime(lastEnd + firstNode.getLength());
        }
        FlamegraphDepthEntry firstEntry = checkNotNull(childrenEntries.get(0));
        firstEntry.addEvent(new FlamegraphEvent(firstEntry, lastEnd, firstNode));
        // Build the event list for next entries (next depth)
        addEvent(firstNode, childrenEntries, timestampStack, 1);
        timestampStack.pop();
    }

    /**
     * Build the events list for an entry (depth), then creates recursively the
     * events for the next entries. This parses the aggregation tree starting
     * from the bottom. This uses a stack to save the timestamp for each
     * function. Once we save a function's timestamp we'll use it to create the
     * callees events.
     *
     * @param node
     *            The node of the aggregation tree
     * @param childrenEntries
     *            The list of entries for one thread
     * @param timestampStack
     *            A stack used to save the functions timeStamps
     */
    private void addEvent(AggregatedCallSite node, List<FlamegraphDepthEntry> childrenEntries, Deque<Long> timestampStack, int depth) {
        node.getChildren().stream()
                .sorted(Comparator.comparingLong(AggregatedCallSite::getLength))
                .forEach(child -> {
                    addEvent(child, childrenEntries, timestampStack, depth + 1);
                });
        node.getChildren().stream().forEach(child -> {
            timestampStack.pop();
        });

        FlamegraphDepthEntry entry = checkNotNull(childrenEntries.get(depth - 1));
        // Create the event corresponding to the function using the caller's
        // timestamp
        entry.addEvent(new FlamegraphEvent(entry, timestampStack.peek(), node));
        timestampStack.push(timestampStack.peek() + node.getLength());
    }

    @Override
    public boolean hasChildren(@Nullable Object element) {
        return !fFlameGraphEntries.isEmpty();
    }

    @Override
    public ITimeGraphEntry[] getElements(@Nullable Object inputElement) {
        fFlameGraphEntries.clear();
        fLevelEntries.clear();
        // Get the root of each thread
        if (inputElement instanceof Collection<?>) {
            Collection<?> threadNodes = (Collection<?>) inputElement;
            for (Object object : threadNodes) {
                if (object instanceof GroupNode) {
                    buildChildrenEntries((GroupNode) object, null);
                }
            }
        } else {
            return new ITimeGraphEntry[0];
        }

        // Sort the threads
        fFlameGraphEntries.sort(fThreadComparator);
        return fFlameGraphEntries.toArray(new ITimeGraphEntry[fFlameGraphEntries.size()]);
    }

    /**
     * Build the entry list for one thread
     *
     * @param groupNode
     *            The node of the aggregation tree
     */
    private void buildChildrenEntries(GroupNode groupNode, @Nullable TimeGraphEntry parent) {
        // Add the entry
        TimeGraphEntry groupEntry = new TimeGraphEntry(groupNode.getName(), 0L, 0L);
        if (parent != null) {
            parent.addChild(groupEntry);
        } else {
            fFlameGraphEntries.add(groupEntry);
        }

        // Create the children entries
        for (GroupNode child : groupNode.getChildren()) {
            buildChildrenEntries(child, groupEntry);
        }

        // Create the callsites entries
        if (!(groupNode instanceof LeafGroupNode)) {
            return;
        }
        LeafGroupNode leaf = (LeafGroupNode) groupNode;

        List<FlamegraphDepthEntry> childrenEntries = new ArrayList<>();
        Deque<Long> timestampStack = new ArrayDeque<>();
        timestampStack.push(0L);

        // Sort children by duration
        leaf.getAggregatedData().stream()
                .sorted(Comparator.comparingLong(AggregatedCallSite::getLength))
                .forEach(rootFunction -> {
                    setData(rootFunction, childrenEntries, timestampStack);
                    long currentThreadDuration = timestampStack.pop() + rootFunction.getLength();
                    timestampStack.push(currentThreadDuration);
                });
        childrenEntries.forEach(child -> {
            groupEntry.addChild(child);
        });
        groupEntry.updateEndTime(timestampStack.pop());
        return;




    }

    @Override
    public ITimeGraphEntry[] getChildren(@Nullable Object parentElement) {
        return fFlameGraphEntries.toArray(new TimeGraphEntry[fFlameGraphEntries.size()]);
    }

    @Override
    public @Nullable ITimeGraphEntry getParent(@Nullable Object element) {
        // Do nothing
        return null;
    }

    @Override
    public void dispose() {
        // Do nothing
    }

    @Override
    public void inputChanged(@Nullable Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
        // Do nothing
    }

    /**
     * Get the sort option
     *
     * @return the sort option.
     */
    public SortOption getSortOption() {
        return fSortOption;
    }

    /**
     * Set the sort option for sorting the thread entries
     *
     * @param sortOption
     *            the sort option to set
     *
     */
    public void setSortOption(SortOption sortOption) {
        fSortOption = sortOption;
        switch (sortOption) {
        case BY_NAME:
            fThreadComparator = new ThreadNameComparator();
            break;
        case BY_NAME_REV:
            fThreadComparator = checkNotNull(new ThreadNameComparator().reversed());
            break;
        case BY_ID:
            fThreadComparator = new ThreadIdComparator();
            break;
        case BY_ID_REV:
            fThreadComparator = checkNotNull(new ThreadIdComparator().reversed());
            break;
        default:
            break;
        }
    }
}

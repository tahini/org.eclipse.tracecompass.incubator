/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.callgraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.tracecompass.incubator.callstack.core.base.CallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.internal.callstack.core.base.AllGroupDescriptor;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * A class containing helper methods to group aggregated callgraph data by the
 * different available groups
 *
 * @author Geneviève Bastien
 */
public final class CallGraphGroupBy {

    private CallGraphGroupBy() {
        // Nothing to do
    }

    /**
     * Group callgraph groups by one of the descriptor.
     *
     * @param groupBy
     *            The group descriptor by which to group the call graph elements.
     * @param elements
     *            The full expanded data from the groups
     * @param cgProvider
     *            The call graph data provider
     * @return A collection of data that is the result of the grouping by the
     *         descriptor
     */
    public static Multimap<ICallStackElement, AggregatedCallSite> groupCallGraphBy(ICallStackGroupDescriptor groupBy, Collection<ICallStackElement> elements, ICallGraphProvider cgProvider) {
        // Fast return: just aggregated all groups together
        if (groupBy.equals(AllGroupDescriptor.getInstance())) {
            return groupCallGraphByAll(elements, cgProvider);
        }

        Multimap<ICallStackElement, AggregatedCallSite> grouped = HashMultimap.create();
        elements.forEach(g -> grouped.putAll(searchForGroups(g, groupBy, cgProvider)));
        return grouped;
    }

    private static void mergeCallsites(
            Map<Object, AggregatedCallSite> map, Collection<AggregatedCallSite> toMerge) {
        toMerge.forEach(acs -> {
            AggregatedCallSite mergeTo = map.get(acs.getSymbol());
            if (mergeTo != null) {
                mergeTo.merge(acs);
            } else {
                map.put(acs.getSymbol(), acs);
            }
        });
    }

    private static Collection<AggregatedCallSite> addGroupData(ICallStackElement srcGroup, ICallStackElement dstGroup, ICallGraphProvider cgProvider) {
        Map<Object, AggregatedCallSite> callsiteMap = new HashMap<>();
        mergeCallsites(callsiteMap, cgProvider.getCallingContextTree(srcGroup));
        srcGroup.getChildren().forEach(group -> {
            Collection<AggregatedCallSite> groupData = addGroupData(group, dstGroup, cgProvider);
            mergeCallsites(callsiteMap, groupData);
        });
        return callsiteMap.values();
    }

    private static Multimap<ICallStackElement, AggregatedCallSite> groupCallGraphByAll(Collection<ICallStackElement> groups, ICallGraphProvider cgProvider) {
        // Fast return: just aggregate all groups together
        ICallStackElement allGroup = new CallStackElement("All", AllGroupDescriptor.getInstance(), null, null);
        Map<Object, AggregatedCallSite> callsiteMap = new HashMap<>();
        groups.forEach(g -> {
            Collection<AggregatedCallSite> groupData = addGroupData(g, allGroup, cgProvider);
            mergeCallsites(callsiteMap, groupData);
        });
        HashMultimap<ICallStackElement, AggregatedCallSite> map = HashMultimap.create();
        map.putAll(allGroup, callsiteMap.values());
        return map;
    }

    private static Multimap<ICallStackElement, AggregatedCallSite> searchForGroups(ICallStackElement element, ICallStackGroupDescriptor descriptor, ICallGraphProvider cgProvider) {
        HashMultimap<ICallStackElement, AggregatedCallSite> map = HashMultimap.create();
        if (element.getGroup().equals(descriptor)) {
            ICallStackElement groupedElement = new CallStackElement(element.getName(), descriptor);
            map.putAll(groupedElement, addGroupData(element, groupedElement, cgProvider));
            return map;
        }
        ICallStackGroupDescriptor nextGroup = descriptor.getNextGroup();
        if (nextGroup == null) {
            map.putAll(element, cgProvider.getCallingContextTree(element));
            return map;
        }
        element.getChildren().forEach(g -> map.putAll(searchForGroups(g, nextGroup, cgProvider)));
        return map;
    }

}

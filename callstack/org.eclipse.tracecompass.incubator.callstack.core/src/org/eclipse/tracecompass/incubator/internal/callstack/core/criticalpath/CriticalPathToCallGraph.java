/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.criticalpath;

import java.util.Collection;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.graph.core.base.IGraphWorker;
import org.eclipse.tracecompass.analysis.graph.core.base.ITmfGraphVisitor;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfEdge.EdgeType;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.base.TmfVertex;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.WeightedTree;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.IWeightedTreeProvider;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;

import com.google.common.collect.ImmutableList;

/**
 *
 *
 * @author Geneviève Bastien
 */
public class CriticalPathToCallGraph implements IWeightedTreeProvider<Object, WeightedTree<Object>> {

    private static final String ALL_SUFFIX = "_all"; //$NON-NLS-1$

    private WeightedTree<Object> fAggregatedTree;
    private WeightedTree<Object> fTree;

    private class graphToCallGraphConverter implements ITmfGraphVisitor {

        private final TmfGraph fGraph;
        private final IGraphWorker fMainWorker;

        public graphToCallGraphConverter(IGraphWorker mainWorker, TmfGraph graph) {
            fGraph = graph;
            fMainWorker = mainWorker;
        }

        @Override
        public void visitHead(TmfVertex vertex) {
            // Nothing to do

        }

        @Override
        public void visit(TmfVertex vertex) {
            // Nothing to do
        }

        @Override
        public void visit(TmfEdge edge, boolean horizontal) {
            addEdgeToElement(edge);
            addEdgeToAggregatedElement(edge);
        }

        private void addEdgeToAggregatedElement(TmfEdge edge) {
            // Get the worker to which to attribute this edge, whether vertical
            // or horizontal
            IGraphWorker worker = fGraph.getParentOf(edge.getVertexTo());
            if (worker == null) {
                return;
            }

            // If it's another worker that is running, add a other process running state
            if (worker != fMainWorker && edge.getType().equals(EdgeType.RUNNING)) {
                WeightedTree<Object> callSite = new WeightedTree<>("Other process"); //$NON-NLS-1$
                callSite.addToWeight(edge.getDuration());
                fAggregatedTree.addChild(callSite);
                return;
            }

            // Otherwise, add a first level call that corresponds to the worker
            WeightedTree<Object> callSite = new WeightedTree<>(edge.getType());
            callSite.addToWeight(edge.getDuration());
            fAggregatedTree.addChild(callSite);

        }

        private void addEdgeToElement(TmfEdge edge) {
            // Get the worker to which to attribute this edge, whether vertical
            // or horizontal
            IGraphWorker worker = fGraph.getParentOf(edge.getVertexTo());
            if (worker == null) {
                return;
            }

            // If it is the main worker, just add a 1st level call of the edge
            // type
            if (worker == fMainWorker) {
                WeightedTree<Object> callSite = new WeightedTree<>(edge.getType());
                callSite.addToWeight(edge.getDuration());
                fTree.addChild(callSite);
                return;
            }

            // Otherwise, add a first level call that corresponds to the worker
            WeightedTree<Object> callSite = new WeightedTree<>(String.valueOf(worker));
            callSite.addToWeight(edge.getDuration());

            // Then, add a second level for the edge type if it is not running
            if (!edge.getType().equals(EdgeType.RUNNING)) {
                WeightedTree<Object> childType = new WeightedTree<>(edge.getType());
                childType.addToWeight(edge.getDuration());
                callSite.addChild(childType);
            }
            fTree.addChild(callSite);
        }

    }

    /**
     * @param graph
     */
    public CriticalPathToCallGraph(@Nullable TmfGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Bad graph"); //$NON-NLS-1$
        }
        TmfVertex head = graph.getHead();
        if (head == null) {
            throw new NullPointerException("Empty graph"); //$NON-NLS-1$
        }

        IGraphWorker worker = graph.getParentOf(head);
        if (worker == null) {
            throw new NullPointerException("head vertex has no parent"); //$NON-NLS-1$
        }
        fTree = new WeightedTree<>(String.valueOf(worker));
        fAggregatedTree = new WeightedTree<>(String.valueOf(worker) + ALL_SUFFIX);
        graphToCallGraphConverter converter = new graphToCallGraphConverter(worker, graph);
        graph.scanLineTraverse(worker, converter);
    }

    @Override
    public Collection<WeightedTree<Object>> getTrees() {
        return ImmutableList.of(fTree, fAggregatedTree);
    }

    @Override
    public Collection<WeightedTree<Object>> getTrees(ITmfTimestamp fromNanos, ITmfTimestamp fromNanos2) {
        return ImmutableList.of(fTree, fAggregatedTree);
    }

//    @Override
//    public Collection<ICallStackGroupDescriptor> getGroupDescriptors() {
//        return DESCRIPTORS;
//    }
//
//    @Override
//    public CallGraph getCallGraph(ITmfTimestamp start, ITmfTimestamp end) {
//        return EMPTY_CALLGRAPH;
//    }
//
//    @Override
//    public CallGraph getCallGraph() {
//        return fCallGraph;
//    }


//    @Override
//    public List<@NonNull Long> histogramQuery(long @Nullable [] timeRequested) {
//        return Collections.emptyList();
//    }
//
//    @Override
//    public long getEventsTotal() {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    public @Nullable Collection<AggregatedCallSite> getRootSites(boolean aggregated) {
//        CallGraph callGraph = fCallGraph;
//        Collection<ICallStackElement> elements = fCallGraph.getElements();
//        if (elements.isEmpty()) {
//            return null;
//        }
//        Iterator<ICallStackElement> iterator = elements.iterator();
//        ICallStackElement element = iterator.next();
//        if (aggregated && !element.getName().endsWith(ALL_SUFFIX) && iterator.hasNext()) {
//            element = iterator.next();
//        } else if (!aggregated && element.getName().endsWith(ALL_SUFFIX) && iterator.hasNext()) {
//            element = iterator.next();
//        }
//
//        return callGraph.getCallingContextTree(element).getChildren();
//    }
//
//    @Override
//    public Map<String, Long> getEventTypesTotal() {
//        Collection<AggregatedCallSite> rootSites = getRootSites(false);
//        if (rootSites == null) {
//            return Collections.emptyMap();
//        }
//        Map<String, Long> map = new HashMap<>();
//        for (AggregatedCallSite callsite : rootSites) {
//            map.put(String.valueOf(callsite.getObject()), callsite.getWeight());
//        }
//        return map;
//    }
//
//    @Override
//    public long getEventsInRange(long start, long end) {
//        return 0;
//    }
//
//    @Override
//    public @NonNull Map<@NonNull String, @NonNull Long> getEventTypesInRange(long start, long end) {
//        boolean aggregated = true;
//        if (start == Long.MIN_VALUE) {
//            aggregated = false;
//        }
//        Collection<AggregatedCallSite> rootSites = getRootSites(aggregated);
//        if (rootSites == null) {
//            return Collections.emptyMap();
//        }
//        Map<String, Long> map = new HashMap<>();
//        for (AggregatedCallSite callsite : rootSites) {
//            map.put(String.valueOf(callsite.getObject()), callsite.getWeight());
//        }
//        return map;
//    }

//    @Override
//    public void dispose() {
//
//    }
//
//    @Override
//    public long getEventsTotal() {
//        // TODO Auto-generated method stub
//        return 0;
//    }

//    @Override
//    public Map<String, Long> getEventTypesTotal() {
//        // TODO Auto-generated method stub
//        return null;
//    }
//
//    @Override
//    public long getEventsInRange(long start, long end) {
//        // TODO Auto-generated method stub
//        return 0;
//    }
//
//    @Override
//    public Map<String, Long> getEventTypesInRange(long start, long end) {
//        // TODO Auto-generated method stub
//        return null;
//    }

}

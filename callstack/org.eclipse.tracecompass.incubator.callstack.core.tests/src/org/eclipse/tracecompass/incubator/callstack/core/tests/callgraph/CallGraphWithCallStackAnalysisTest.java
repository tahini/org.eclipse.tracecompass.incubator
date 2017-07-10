/*******************************************************************************
 * Copyright (c) 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.tests.callgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.ICpuTimeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.GroupNode;
import org.eclipse.tracecompass.incubator.callstack.core.tests.callstack.CallStackTestBase;
import org.eclipse.tracecompass.incubator.callstack.core.tests.stubs.CallStackAnalysisStub;
import org.eclipse.tracecompass.incubator.internal.analysis.core.model.CompositeHostModel;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.AggregatedCallSite;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.LeafGroupNode;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.instrumented.AggregatedCalledFunction;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.instrumented.CallGraphAnalysis;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.junit.After;
import org.junit.Test;

/**
 * Test the callgraph analysis with the call stack trace and module
 *
 * @author Geneviève Bastien
 */
public class CallGraphWithCallStackAnalysisTest extends CallStackTestBase {

    /**
     *
     */
    @After
    public void cleanUp() {
        // Model objects use weak hash map, we garbage-collect here to make sure
        // there are no artefacts in memory
        System.gc();
    }

    /**
     * Test tha callgraph with a small trace
     */
    @Test
    public void testCallGraph() {
        CallStackAnalysisStub cga = getModule();

        try {
            Collection<GroupNode> groups = cga.getGroups();
            for (GroupNode group : groups) {
                String firstLevelName = group.getName();
                switch (firstLevelName) {
                case "1":
                    // Make sure the symbol key is correctly resolved
                    verifyProcess1(group);
                    break;
                case "5":
                    // Make sure the symbol key is correctly resolved
                    verifyProcess5(group);
                    break;
                default:
                    fail("Unknown process in callstack");
                }
            }
        } finally {
            cga.dispose();
        }
    }

    private static void verifyProcess1(GroupNode group) {
        Collection<GroupNode> secondLevels = group.getChildren();
        assertEquals(2, secondLevels.size());
        for (GroupNode secondLevel : secondLevels) {
            String secondLevelName = secondLevel.getName();
            assertEquals(1, secondLevel.getChildren().size());
            GroupNode thirdLevel = secondLevel.getChildren().iterator().next();
            assertTrue(thirdLevel instanceof LeafGroupNode);
            Collection<AggregatedCallSite> children = ((LeafGroupNode) thirdLevel).getAggregatedData();
            switch (secondLevelName) {
            case "2":
                assertEquals(2, children.size());
                for (AggregatedCallSite child : children) {
                    assertTrue(child instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction func = (AggregatedCalledFunction) child;
                    switch ((String) func.getSymbol()) {
                    case "op1":
                        assertEquals(9, func.getDuration());
                        assertEquals(5, func.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, func.getCpuTime());
                        assertEquals(1, func.getNbCalls());
                        assertEquals(1, func.getProcessId());
                        assertEquals(1, func.getChildren().size());
                        AggregatedCalledFunction next = (AggregatedCalledFunction) func.getChildren().iterator().next();
                        assertNotNull(next);
                        assertEquals(4, next.getDuration());
                        assertEquals(3, next.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals("op2", next.getSymbol());
                        assertEquals(1, next.getChildren().size());
                        AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                        assertNotNull(third);
                        assertEquals(1, third.getDuration());
                        assertEquals(1, third.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, third.getCpuTime());
                        assertEquals(1, third.getNbCalls());
                        assertEquals(1, third.getProcessId());
                        assertEquals("op3", third.getSymbol());
                        assertEquals(0, third.getChildren().size());
                        break;
                    case "op4":
                        assertEquals(8, func.getDuration());
                        assertEquals(8, func.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, func.getCpuTime());
                        assertEquals(1, func.getNbCalls());
                        assertEquals(1, func.getProcessId());
                        assertEquals(0, func.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for thread 2" + func.getSymbol());
                    }
                }
                break;
            case "3":
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertEquals("op2", func.getSymbol());

                assertEquals(17, func.getDuration());
                assertEquals(10, func.getSelfTime());
                assertEquals(IHostModel.TIME_UNKNOWN, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(1, func.getProcessId());
                assertEquals(2, func.getChildren().size());
                for (AggregatedCallSite nextChild : func.getChildren()) {
                    assertTrue(nextChild instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction next = (AggregatedCalledFunction) nextChild;
                    switch (next.getSymbol().toString()) {
                    case "op3":
                        assertEquals(1, next.getDuration());
                        assertEquals(1, next.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals(0, next.getChildren().size());
                        break;
                    case "op2":
                        assertEquals(6, next.getDuration());
                        assertEquals(6, next.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals(0, next.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for thread 2" + func.getSymbol());
                    }
                }

                break;
            default:
                fail("Unknown process in callstack");
            }
        }
    }

    private static void verifyProcess5(GroupNode group) {
        Collection<GroupNode> secondLevels = group.getChildren();
        assertEquals(2, secondLevels.size());
        for (GroupNode secondLevel : secondLevels) {
            String secondLevelName = secondLevel.getName();
            assertEquals(1, secondLevel.getChildren().size());
            GroupNode thirdLevel = secondLevel.getChildren().iterator().next();
            assertTrue(thirdLevel instanceof LeafGroupNode);
            Collection<AggregatedCallSite> children = ((LeafGroupNode) thirdLevel).getAggregatedData();
            switch (secondLevelName) {
            case "6": {
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertNotNull(func);
                assertEquals("op1", func.getSymbol());
                assertEquals(19, func.getDuration());
                assertEquals(6, func.getSelfTime());
                assertEquals(IHostModel.TIME_UNKNOWN, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(5, func.getProcessId());
                assertEquals(2, func.getChildren().size());

                for (AggregatedCallSite nextChild : func.getChildren()) {
                    assertTrue(nextChild instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction next = (AggregatedCalledFunction) nextChild;
                    switch (next.getSymbol().toString()) {
                    case "op3":
                        assertEquals(5, next.getDuration());
                        assertEquals(3, next.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(5, next.getProcessId());
                        assertEquals("op3", next.getSymbol());
                        assertEquals(1, next.getChildren().size());
                        AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                        assertNotNull(third);
                        assertEquals(2, third.getDuration());
                        assertEquals(2, third.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, third.getCpuTime());
                        assertEquals(1, third.getNbCalls());
                        assertEquals(5, third.getProcessId());
                        assertEquals("op1", third.getSymbol());
                        assertEquals(0, third.getChildren().size());
                        break;
                    case "op4":
                        assertEquals(8, next.getDuration());
                        assertEquals(8, next.getSelfTime());
                        assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(5, next.getProcessId());
                        assertEquals("op4", next.getSymbol());
                        assertEquals(0, next.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for second level of tid 6");
                    }
                }
            }
                break;
            case "7": {
                /*
                 * pid1 --- tid2 1e1 ------------- 10x1 12e4------------20x |
                 * 3e2-------7x | 4e3--5x |-- tid3 3e2
                 * --------------------------------20x 5e3--6x 7e2--------13x
                 *
                 * pid5 --- tid6 1e1 -----------------------------------20x |
                 * 2e3 ---------7x 12e4------------20x | 4e1--6x |-- tid7 1e5
                 * -----------------------------------20x 2e2 +++ 6x 9e2 ++++
                 * 13x 15e2 ++ 19x 10e3 + 11x
                 */
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertNotNull(func);
                assertEquals("op5", func.getSymbol());
                assertEquals(19, func.getDuration());
                assertEquals(7, func.getSelfTime());
                assertEquals(IHostModel.TIME_UNKNOWN, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(5, func.getProcessId());
                assertEquals(1, func.getChildren().size());

                // Verify children
                Iterator<AggregatedCallSite> iterator = func.getChildren().iterator();
                AggregatedCalledFunction next = (AggregatedCalledFunction) iterator.next();
                assertNotNull(next);
                assertEquals(12, next.getDuration());
                assertEquals(11, next.getSelfTime());
                assertEquals(IHostModel.TIME_UNKNOWN, next.getCpuTime());
                assertEquals(3, next.getNbCalls());
                assertEquals(5, next.getProcessId());
                assertEquals("op2", next.getSymbol());
                assertEquals(1, next.getChildren().size());
                AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                assertNotNull(third);
                assertEquals(1, third.getDuration());
                assertEquals(1, third.getSelfTime());
                assertEquals(IHostModel.TIME_UNKNOWN, third.getCpuTime());
                assertEquals(1, third.getNbCalls());
                assertEquals(5, third.getProcessId());
                assertEquals("op3", third.getSymbol());
                assertEquals(0, third.getChildren().size());
            }
                break;
            default:
                fail("Unknown process in callstack");
            }
        }
    }

    private CallGraphAnalysis getCallGraphModule() throws TmfAnalysisException {
        CallGraphAnalysis cga = new CallGraphAnalysis();
        cga.setId(getModule().getId());
        cga.setTrace(getTrace());

        cga.schedule();
        cga.waitForCompletion();
        return cga;
    }

    /**
     * Test a callgraph with a callstack that provides CPU times
     *
     * @throws TmfAnalysisException
     *             Propagates exceptions
     */
    @Test
    public void testCallGraphWithCpuTime() throws TmfAnalysisException {
        IHostModel model = ModelManager.getModelFor(getTrace().getHostId());
        // Assign it to a variable because the model uses weak hash map, we
        // don't want it garbage-collected before the end of the test.
        ICpuTimeProvider cpuTimeProvider = new ICpuTimeProvider() {

            @Override
            public long getCpuTime(int tid, long start, long end) {
                // TID 7 was out of CPU from 3 to 4
                if (tid == 7) {
                    long beginTime = Math.max(start, 3);
                    long endTime = Math.min(end, 4);
                    if (endTime - beginTime > 0) {
                        return (end - start) - (endTime - beginTime);
                    }
                }
                // TID 3 was out of CPU from 8 to 11
                if (tid == 3) {
                    long beginTime = Math.max(start, 8);
                    long endTime = Math.min(end, 11);
                    if (endTime - beginTime > 0) {
                        return (end - start) - (endTime - beginTime);
                    }
                }
                // TID 2 was out of CPU from 13 to 18
                if (tid == 2) {
                    long beginTime = Math.max(start, 13);
                    long endTime = Math.min(end, 18);
                    if (endTime - beginTime > 0) {
                        return (end - start) - (endTime - beginTime);
                    }
                }
                return end - start;
            }

            @Override
            public @NonNull Collection<@NonNull String> getHostIds() {
                return Collections.singleton("callstack.xml");
            }

        };
        ((CompositeHostModel) model).setCpuTimeProvider(cpuTimeProvider);

        CallGraphAnalysis cga = getCallGraphModule();
        try {
            Collection<GroupNode> groups = cga.getGroups();
            for (GroupNode group : groups) {
                String firstLevelName = group.getName();
                switch (firstLevelName) {
                case "1":
                    // Make sure the symbol key is correctly resolved
                    verifyProcess1CpuTime(group);
                    break;
                case "5":
                    // Make sure the symbol key is correctly resolved
                    verifyProcess5CpuTime(group);
                    break;
                default:
                    fail("Unknown process in callstack");
                }
            }
        } finally {
            cga.dispose();
        }
    }

    private static void verifyProcess1CpuTime(GroupNode group) {
        Collection<GroupNode> secondLevels = group.getChildren();
        assertEquals(2, secondLevels.size());
        for (GroupNode secondLevel : secondLevels) {
            String secondLevelName = secondLevel.getName();
            assertEquals(1, secondLevel.getChildren().size());
            GroupNode thirdLevel = secondLevel.getChildren().iterator().next();
            assertTrue(thirdLevel instanceof LeafGroupNode);
            Collection<AggregatedCallSite> children = ((LeafGroupNode) thirdLevel).getAggregatedData();
            switch (secondLevelName) {
            case "2":
                assertEquals(2, children.size());
                for (AggregatedCallSite child : children) {
                    assertTrue(child instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction func = (AggregatedCalledFunction) child;
                    switch ((String) func.getSymbol()) {
                    case "op1":
                        assertEquals(9, func.getDuration());
                        assertEquals(5, func.getSelfTime());
                        assertEquals(9, func.getCpuTime());
                        assertEquals(1, func.getNbCalls());
                        assertEquals(1, func.getProcessId());
                        assertEquals(1, func.getChildren().size());
                        AggregatedCalledFunction next = (AggregatedCalledFunction) func.getChildren().iterator().next();
                        assertNotNull(next);
                        assertEquals(4, next.getDuration());
                        assertEquals(3, next.getSelfTime());
                        assertEquals(4, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals("op2", next.getSymbol());
                        assertEquals(1, next.getChildren().size());
                        AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                        assertNotNull(third);
                        assertEquals(1, third.getDuration());
                        assertEquals(1, third.getSelfTime());
                        assertEquals(1, third.getCpuTime());
                        assertEquals(1, third.getNbCalls());
                        assertEquals(1, third.getProcessId());
                        assertEquals("op3", third.getSymbol());
                        assertEquals(0, third.getChildren().size());
                        break;
                    case "op4":
                        assertEquals(8, func.getDuration());
                        assertEquals(8, func.getSelfTime());
                        assertEquals(3, func.getCpuTime());
                        assertEquals(1, func.getNbCalls());
                        assertEquals(1, func.getProcessId());
                        assertEquals(0, func.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for thread 2" + func.getSymbol());
                    }
                }
                break;
            case "3":
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertEquals("op2", func.getSymbol());

                assertEquals(17, func.getDuration());
                assertEquals(10, func.getSelfTime());
                assertEquals(14, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(1, func.getProcessId());
                assertEquals(2, func.getChildren().size());
                for (AggregatedCallSite nextChild : func.getChildren()) {
                    assertTrue(nextChild instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction next = (AggregatedCalledFunction) nextChild;
                    switch (next.getSymbol().toString()) {
                    case "op3":
                        assertEquals(1, next.getDuration());
                        assertEquals(1, next.getSelfTime());
                        assertEquals(1, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals(0, next.getChildren().size());
                        break;
                    case "op2":
                        assertEquals(6, next.getDuration());
                        assertEquals(6, next.getSelfTime());
                        assertEquals(3, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(1, next.getProcessId());
                        assertEquals(0, next.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for thread 2" + func.getSymbol());
                    }
                }

                break;
            default:
                fail("Unknown process in callstack");
            }
        }
    }

    private static void verifyProcess5CpuTime(GroupNode group) {
        Collection<GroupNode> secondLevels = group.getChildren();
        assertEquals(2, secondLevels.size());
        for (GroupNode secondLevel : secondLevels) {
            String secondLevelName = secondLevel.getName();
            assertEquals(1, secondLevel.getChildren().size());
            GroupNode thirdLevel = secondLevel.getChildren().iterator().next();
            assertTrue(thirdLevel instanceof LeafGroupNode);
            Collection<AggregatedCallSite> children = ((LeafGroupNode) thirdLevel).getAggregatedData();
            switch (secondLevelName) {
            case "6": {
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertNotNull(func);
                assertEquals("op1", func.getSymbol());
                assertEquals(19, func.getDuration());
                assertEquals(6, func.getSelfTime());
                assertEquals(19, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(5, func.getProcessId());
                assertEquals(2, func.getChildren().size());

                for (AggregatedCallSite nextChild : func.getChildren()) {
                    assertTrue(nextChild instanceof AggregatedCalledFunction);
                    AggregatedCalledFunction next = (AggregatedCalledFunction) nextChild;
                    switch (next.getSymbol().toString()) {
                    case "op3":
                        assertEquals(5, next.getDuration());
                        assertEquals(3, next.getSelfTime());
                        assertEquals(5, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(5, next.getProcessId());
                        assertEquals("op3", next.getSymbol());
                        assertEquals(1, next.getChildren().size());
                        AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                        assertNotNull(third);
                        assertEquals(2, third.getDuration());
                        assertEquals(2, third.getSelfTime());
                        assertEquals(2, third.getCpuTime());
                        assertEquals(1, third.getNbCalls());
                        assertEquals(5, third.getProcessId());
                        assertEquals("op1", third.getSymbol());
                        assertEquals(0, third.getChildren().size());
                        break;
                    case "op4":
                        assertEquals(8, next.getDuration());
                        assertEquals(8, next.getSelfTime());
                        assertEquals(8, next.getCpuTime());
                        assertEquals(1, next.getNbCalls());
                        assertEquals(5, next.getProcessId());
                        assertEquals("op4", next.getSymbol());
                        assertEquals(0, next.getChildren().size());
                        break;
                    default:
                        fail("Unknown symbol for second level of tid 6");
                    }
                }
            }
                break;
            case "7": {
                /*
                 * pid1 --- tid2 1e1 ------------- 10x1 12e4------------20x |
                 * 3e2-------7x | 4e3--5x |-- tid3 3e2
                 * --------------------------------20x 5e3--6x 7e2--------13x
                 *
                 * pid5 --- tid6 1e1 -----------------------------------20x |
                 * 2e3 ---------7x 12e4------------20x | 4e1--6x |-- tid7 1e5
                 * -----------------------------------20x 2e2 +++ 6x 9e2 ++++
                 * 13x 15e2 ++ 19x 10e3 + 11x
                 */
                assertEquals(1, children.size());
                AggregatedCalledFunction func = (AggregatedCalledFunction) children.iterator().next();
                assertNotNull(func);
                assertEquals("op5", func.getSymbol());
                assertEquals(19, func.getDuration());
                assertEquals(7, func.getSelfTime());
                assertEquals(18, func.getCpuTime());
                assertEquals(1, func.getNbCalls());
                assertEquals(5, func.getProcessId());
                assertEquals(1, func.getChildren().size());

                // Verify children
                Iterator<AggregatedCallSite> iterator = func.getChildren().iterator();
                AggregatedCalledFunction next = (AggregatedCalledFunction) iterator.next();
                assertNotNull(next);
                assertEquals(12, next.getDuration());
                assertEquals(11, next.getSelfTime());
                assertEquals(11, next.getCpuTime());
                assertEquals(3, next.getNbCalls());
                assertEquals(5, next.getProcessId());
                assertEquals("op2", next.getSymbol());
                assertEquals(1, next.getChildren().size());
                AggregatedCalledFunction third = (AggregatedCalledFunction) next.getChildren().iterator().next();
                assertNotNull(third);
                assertEquals(1, third.getDuration());
                assertEquals(1, third.getSelfTime());
                assertEquals(1, third.getCpuTime());
                assertEquals(1, third.getNbCalls());
                assertEquals(5, third.getProcessId());
                assertEquals("op3", third.getSymbol());
                assertEquals(0, third.getChildren().size());
            }
                break;
            default:
                fail("Unknown process in callstack");
            }
        }
    }

}

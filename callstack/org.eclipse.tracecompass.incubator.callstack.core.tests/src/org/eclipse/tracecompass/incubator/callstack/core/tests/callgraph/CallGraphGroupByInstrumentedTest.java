/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.tests.callgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.CallGraphGroupBy;
import org.eclipse.tracecompass.incubator.callstack.core.tests.flamechart.CallStackTestBase;
import org.eclipse.tracecompass.incubator.callstack.core.tests.stubs.CallStackAnalysisStub;
import org.junit.Test;

import com.google.common.collect.Iterables;

/**
 * Test the {@link CallGraphGroupBy} class
 *
 * @author Geneviève Bastien
 */
public class CallGraphGroupByInstrumentedTest extends CallStackTestBase {

    /**
     *
     */
    @Test
    public void testGroupByProcessInstrumented() {
        CallStackAnalysisStub cga = getModule();

        // The first group descriptor is the process
        Collection<ICallStackGroupDescriptor> groupDescriptors = cga.getGroupDescriptors();
        ICallStackGroupDescriptor processGroup = Iterables.getFirst(groupDescriptors, null);
        assertNotNull(processGroup);

    }

    /**
    *
    */
   @Test
   public void testGroupByThreadInstrumented() {
       CallStackAnalysisStub cga = getModule();

       // The first group descriptor is the process
       Collection<ICallStackGroupDescriptor> groupDescriptors = cga.getGroupDescriptors();
       ICallStackGroupDescriptor threadGroup = Iterables.getLast(groupDescriptors);
       assertNotNull(threadGroup);

       // Group by thread
       cga.setGroupBy(threadGroup);
       Collection<ICallStackElement> elements = cga.getElements();
       assertEquals(2, Iterables.size(elements));

   }

}

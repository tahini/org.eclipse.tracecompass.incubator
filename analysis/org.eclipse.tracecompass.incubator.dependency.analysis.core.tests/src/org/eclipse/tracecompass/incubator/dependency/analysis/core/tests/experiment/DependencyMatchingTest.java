/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.tracecompass.incubator.dependency.analysis.core.matching.SpanDependencyAnalysis;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.junit.Test;

/**
 * @author Geneviève Bastien
 *
 */
public class DependencyMatchingTest extends DependencyTestBase {

    /**
     * Test the results of the dependency analysis
     */
    @Test
    public void testDependencyAnalysis() {
        SpanDependencyAnalysis module = getModule();
        ISegmentStore<ISegment> segmentStore = module.getSegmentStore();
        assertNotNull(segmentStore);
        assertEquals(4, segmentStore.size());
    }

}

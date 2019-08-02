/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.concepts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics;

import com.google.common.collect.ImmutableMap;

/**
 *
 *
 * @author Geneviève Bastien
 */
public class AggregatedCallSite extends WeightedTree<ICallStackSymbol> {

    /**
     * Constructor
     *
     * @param symbol
     *            The symbol of the call site. It can eventually be resolved to
     *            a string using the symbol providers
     * @param initialLength
     *            The initial length of this object
     */
    public AggregatedCallSite(ICallStackSymbol symbol, long initialLength) {
        super(symbol, initialLength);
    }

    /**
     * Copy constructor
     *
     * @param copy
     *            The call site to copy
     */
    protected AggregatedCallSite(AggregatedCallSite copy) {
        super(copy);
    }

    /**
     * TODO: This is used in unit tests only, those should be updated instead
     *
     * Return the children as a collection of aggregatedCallSite
     * @return The children as callees
     */
    public Collection<AggregatedCallSite> getCallees() {
        List<AggregatedCallSite> list = new ArrayList<>();
        for (WeightedTree<ICallStackSymbol> child :getChildren()) {
            if (child instanceof AggregatedCallSite) {
                list.add((AggregatedCallSite) child);
            }
        }
        return list;
    }

    /**
     * Make a copy of this callsite, with its statistics. Implementing classes
     * should make sure they copy all fields of the callsite, including the
     * statistics.
     *
     * @return A copy of this aggregated call site
     */
    @Override
    public AggregatedCallSite copyOf() {
        return new AggregatedCallSite(this);
    }

    /**
     * Get additional statistics for this call site
     *
     * @return A map of statistics title with statistics
     */
    public Map<String, IStatistics<?>> getStatistics() {
        return ImmutableMap.of();
    }

    @Override
    public String toString() {
        return "CallSite: " + getObject(); //$NON-NLS-1$
    }

    /**
     * Get extra children sites that come with this callsite. For instance, an
     * instrumented callsite could return the kernel processes
     *
     * @return The extra children sites
     */
    public Collection<AggregatedCallSite> getExtraChildrenSites() {
        return Collections.emptyList();
    }

}

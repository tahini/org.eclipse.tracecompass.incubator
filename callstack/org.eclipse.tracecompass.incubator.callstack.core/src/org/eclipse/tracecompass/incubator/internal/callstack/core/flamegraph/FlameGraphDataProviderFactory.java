/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.internal.tmf.core.model.xy.TmfTreeXYCompositeDataProvider;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderFactory;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.Iterables;

/**
 * Factory for the flame chart data provider
 *
 * @author Geneviève Bastien
 */
public class FlameGraphDataProviderFactory implements IDataProviderFactory {

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(ITmfTrace trace) {
        // Need the analysis
        return null;
    }

    @Override
    public @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> createProvider(ITmfTrace trace, String secondaryId) {
        // Create with the trace or experiment first
        ITmfTreeDataProvider<? extends ITmfTreeDataModel> provider = create(trace, secondaryId);
        if (provider != null) {
            return provider;
        }
        // Otherwise, see if it's an experiment and create a composite if that's the
        // case
        Collection<ITmfTrace> traces = TmfTraceManager.getTraceSet(trace);
        if (traces.size() > 1) {
            // Try creating a composite only if there are many traces, otherwise, the
            // previous call to create should have returned the data provider
            return TmfTreeXYCompositeDataProvider.create(traces, Objects.requireNonNull(Messages.FlameGraphDataProvider_Title), FlameGraphDataProvider.ID, secondaryId);
        }
        return null;

    }

    private static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace, String secondaryId) {
        // The trace can be an experiment, so we need to know if there are multiple
        // analysis modules with the same ID
        Iterable<IWeightedTreeProvider> modules = TmfTraceUtils.getAnalysisModulesOfClass(trace, IWeightedTreeProvider.class);
        Iterable<IWeightedTreeProvider> filteredModules = Iterables.filter(modules, m -> ((IAnalysisModule) m).getId().equals(secondaryId));
        Iterator<IWeightedTreeProvider> iterator = filteredModules.iterator();
        if (iterator.hasNext()) {
            IWeightedTreeProvider<?, ?, ?> module = iterator.next();
            if (iterator.hasNext()) {
                // More than one module, must be an experiment, return null so the factory can
                // try with individual traces
                return null;
            }
            ((IAnalysisModule) module).schedule();
            return new FlameGraphDataProvider2(trace, module, secondaryId);
            //return module instanceof ICallGraphProvider ? new FlameGraphDataProvider(trace, (ICallGraphProvider) module, secondaryId) : new FlameGraphDataProvider2(trace, module, secondaryId);
        }
        return null;
    }

}

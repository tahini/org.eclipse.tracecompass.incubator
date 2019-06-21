/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.scripting.core.callstack;

import java.util.Collection;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTreeUtils;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff.DifferentialWeightedTree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff.DifferentialWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph.FlameGraphDataProvider2;
import org.eclipse.tracecompass.incubator.internal.scripting.core.data.provider.ScriptingDataProviderManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * @author gbastien
 * @param <E>
 */
public class CallStackScriptingModule {

    /**
     * @param <N>
     * @param <E>
     * @param <T>
     * @param first
     * @param second
     * @return
     */
    @WrapToScript
    public <@NonNull N, E> IWeightedTreeProvider<Object, String, DifferentialWeightedTree<Object>> diffTrees(IWeightedTreeProvider<Object, ?, WeightedTree<Object>> provider,  Collection<WeightedTree<Object>> first, Collection<WeightedTree<Object>> second) {
        Collection<DifferentialWeightedTree<Object>> diffTrees = WeightedTreeUtils.diffTrees(first, second);
        return new DifferentialWeightedTreeProvider(provider, diffTrees);
    }

    /**
     * @param <N>
     * @param <E>
     * @param <T>
     * @param provider
     * @return
     */
    @SuppressWarnings("restriction")
    @WrapToScript
    public <@NonNull N, E, @NonNull T extends WeightedTree<N>> FlameGraphDataProvider2<N, E, T> getFlameGraphDataProvider(ITmfTrace trace, IWeightedTreeProvider<N, E, T> provider, String id) {
         FlameGraphDataProvider2<@NonNull N, E, @NonNull T> dataProvider = new FlameGraphDataProvider2<>(trace, provider, ScriptingDataProviderManager.PROVIDER_ID + ':' + id);
         ScriptingDataProviderManager.getInstance().registerDataProvider(trace, dataProvider);
         return dataProvider;
    }

}

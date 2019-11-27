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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.AllGroupDescriptor;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeGroupDescriptor;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeSet;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTreeGroupBy;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTreeUtils;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff.DifferentialWeightedTree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff.DifferentialWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph.FlameGraphDataProvider;
import org.eclipse.tracecompass.incubator.internal.scripting.core.data.provider.ScriptingDataProviderManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * @author Geneviève Bastien
 */
public class CallStackScriptingModule {

    /**
     * @param <N>
     * @param <E>
     * @param provider
     * @param level
     * @return
     */
    @WrapToScript
    public <@NonNull N, E> IWeightedTreeSet<N, ?, WeightedTree<N>> groupTreesBy(IWeightedTreeProvider<N, E, WeightedTree<N>> provider, int level) {
        IWeightedTreeSet<N, E, WeightedTree<N>> treeSet = provider.getTreeSet();
        IWeightedTreeGroupDescriptor groupDescriptor = getGroupDescriptor(provider, level);
        if (groupDescriptor != null) {
            return WeightedTreeGroupBy.groupWeightedTreeBy(groupDescriptor, treeSet, provider);
        }
        return treeSet;
    }

    private static <@NonNull N> @Nullable IWeightedTreeGroupDescriptor getGroupDescriptor(IWeightedTreeProvider<N, ?, WeightedTree<N>> provider, int level) {
        IWeightedTreeGroupDescriptor groupDescriptor = provider.getGroupDescriptor();
        if (level == 0) {
            return AllGroupDescriptor.getInstance();
        }
        int i = 1;
        while (groupDescriptor != null && i < level) {
            groupDescriptor = groupDescriptor.getNextGroup();
            i++;
        }
        return groupDescriptor;
    }

    /**
     * @param first
     * @param second
     * @return
     */
    @WrapToScript
    public IWeightedTreeProvider<Object, Object, DifferentialWeightedTree<Object>> diffTrees(IWeightedTreeProvider<Object, ?, WeightedTree<Object>> provider, Collection<WeightedTree<Object>> first,
            Collection<WeightedTree<Object>> second) {
        Collection<DifferentialWeightedTree<Object>> diffTrees = WeightedTreeUtils.diffTrees(first, second);
        return new DifferentialWeightedTreeProvider<>(provider, diffTrees);
    }

    /**
     * @param provider The original weighted tree provider, whose values will be used for the metrics, palettes, etc
     *
     * @param first
     * @param second
     * @param <N>
     *            The type of data that goes in the trees
     * @return
     */
    @WrapToScript
    public <@NonNull N> @Nullable DifferentialWeightedTreeProvider<N> diffTreeSets(IWeightedTreeProvider<N, ?, WeightedTree<N>> provider,
            IWeightedTreeSet<N, @NonNull ?, WeightedTree<N>> first,
            IWeightedTreeSet<N, @NonNull ?, WeightedTree<N>> second) {
        DifferentialWeightedTreeProvider<@NonNull N> diffTrees = WeightedTreeUtils.diffTreeSets(provider, first, second);
        return diffTrees;
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
    public <@NonNull N, E, @NonNull T extends WeightedTree<N>> FlameGraphDataProvider<N, E, T> getFlameGraphDataProvider(ITmfTrace trace, IWeightedTreeProvider<N, E, T> provider, String id) {
        FlameGraphDataProvider<@NonNull N, E, @NonNull T> dataProvider = new FlameGraphDataProvider<>(trace, provider, ScriptingDataProviderManager.PROVIDER_ID + ':' + id);
        ScriptingDataProviderManager.getInstance().registerDataProvider(trace, dataProvider);
        return dataProvider;
    }

}
/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.diff;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IDataPalette;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTree;

/**
 * @author gbastien
 *
 * @param <N>
 * @param <E>
 */
public class DifferentialWeightedTreeProvider implements IWeightedTreeProvider<Object, String, DifferentialWeightedTree<Object>> {

    private static final String DEFAULT_ELEMENT = "diff";

    private final Collection<DifferentialWeightedTree<Object>> fTrees;

    private final IWeightedTreeProvider<Object, ?, WeightedTree<Object>> fOriginalTree;

    /**
     * @param trees
     */
    public DifferentialWeightedTreeProvider(IWeightedTreeProvider<Object, ?, WeightedTree<Object>> originalTree, Collection<DifferentialWeightedTree<Object>> trees) {
        fTrees = trees;
        fOriginalTree = originalTree;
    }

    @Override
    public Collection<DifferentialWeightedTree<Object>> getTreesFor(String element) {
        if (element.equals(DEFAULT_ELEMENT)) {
            return fTrees;
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getElements() {
        return Collections.singleton(DEFAULT_ELEMENT);
    }

    @Override
    public String getTitle() {
        return "Differential tree"; //$NON-NLS-1$
    }

    @Override
    public @Nullable IDataPalette getPalette() {
        return DifferentialPalette.getInstance();
    }

    @Override
    public String toDisplayString(DifferentialWeightedTree<Object> tree) {
        return fOriginalTree.toDisplayString(tree.getOriginalTree());
    }

}

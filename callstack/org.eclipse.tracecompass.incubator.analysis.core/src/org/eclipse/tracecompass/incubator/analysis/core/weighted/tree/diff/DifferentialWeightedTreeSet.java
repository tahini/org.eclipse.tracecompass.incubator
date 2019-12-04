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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.ITree;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeSet;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * A treeset for differential weighted trees.
 *
 * @author Geneviève Bastien
 */
public class DifferentialWeightedTreeSet<@NonNull N> implements IWeightedTreeSet<N, Object, DifferentialWeightedTree<N>> {

    private static final String DEFAULT_ELEMENT = "diff"; //$NON-NLS-1$

    private final Set<Object> fRootElements = new HashSet<>();
    private final Multimap<Object, DifferentialWeightedTree<N>> fTrees = HashMultimap.create();

    /**
     * Constructor. This constructor takes weighted trees and adds them to a
     * unique default element
     *
     * @param trees
     *            The differential weighted trees for the single element
     */
    public DifferentialWeightedTreeSet(Collection<DifferentialWeightedTree<N>> trees) {
        fRootElements.add(DEFAULT_ELEMENT);
        fTrees.putAll(DEFAULT_ELEMENT, trees);
    }

    /**
     * Default constructor. The elements and their trees should be added by
     * calling {@link #addWeightedTree(Object, DifferentialWeightedTree)}
     */
    public DifferentialWeightedTreeSet() {

    }

    @Override
    public Collection<Object> getElements() {
        return fRootElements;
    }

    @Override
    public Collection<@NonNull DifferentialWeightedTree<N>> getTreesFor(Object element) {
        return Objects.requireNonNull(fTrees.get(element));
    }

    /**
     * Add a weighted tree for an element in this set. If a tree for the same
     * object already exists, their data will be merged.
     *
     * @param dstGroup
     *            The group to which to add this tree
     * @param tree
     *            The weighted tree to add to this set. This tree may be
     *            modified and merged with others, so it should not be a tree
     *            that is part of another tree (for groupings or diff or other
     *            operations for instance)
     */
    public void addWeightedTree(Object dstGroup, DifferentialWeightedTree<N> tree) {
        // Make sure the root element is present
        Object root = dstGroup;
        if (dstGroup instanceof ITree) {
            ITree parent = ((ITree) dstGroup).getParent();
            while (parent != null) {
                root = parent;
                parent = parent.getParent();
            }
            fRootElements.add(root);
        }
        fRootElements.add(root);

        // Add the tree to the appropriate group
        Collection<DifferentialWeightedTree<N>> trees = fTrees.get(dstGroup);
        for (DifferentialWeightedTree<N> currentTree : trees) {
            if (currentTree.getObject().equals(tree.getObject())) {
                currentTree.merge(tree);
                return;
            }
        }
        fTrees.put(dstGroup, tree);
    }

}
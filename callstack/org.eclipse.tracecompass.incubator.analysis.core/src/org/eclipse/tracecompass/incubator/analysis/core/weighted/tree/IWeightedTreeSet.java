/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.weighted.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

/**
 * @author Geneviève Bastien
 *
 * @param <N>
 *            The type of objects represented by each node in the tree
 * @param <E>
 *            The type of elements used to group the trees. If this type extends
 *            {@link ITree}, then the elements and their associated weighted
 *            trees will be grouped in a hierarchical style
 * @param <T>
 *            The type of the tree provided
 */
public interface IWeightedTreeSet<@NonNull N, E, @NonNull T extends WeightedTree<N>> {

    /**
     * Get the elements under which are the trees. It can be a single constant
     * element if this provider does not have the concept of grouping the trees.
     *
     * @return The elements used to group the trees
     */
    Collection<E> getElements();

    /**
     * Get the trees provided by this analysis. This should return all the trees
     * for the whole trace
     *
     * @param element
     *            The element for which to get the trees
     * @return A collection of trees provided by this class
     */
    Collection<T> getTreesFor(Object element);

    /**
     * Return a list of additional data sets' titles. These sets will be
     * available by calling {@link WeightedTree#getExtraDataTrees(int)} on the
     * trees, where the index in the list is the parameter that the children set
     * should match
     *
     * @return The title of each child set
     */
    default List<String> getExtraDataSets() {
        return Collections.emptyList();
    }

    /**
     * Get the trees for an element with the given string representation. If
     * many names are entered, then it is assumed the elements should be
     * {@link ITree}s and the hierarchy is followed
     *
     * @param elementNames
     *            The name(s) of the element to get the trees for. If multiple
     *            names are given, then the elements are expected to have a
     *            hierarchical relation
     * @return The trees for the given element. If no element with that name is
     *         found, an empty collection will be returned
     */
    default Collection<T> getTreesForNamed(String... elementNames) {
        Collection<?> elements = getElements();
        for (int i = 0; i < elementNames.length; i++) {
            String elementName = elementNames[i];
            for (Object element : elements) {
                if (String.valueOf(element).equals(elementName)) {
                    // Found the element at this level. Is this the last?
                    if (i == elementNames.length - 1) {
                        return getTreesFor(element);
                    }
                    if (element instanceof ITree) {
                        elements = ((ITree) element).getChildren();
                        break;
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}

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

import org.eclipse.jdt.annotation.Nullable;

/**
 * A basic interface for a tree structure, ie hierarchical data where each node
 * can be linked to a specific object.
 *
 * @author Geneviève Bastien
 */
public interface ITree {

    /**
     * Get the name of this tree element, it should be human-readable as it will
     * be displayed to the user
     *
     * @return The name of this tree element
     */
    String getName();

    /**
     * Get the caller of this callsite (parent)
     *
     * @return The caller of this callsite
     */
    @Nullable ITree getParent();

    /**
     * Get the callees of this callsite, ie the functions called by this one
     *
     * @return A collection of callees' callsites
     */
    Collection<ITree> getChildren();

    void addChild(ITree child);

    void setParent(@Nullable ITree parent);

    /**
     * Create a new element that is a copy of the current object. This copy
     * should copy only the object's data, not the hierarchy as callers of this
     * method may want to create a new hierarchy for those elements.
     *
     * @return a new element, copy of the element in parameter
     */
    ITree copyElement();

    /**
     * Get the depth of this object, recursively with its children. An object
     * with no children would have a depth of 1.
     *
     * @param tree
     *            The object for which to get the depth
     * @return The depth
     */
    static int getDepth(ITree tree) {
        Collection<ITree> children = tree.getChildren();
        if (children.isEmpty()) {
            return 1;
        }
        int depth = 1;
        for (ITree child : children) {
            depth = Math.max(depth, getDepth(child) + 1);
        }
        return depth;
    }

}

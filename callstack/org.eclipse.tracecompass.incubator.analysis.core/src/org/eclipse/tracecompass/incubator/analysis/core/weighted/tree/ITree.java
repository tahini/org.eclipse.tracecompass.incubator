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
 * This interface is meant to be used for consuming the tree structure, the
 * implementations can add their own tree building methods.
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
    @Nullable
    ITree getParent();

    /**
     * Get the callees of this callsite, ie the functions called by this one
     *
     * @return A collection of callees' callsites
     */
    Collection<ITree> getChildren();

}

/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.concepts;

import org.eclipse.jdt.annotation.NonNull;

/**
 * A class that represents a differential weighted tree. The weight is the base
 * weight of one of the differentiated tree[set] and a differencial value is
 * used to represent how it differs with the weight value of another tree.
 *
 * @author Geneviève Bastien
 *
 * @param <T>
 *            The type of object this tree uses
 */
public class DifferentialWeightedTree<@NonNull T> extends WeightedTree<@NonNull T> {

    private final double fDifference;

    /**
     * Constructor
     *
     * @param object
     *            The object this tree is linked to
     * @param initialWeight
     *            The initial weight of this tree
     * @param diffWeight
     *            The differential weight with the base
     */
    public DifferentialWeightedTree(T object, long initialWeight, double diffWeight) {
        super(object, initialWeight);
        fDifference = diffWeight;
    }

    /**
     * Get the differential value for this object
     *
     * @return The differential value
     */
    public double getDifference() {
        return fDifference;
    }

}

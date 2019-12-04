/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.tests.stubs.weighted;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeGroupDescriptor;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeSet;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.WeightedTree;

/**
 * Simple implementation of the {@link IWeightedTreeProvider} interface, for
 * testing purposes.
 *
 * @author Geneviève Bastien
 */
public class SimpleWeightedTreeProvider implements IWeightedTreeProvider<String, SimpleTree, WeightedTree<String>> {

    static final IWeightedTreeGroupDescriptor ROOT_DESCRIPTOR = new IWeightedTreeGroupDescriptor() {
        @Override
        public @Nullable IWeightedTreeGroupDescriptor getNextGroup() {
            return SECOND_DESCRIPTOR;
        }

        @Override
        public String getName() {
            return "first level";
        }
    };

    private static final IWeightedTreeGroupDescriptor SECOND_DESCRIPTOR = new IWeightedTreeGroupDescriptor() {
        @Override
        public @Nullable IWeightedTreeGroupDescriptor getNextGroup() {
            return null;
        }

        @Override
        public String getName() {
            return "second level";
        }
    };

    private final boolean fWithGroupDescriptors;

    /**
     * Constructor
     */
    public SimpleWeightedTreeProvider() {
        this(true);
    }

    /**
     * Constructor
     *
     * @param withGroupDescriptors Whether to use the group descriptors or not
     */
    public SimpleWeightedTreeProvider(boolean withGroupDescriptors) {
        fWithGroupDescriptors = withGroupDescriptors;
    }

    @Override
    public IWeightedTreeSet<String, SimpleTree, WeightedTree<String>> getTreeSet() {
        return WeightedTreeTestData.getStubData();
    }

    @Override
    public String getTitle() {
        return "Simple weighted tree provider for unit tests";
    }

    @Override
    public @Nullable IWeightedTreeGroupDescriptor getGroupDescriptor() {
        return fWithGroupDescriptors ? ROOT_DESCRIPTOR : IWeightedTreeProvider.super.getGroupDescriptor();
    }

}
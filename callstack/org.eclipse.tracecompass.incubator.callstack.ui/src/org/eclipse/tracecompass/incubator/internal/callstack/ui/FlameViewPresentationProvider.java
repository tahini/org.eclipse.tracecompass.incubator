/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.ui;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.BaseDataProviderTimeGraphPresentationProvider;

public class FlameViewPresentationProvider extends BaseDataProviderTimeGraphPresentationProvider {

    private @Nullable String fProviderId = null;

    public FlameViewPresentationProvider(String defaultDataProviderProviderId) {
        super(defaultDataProviderProviderId);
    }

    @Override
    protected String getProviderId() {
        return fProviderId != null ? fProviderId : super.getProviderId();
    }

    public void setProviderId(String providerId) {
        fProviderId = providerId;
    }

}

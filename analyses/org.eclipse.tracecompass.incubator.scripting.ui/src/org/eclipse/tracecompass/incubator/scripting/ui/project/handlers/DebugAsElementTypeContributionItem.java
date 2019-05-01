/*******************************************************************************
 * Copyright (c) 2019 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.scripting.ui.project.handlers;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.tracecompass.tmf.core.util.Pair;

/**
 * ContributionItem for the Debug As -> <launch shortcut>.
 *
 * @author Bernd Hufmann
 *
 */
public class DebugAsElementTypeContributionItem extends LaunchElementTypeContributionItem {

    private static final String EASE_LAUNCH_SHORTCUT = "org.eclipse.ease.launchShortcut"; //$NON-NLS-1$

    @Override
    protected String getLaunchMode() {
        return Objects.requireNonNull(ILaunchManager.DEBUG_MODE);
    }

    @Override
    protected Set<Pair<String, String>> getParam() {
        Set<Pair<String, String>> selectedTraceTypes = new HashSet<>();
        selectedTraceTypes.add(new Pair<>(EASE_LAUNCH_SHORTCUT, Objects.requireNonNull(Messages.Scripting_RunAsScriptName)));
        return selectedTraceTypes;
    }
}

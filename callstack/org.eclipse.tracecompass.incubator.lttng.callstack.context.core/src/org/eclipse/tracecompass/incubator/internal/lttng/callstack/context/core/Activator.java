package org.eclipse.tracecompass.incubator.internal.lttng.callstack.context.core;

import org.eclipse.tracecompass.common.core.TraceCompassActivator;

/**
 * Activator
 */
public class Activator extends TraceCompassActivator {

    /** The plug-in ID */
    public static final String PLUGIN_ID = "org.eclipse.tracecompass.incubator.lttng.callstack.context.core"; //$NON-NLS-1$

    /**
     * The constructor
     */
    public Activator() {
        super(PLUGIN_ID);
    }

    /**
     * Returns the instance of this plug-in
     *
     * @return The plugin instance
     */
    public static TraceCompassActivator getInstance() {
        return TraceCompassActivator.getInstance(PLUGIN_ID);
    }

    @Override
    protected void startActions() {
    }

    @Override
    protected void stopActions() {
    }

}


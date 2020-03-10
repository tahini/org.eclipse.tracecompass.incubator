/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.io;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.kernel.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemBuilderUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * File Descriptor State Provider
 * </p>
 * <p>
 * This allows handling of generic file descriptors with common operations
 * </p>
 * <p>
 * Common elements
 * </p>
 * <ul>
 * <li>read</li>
 * <li>write</li>
 * <li>close</li>
 * </ul>
 *
 * @author Matthew Khouzam
 */
public class IoStateProvider extends AbstractTmfStateProvider {

    /**
     * ID of this state provider
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.io.stateprovider"; //$NON-NLS-1$

    /**
     * TID field in state system
     */
    public static final String ATTRIBUTE_TID = "TID"; //$NON-NLS-1$
    /**
     * Resources field in state system
     */
    public static final String RESOURCES = "RES"; //$NON-NLS-1$
    /**
     * TID field in state system
     */
    public static final String ATTRIBUTE_FDTBL = "TID"; //$NON-NLS-1$


    /**
     * Read entry
     */
    public static final String ATTRIBUTE_READ = "read"; //$NON-NLS-1$

    /** Various syscall names for different purposes */
    private static final Collection<String> OPEN_FROM_DISK = ImmutableList.of("open", "openat");  //$NON-NLS-1$//$NON-NLS-2$
    private static final Collection<String> OPEN_FROM_NET = ImmutableList.of("socket"); //$NON-NLS-1$
    private static final Collection<String> DUP_SYSCALLS = ImmutableList.of("fcntl", "dup", "dup2", "dup3");   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> SYNC_SYSCALLS = ImmutableList.of("sync", "sync_file_range", "fsync", "fdatasync");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> READ_SYSCALLS = ImmutableList.of("read", "recvmsg", "recvfrom", "readv", "pread", "pread64", "preadv"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> WRITE_SYSCALLS = ImmutableList.of("write", "sendmsg", "sendto", "writev", "pwrite", "pwrite64", "pwritev");   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> CLOSE_SYSCALL = ImmutableList.of("close"); //$NON-NLS-1$
    private static final Collection<String> READ_WRITE_SYSCALLS = ImmutableList.of("splice", "senfile64");  //$NON-NLS-1$//$NON-NLS-2$
    private static final String LTTNG_STATEDUMP_FILE_DESCRIPTOR = "lttng_statedump_file_descriptor"; //$NON-NLS-1$


    /** Event fieLD names */
    private static final String FIELD_FILENAME = "filename"; //$NON-NLS-1$
    private static final String FIELD_PID = "pid"; //$NON-NLS-1$
    private static final String FIELD_OLDFD = "oldfd"; //$NON-NLS-1$
    private static final String FIELD_FILDES = "fildes"; //$NON-NLS-1$
    private static final String FIELD_DESCRIPTOR = "fd"; //$NON-NLS-1$

    private static final String UNKNOWN_FILE = "<unknown>"; //$NON-NLS-1$

    private static final int VERSION = 2;

    /**
     * Write entry
     */
    public static final String ATTRIBUTE_WRITE = "write"; //$NON-NLS-1$

    private final Map<String, EventConsumer> fHandlers = new HashMap<>();
    private final IKernelAnalysisEventLayout fLayout;

    /*
     * Nullable to make the jdt be quiet
     */
    private final Map<Integer, @Nullable Long> fToRead = new HashMap<>();
    private final Map<Integer, @Nullable Long> fToWrite = new HashMap<>();
    /* Map a TID to the file being opened */
    private final Map<Integer, String> fOpening = new HashMap<>();
    /* Map a TID to the file descriptor being closed */
    private final Map<Integer, Long> fClosing = new HashMap<>();

    @FunctionalInterface
    private interface EventConsumer {
        void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid);
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace
     */
    public IoStateProvider(IKernelTrace trace) {
        super(trace, ID);
        fLayout = trace.getKernelEventLayout();

        for (String syscall : OPEN_FROM_DISK) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::openBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::openEnd);
        }
        for (String syscall : OPEN_FROM_NET) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::netBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::netEnd);
        }
        for (String syscall : DUP_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::dupBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::dupEnd);
        }
        for (String syscall : SYNC_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::syncBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::syncEnd);
        }
        for (String syscall : READ_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::readBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::readEnd);
        }
        for (String syscall : WRITE_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::writeBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::writeEnd);
        }
        for (String syscall : READ_WRITE_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::readWriteBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::readWriteEnd);
        }
        for (String syscall : CLOSE_SYSCALL) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::closeBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::closeEnd);
        }
        addEventHandler(LTTNG_STATEDUMP_FILE_DESCRIPTOR, this::statedumpHandle);
    }

    /**
     * Add a handler for entries
     *
     * @param eventName
     *            the name to handle
     * @param handler
     *            handler
     */
    protected final void addEventHandler(String eventName, EventConsumer handler) {
        fHandlers.put(eventName, handler);
    }

    @Override
    protected final void eventHandle(@NonNull ITmfEvent event) {
        EventConsumer eventHandler = fHandlers.get(event.getName());
        if (eventHandler == null) {
            return;
        }

        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }
        Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (tid == null) {
            return;
        }

        eventHandler.handleEvent(ssb, event, tid);
    }

    /**
     * Get the event layout of this trace. Many known concepts from the Linux
     * kernel may be exported under different names, depending on the tracer.
     *
     * @return The event layout
     */
    protected final IKernelAnalysisEventLayout getLayout() {
        return fLayout;
    }

    /**
     * Check if a file descriptor is valid. Has it been opened? if not, let's
     * ignore it for now.
     *
     * @param ssb
     *            State system
     * @param tid
     *            the TID that owns the file descriptor
     * @param fd
     *            the file descriptor
     * @return the file descriptor or null if invalid
     */
    protected static final @Nullable Long isValidFileDescriptor(ITmfStateSystem ssb, Integer tid, @Nullable Long fd) {
        if (fd == null) {
            return null;
        }
        int tidFileQuark = ssb.optQuarkAbsolute(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        if (tidFileQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return null;
        }
        return ssb.queryOngoing(tidFileQuark) != null ? fd : null;
    }

    /**
     * @param ssb
     */
    private void openBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = event.getContent().getFieldValue(String.class, FIELD_FILENAME);
        fOpening.put(tid, filename != null ? filename : UNKNOWN_FILE);
    }

    private void openEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = fOpening.remove(tid);

        Long ret = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (ret == null) {
            return;
        }

        if (ret > 0) {
            openFile(ssb, event.getTimestamp().toNanos(), tid, ret, filename);
        }
        // TODO: Do something about erroneous opens?

    }

    private void netBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    private void netEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    private void dupBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long oldFd = event.getContent().getFieldValue(Long.class, FIELD_OLDFD);
        if (oldFd == null) {
            // Maybe it's the dup syscall with the fildes field
            oldFd = event.getContent().getFieldValue(Long.class, FIELD_FILDES);
        }
        // TODO Handle the fcntl syscall
        if (oldFd == null) {
            return;
        }
        int oldFdQuark = ssb.optQuarkAbsolute(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(oldFd));
        // Get the file to dup
        String filename = UNKNOWN_FILE;
        if (oldFdQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
            Object fileObj = ssb.queryOngoing(oldFdQuark);
            if (fileObj instanceof String) {
                filename = (String) fileObj;
            }
        }
        fOpening.put(tid, filename);
    }

    private void dupEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = fOpening.remove(tid);
        // ret is the new fd, whether for a dup, dup2 or dup3 call
        Long newFd = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (newFd == null || newFd < 0) {
            return;
        }

        // Close the previous fd if required, then add the new file
        long time = event.getTimestamp().toNanos();
        closeFile(ssb, time, tid, newFd);
        openFile(ssb, event.getTimestamp().toNanos(), tid, newFd, filename);
    }

    private void syncBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    private void syncEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    /**
     * @param ssb
     */
    private void readBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        if (fd == null) {
            return;
        }
        fToRead.put(tid, fd);
    }

    private void readEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        long time = event.getTimestamp().toNanos();
        Long count = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
        Long fd = fToRead.remove(tid);
        // No read was done
        if (fd == null || count == null || count < 0) {
            return;
        }
        Long validFd = isValidFileDescriptor(ssb, tid, fd);
        if (validFd == null) {
            // The file is not opened in the state system, open it for this thread
            openFile(ssb, time, tid, fd, null);
        }
        readFromFd(ssb, time, tid, fd, count);
    }

    /**
     * @param ssb
     */
    private void writeBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        if (fd == null) {
            return;
        }
        fToWrite.put(tid, fd);
    }

    private void writeEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        long time = event.getTimestamp().toNanos();
        Long count = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
        Long fd = fToRead.remove(tid);
        // No read was done
        if (fd == null || count == null || count < 0) {
            return;
        }
        Long validFd = isValidFileDescriptor(ssb, tid, fd);
        if (validFd == null) {
            // The file is not opened in the state system, open it for this thread
            openFile(ssb, time, tid, fd, null);
        }
        writeFromFd(ssb, time, tid, fd, count);
    }

    private void readWriteBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    private void readWriteEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {

    }

    private void closeBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        fd = isValidFileDescriptor(ssb, tid, fd);
        if (fd == null) {
            return;
        }
        fClosing.put(tid, fd);
    }

    private void closeEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        try {
            Long ret = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
            Long fd = fClosing.remove(tid);
            if (ret == null || fd == null || ret < 0) {
                return;
            }
            closeFile(ssb, event.getTimestamp().toNanos(), tid, fd);
        } catch (StateValueTypeException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    private static void closeFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd) {
        int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        ssb.removeAttribute(time, fdQuark);

        // TODO Close the file for this thread in the Resources section
    }

    private static void openFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, @Nullable String filename) {
        int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        ssb.modifyAttribute(time, filename != null ? filename : UNKNOWN_FILE, fdQuark);
    }

    private static void readFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count) {
        rwFromFd(ssb, time, tid, fd, count, ATTRIBUTE_READ);
    }

    private static void writeFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count) {
        rwFromFd(ssb, time, tid, fd, count, ATTRIBUTE_WRITE);
    }

    private static void rwFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count, String attribute) {
        try {
            // Add the read specific to this file
            int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, fdQuark, count);

            // Add the read for this thread
            int tidReadQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, tidReadQuark, count);
        } catch (StateValueTypeException | AttributeNotFoundException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    /**
     * @param tid
     */
    private void statedumpHandle(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long pid = (event.getContent().getFieldValue(Long.class, FIELD_PID));
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        String filename = (event.getContent().getFieldValue(String.class, FIELD_FILENAME));
        if (pid == null || fd == null || filename == null) {
            return;
        }
        int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(pid), String.valueOf(fd));
        ssb.updateOngoingState(filename, fdQuark);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new IoStateProvider((IKernelTrace) getTrace());
    }
}

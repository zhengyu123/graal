/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.jdk11.jfr;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.GCWhen;
import com.oracle.svm.core.jfr.JfrEventSupport;
import com.oracle.svm.core.jfr.JfrPausePhase;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.util.VMError;

/**
 * Jfr event support for supported JDK versions.
 */
final class JfrEventSupportJDK11OrLater extends JfrEventSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    JfrEventSupportJDK11OrLater() {
    }

    @Override
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public void emitGCHeapSummaryEvent(UnsignedWord gcId, GCWhen gcWhen, UnsignedWord start, UnsignedWord committedSize, UnsignedWord reservedSize, UnsignedWord heapUsed) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvents.GCHeapSummaryEvent)) {
            JfrBuffer buffer = ((JfrThreadLocal) SubstrateJVM.getThreadLocal()).getNativeBuffer();
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, buffer);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvents.GCHeapSummaryEvent.getId());
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, gcId.rawValue());
            JfrNativeEventWriter.putLong(data, gcWhen.getId());
            // Virtual Space
            JfrNativeEventWriter.putLong(data, start.rawValue());
            JfrNativeEventWriter.putLong(data, start.rawValue() + committedSize.rawValue()); // Committed end
            JfrNativeEventWriter.putLong(data, committedSize.rawValue()); // Committed size
            JfrNativeEventWriter.putLong(data, start.rawValue() + reservedSize.rawValue()); // Reserved end
            JfrNativeEventWriter.putLong(data, reservedSize.rawValue()); // Reserved size

            JfrNativeEventWriter.putLong(data, heapUsed.rawValue());
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    // GC Pauses
    @Override
    public void startPausePhase(JfrPausePhase phase, UnsignedWord gcEpoch, String name) {
        phase.setGCEpoch(gcEpoch);
        phase.setStartTicks(JfrTicks.elapsedTicks());
        phase.setLevel(0);
        phase.setParent(null);
        JfrNamedGCEventAccess.initName(phase, name);
        assert JfrThreadLocal.getPausePhase().isNull();
        JfrThreadLocal.setPausePhase(phase);
    }

    @Override
    public void startPauseSubPhase(JfrPausePhase phase, String name) {
        JfrPausePhase parent = JfrThreadLocal.getPausePhase();
        assert parent.isNonNull();
        assert !parent.equal(phase);
        assert parent.getLevel() < 4;
        phase.setGCEpoch(parent.getGCEpoch());
        phase.setStartTicks(JfrTicks.elapsedTicks());
        phase.setLevel(parent.getLevel() + 1);
        JfrNamedGCEventAccess.initName(phase, name);
        phase.setParent(parent);
        JfrThreadLocal.setPausePhase(phase);
    }

    @Override
    public void commitPausePhase(JfrPausePhase phase) {
        long endTicks = JfrTicks.elapsedTicks();
        JfrEvents event = getGCPhasePauseEvent(phase.getLevel());
        emitGCPhasePauseEventImpl(event, endTicks, phase);
        JfrNamedGCEventAccess.release(phase);
        JfrThreadLocal.setPausePhase(phase.getParent());
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitGCPhasePauseEventImpl(JfrEvents event, long end, JfrPausePhase phase) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(event)) {
            JfrBuffer buffer = ((JfrThreadLocal) SubstrateJVM.getThreadLocal()).getNativeBuffer();
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, buffer);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, event.getId());
            JfrNativeEventWriter.putLong(data, phase.getStartTicks());
            JfrNativeEventWriter.putLong(data, end - phase.getStartTicks());
            JfrNativeEventWriter.putLong(data, 0); // Thread
            JfrNativeEventWriter.putLong(data, phase.getGCEpoch().rawValue());

            int len = phase.getLength();
            if (len == 0) {
                JfrNativeEventWriter.putByte(data, JfrChunkWriter.StringEncoding.EMPTY_STRING.byteValue);
            } else {
                JfrNativeEventWriter.putByte(data, JfrChunkWriter.StringEncoding.CHAR_ARRAY.byteValue);
                JfrNativeEventWriter.putInt(data, len);
                for (int index = 0; index < len; index++) {
                    JfrNativeEventWriter.putChar(data, JfrNamedGCEventAccess.read(phase, index));
                }
            }
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    private static JfrEvents getGCPhasePauseEvent(int level) {
        switch (level) {
            case 0:
                return JfrEvents.GCPhasePauseEvent;
            case 1:
                return JfrEvents.GCPhasePauseLevel1Event;
            case 2:
                return JfrEvents.GCPhasePauseLevel2Event;
            case 3:
                return JfrEvents.GCPhasePauseLevel3Event;
            case 4:
                return JfrEvents.GCPhasePauseLevel4Event;
            default:
                VMError.shouldNotReachHere("At most 4 levels");
                return JfrEvents.GCPhasePauseEvent; /* return something */
        }
    }
}

@AutomaticFeature
final class JfrFeatureJDK11OrLater implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (JfrEnabled.get()) {
            ImageSingletons.add(JfrEventSupport.class, new JfrEventSupportJDK11OrLater());
        } else {
            ImageSingletons.add(JfrEventSupport.class, new JfrEventSupport.JfrDoNothingEventSupport());
        }
    }
}

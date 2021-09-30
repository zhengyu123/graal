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
package com.oracle.svm.core.jfr;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.heap.GCWhen;

import com.oracle.svm.core.annotate.AutomaticFeature;

/**
 * JfrEventSupport provides native JFR event support to SubstractJVM.
 */
public abstract class JfrEventSupport {
    @Fold
    public static JfrEventSupport get() {
        return ImageSingletons.lookup(JfrEventSupport.class);
    }

    public abstract void emitGCHeapSummaryEvent(UnsignedWord gcId, GCWhen gcWhen, UnsignedWord start, UnsignedWord committedSize, UnsignedWord reservedSize, UnsignedWord heapUsed);

    // GC events
    public abstract void startPausePhase(JfrPausePhase phase, UnsignedWord gcEpoch, String name);

    public abstract void startPauseSubPhase(JfrPausePhase phase, String name);

    public abstract void commitPausePhase(JfrPausePhase phase);

    public static class JfrDoNothingEventSupport extends JfrEventSupport {
        @Override
        public void emitGCHeapSummaryEvent(UnsignedWord gcId, GCWhen gcWhen, UnsignedWord start, UnsignedWord committedSize, UnsignedWord reservedSize, UnsignedWord heapUsed) {
        }

        // GC Pauses
        @Override
        public void startPausePhase(JfrPausePhase phase, UnsignedWord gcId, String name) {
        }

        @Override
        public void startPauseSubPhase(JfrPausePhase phase, String name) {
        }

        @Override
        public void commitPausePhase(JfrPausePhase phase) {
        }
    }
}

@AutomaticFeature
final class JfrFeatureBeforeJDK11 implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC < 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrEventSupport.class, new JfrEventSupport.JfrDoNothingEventSupport());
    }
}

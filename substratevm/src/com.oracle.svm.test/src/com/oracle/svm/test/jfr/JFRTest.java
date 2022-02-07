/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assume.assumeTrue;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;

import com.oracle.svm.core.jfr.JfrEnabled;
import com.oracle.svm.test.jfr.utils.JFR;
import com.oracle.svm.test.jfr.utils.JFRFileParser;
import com.oracle.svm.test.jfr.utils.LocalJFR;

import java.util.HashMap;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;


/** Base class for JFR unit tests. */
public abstract class JFRTest {

    private HashMap<String, Boolean> eventMap = new HashMap<>();

    protected JFR jfr;
    protected Recording recording;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || JfrEnabled.get());
    }

    @Before
    public void startRecording() {
        try {
            jfr = new LocalJFR();
            recording = jfr.createRecording(getClass().getName());

            String[] events = testEvents();
            setupEvents(events);

            jfr.startRecording(recording);
        } catch (Exception e) {
            Assert.fail("Fail to start recording! Cause: " + e.getMessage());
        }
    }

    @After
    public void endRecording() {
        try {
            jfr.endRecording(recording);
        } catch (Exception e) {
            Assert.fail("Fail to stop recording! Cause: " + e.getMessage());
        }

        try {
            checkRecording();
        } finally {
            try {
                jfr.cleanupRecording(recording);
            } catch (Exception e) {
            }
        }
    }

    protected void setupEvents(String[] events) {
        if (events != null) {
            for (String event: events) {
                recording.enable(event);
                eventMap.put(event, Boolean.FALSE);
            }
        }
    }

    // List events that expects to be recorded
    public abstract String[] testEvents();

    protected void checkEvents() {
        try (RecordingFile recordingFile = new RecordingFile(recording.getDestination())) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                String eventName = event.getEventType().getName();
                if (eventMap.containsKey(eventName)) {
                    eventMap.put(eventName, Boolean.TRUE);
                }
            }
        } catch (Exception e) {
            Assert.fail("Failed to read events: " + e.getMessage());
        }

        for (String name: eventMap.keySet()) {
            if (!eventMap.get(name)) {
                Assert.fail("Event: " + name + " not found in recording");
            }
        }
    }

    protected void checkRecording() throws AssertionError {
        try {
            JFRFileParser.parse(recording);
        } catch (Exception e) {
            Assert.fail("Failed to parse recording: " + e.getMessage());
        }
    }
}

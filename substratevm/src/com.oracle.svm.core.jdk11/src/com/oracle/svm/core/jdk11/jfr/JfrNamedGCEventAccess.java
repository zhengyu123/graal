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

// Checkstyle: allow reflection

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jfr.JfrNamedGCEvent;
import com.oracle.svm.core.util.VMError;

import sun.misc.Unsafe;
import static jdk.internal.misc.Unsafe.ARRAY_BYTE_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_BYTE_INDEX_SCALE;


public final class JfrNamedGCEventAccess {
    private static final byte LATIN1 = 0;
    private static final byte UTF16 = 1;

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();
    private static final long VALUE_OFFSET;
    private static final long CODER_OFFSET;

    static {
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(String.class.getDeclaredField("value"));
            CODER_OFFSET = UNSAFE.objectFieldOffset(String.class.getDeclaredField("coder"));
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static byte getCoder(String s) {
        return UNSAFE.getByte(s, CODER_OFFSET);
    }

    public static boolean isLatin1(String s) {
        return getCoder(s) == LATIN1;
    }

    public static void initName(JfrNamedGCEvent ne, String name) {
        int len = name.length();
        byte coder = getCoder(name);
        Pointer buffer =  ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(len * 3)); // char occupies max 3 bytes
        ne.setBuffer(buffer);
        writeToBuffer(name, buffer);
        ne.setLength(len);
        ne.setLatin1(isLatin1(name));
    }

    private static void writeToBuffer(String s, Pointer buffer) {
        byte[] value = (byte[]) UNSAFE.getObject(s, VALUE_OFFSET);
        Pointer pos = buffer;
        int len = s.length();
        boolean latin1 = isLatin1(s);
        int offset = 0;
        for (int i = 0; i < len; i++) {
            char c = latin1 ? StringLatin1.getChar(value, i) : StringUTF16.getChar(value, i);
            pos.writeChar(offset, c);
            offset += Character.BYTES;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static char read(JfrNamedGCEvent ns, int index) {
        assert index < ns.getLength();
        Pointer pos = ns.getBuffer();
        return pos.readChar(index * Character.BYTES);
    }

    public static void release(JfrNamedGCEvent ns) {
        Pointer buffer = ns.getBuffer();
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(buffer);
    }

    static class StringUTF16 {
        public static char getChar(byte[] val, int index) {
            assert index < val.length;
            return UNSAFE.getChar(val,
                ARRAY_BYTE_BASE_OFFSET + ARRAY_BYTE_INDEX_SCALE * index * 2L);
        }
    }

    static class StringLatin1 {
        public static char getChar(byte[] val, int index) {
            assert index < val.length;
            return (char) (val[index] & 255);
        }
    }
}


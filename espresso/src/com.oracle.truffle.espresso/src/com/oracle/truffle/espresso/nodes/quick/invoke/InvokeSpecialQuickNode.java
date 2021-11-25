/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecial;
import com.oracle.truffle.espresso.nodes.bytecodes.InvokeSpecialNodeGen;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class InvokeSpecialQuickNode extends QuickNode {

    @CompilationFinal Method method;
    final int resultAt;
    final boolean returnsPrimitiveType;
    @Child InvokeSpecial.WithoutNullCheck invokeSpecial;

    public InvokeSpecialQuickNode(Method method, int top, int callerBCI) {
        super(top, callerBCI);
        this.method = method;
        this.resultAt = top - Signatures.slotsForParameters(method.getParsedSignature()) - 1; // -receiver
        this.returnsPrimitiveType = Types.isPrimitive(Signatures.returnType(method.getParsedSignature()));
        this.invokeSpecial = InvokeSpecialNodeGen.WithoutNullCheckNodeGen.create(method);
    }

    @Override
    public int execute(VirtualFrame frame) {
        /*
         * Method signature does not change across methods. Can safely use the constant signature
         * from `method` instead of the non-constant signature from the lookup.
         */
        Object[] args = BytecodeNode.popArguments(frame, top, true, method.getParsedSignature());
        nullCheck((StaticObject) args[0]);
        Object result = invokeSpecial.execute(args);
        if (!returnsPrimitiveType) {
            getBytecodeNode().checkNoForeignObjectAssumption((StaticObject) result);
        }
        return (getResultAt() - top) + BytecodeNode.putKind(frame, getResultAt(), result, method.getReturnKind());
    }

    private int getResultAt() {
        return resultAt;
    }

    @Override
    public boolean removedByRedefintion() {
        return method.isRemovedByRedefition();
    }
}

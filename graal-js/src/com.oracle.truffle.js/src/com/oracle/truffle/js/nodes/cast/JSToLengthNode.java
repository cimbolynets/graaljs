/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.cast;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.SafeInteger;

/**
 * Implementation of ToLength (ES6 7.1.15).
 *
 */
public abstract class JSToLengthNode extends JavaScriptBaseNode {

    @NeverDefault
    public static JSToLengthNode create() {
        return JSToLengthNodeGen.create();
    }

    public abstract long executeLong(Object value);

    @Specialization
    protected final long doInt(int value,
                    @Cached @Shared("negativeBranch") InlinedBranchProfile negativeBranch) {
        if (value < 0) {
            negativeBranch.enter(this);
            return 0;
        }
        return value;
    }

    @Specialization
    protected final long doSafeInteger(SafeInteger value,
                    @Cached @Shared("negativeBranch") InlinedBranchProfile negativeBranch) {
        long longValue = value.longValue();
        if (longValue < 0) {
            negativeBranch.enter(this);
            return 0;
        }
        return longValue;
    }

    @Specialization
    protected final long doDouble(double value,
                    @Cached @Shared("negativeBranch") InlinedBranchProfile negativeBranch,
                    @Cached @Shared("tooLargeBranch") InlinedBranchProfile tooLargeBranch) {
        // NaN and Infinity are converted to 0L and Long.MAX_VALUE by the long cast, respectively.
        return doLong((long) value, negativeBranch, tooLargeBranch);
    }

    @Specialization(guards = "isUndefined(value)")
    protected static long doUndefined(@SuppressWarnings("unused") Object value) {
        return 0;
    }

    @Specialization
    protected final long doObject(Object value,
                    @Cached JSToNumberNode toNumberNode,
                    @Cached @Shared("negativeBranch") InlinedBranchProfile negativeBranch,
                    @Cached @Shared("tooLargeBranch") InlinedBranchProfile tooLargeBranch) {
        Number result = (Number) toNumberNode.execute(value);
        return doLong(JSRuntime.toInteger(result), negativeBranch, tooLargeBranch);
    }

    private long doLong(long value,
                    InlinedBranchProfile negativeBranch,
                    InlinedBranchProfile tooLargeBranch) {
        if (value < 0) {
            negativeBranch.enter(this);
            return 0;
        }
        if (value > JSRuntime.MAX_SAFE_INTEGER_LONG) {
            tooLargeBranch.enter(this);
            return JSRuntime.MAX_SAFE_INTEGER_LONG;
        }
        return value;
    }
}

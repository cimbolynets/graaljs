/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.js.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.oracle.js.parser.ir.Block;
import com.oracle.js.parser.ir.Scope;
import com.oracle.js.parser.ir.Statement;

/**
 * A class that tracks the current lexical context of node visitation as a stack of
 * {@code ParserContextNode} nodes. Has special methods to retrieve useful subsets of the context.
 *
 * This is implemented with a primitive array and a stack pointer, because it really makes a
 * difference performance wise. None of the collection classes were optimal
 */

class ParserContext {

    private ParserContextNode[] stack;
    private int sp;

    private static final int INITIAL_DEPTH = 16;

    /**
     * Constructs a ParserContext, initializes the stack
     */
    ParserContext() {
        this.sp = 0;
        this.stack = new ParserContextNode[INITIAL_DEPTH];
    }

    /**
     * Pushes a new block on top of the context, making it the innermost open block.
     *
     * @param node the new node
     * @return The node that was pushed
     */
    public <T extends ParserContextNode> T push(final T node) {
        assert !contains(node);
        if (sp == stack.length) {
            final ParserContextNode[] newStack = new ParserContextNode[sp * 2];
            System.arraycopy(stack, 0, newStack, 0, sp);
            stack = newStack;
        }
        stack[sp] = node;
        sp++;

        return node;
    }

    /**
     * The topmost node on the stack
     *
     * @return The topmost node on the stack
     */
    public ParserContextNode peek() {
        return stack[sp - 1];
    }

    /**
     * Removes and returns the topmost Node from the stack.
     *
     * @param node The node expected to be popped, used for sanity check
     * @return The removed node
     */
    @SuppressWarnings("unchecked")
    public <T extends ParserContextNode> T pop(final T node) {
        --sp;
        final T popped = (T) stack[sp];
        stack[sp] = null;

        return popped;
    }

    /**
     * Tests if a node is on the stack.
     *
     * @param node The node to test
     * @return true if stack contains node, false otherwise
     */
    public boolean contains(final ParserContextNode node) {
        for (int i = 0; i < sp; i++) {
            if (stack[i] == node) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the topmost {@link ParserContextBreakableNode} on the stack, null if none on stack
     *
     * @return Returns the topmost {@link ParserContextBreakableNode} on the stack, null if none on
     *         stack
     */
    private ParserContextBreakableNode getBreakable() {
        for (final NodeIterator<ParserContextBreakableNode> iter = new NodeIterator<>(ParserContextBreakableNode.class, getCurrentFunction()); iter.hasNext();) {
            final ParserContextBreakableNode next = iter.next();
            if (next.isBreakableWithoutLabel()) {
                return next;
            }
        }
        return null;
    }

    /**
     * Find the breakable node corresponding to this label.
     *
     * @param labelName name of the label to search for. If null, the closest breakable node will be
     *            returned unconditionally, e.g. a while loop with no label
     * @return closest breakable node
     */
    public ParserContextBreakableNode getBreakable(final String labelName) {
        if (labelName != null) {
            return findLabelledItem(labelName, ParserContextBreakableNode.class);
        } else {
            return getBreakable();
        }
    }

    /**
     * Returns the loop node of the current loop, or null if not inside a loop
     *
     * @return nearest loop node
     */
    public ParserContextLoopNode getCurrentLoop() {
        final Iterator<ParserContextLoopNode> iter = new NodeIterator<>(ParserContextLoopNode.class, getCurrentFunction());
        return iter.hasNext() ? iter.next() : null;
    }

    private ParserContextLoopNode getContinueTo() {
        return getCurrentLoop();
    }

    /**
     * Find the continue target node corresponding to this label.
     *
     * @param labelName label name to search for. If null the closest loop node will be returned
     *            unconditionally, e.g. a while loop with no label
     * @return closest continue target node
     */
    public ParserContextLoopNode getContinueTo(final String labelName) {
        if (labelName != null) {
            return findLabelledItem(labelName, ParserContextLoopNode.class);
        }
        return getContinueTo();
    }

    /**
     * Check the stack for a given label node by name
     *
     * @param name name of the label
     * @return LabelNode if found, null otherwise
     */
    public ParserContextLabelNode findLabel(final String name) {
        for (final Iterator<ParserContextLabelNode> iter = new NodeIterator<>(ParserContextLabelNode.class, getCurrentFunction()); iter.hasNext();) {
            final ParserContextLabelNode next = iter.next();
            if (next.getLabelName().equals(name)) {
                return next;
            }
        }
        return null;
    }

    /**
     * Find the nearest breakable/loop with this label.
     *
     * @param labelName label name to search for
     * @param breakableType {@link ParserContextBreakableNode} or {@link ParserContextLoopNode}
     * @return nearest breakable/loop
     */
    private <T extends ParserContextBreakableNode> T findLabelledItem(final String labelName, Class<T> breakableType) {
        T prev = null;
        for (final Iterator<ParserContextNode> iter = new NodeIterator<>(ParserContextNode.class, getCurrentFunction()); iter.hasNext();) {
            final ParserContextNode next = iter.next();
            if (next instanceof ParserContextLabelNode) {
                ParserContextLabelNode labelStatement = (ParserContextLabelNode) next;
                if (labelStatement.getLabelName().equals(labelName)) {
                    return prev;
                }
            } else if (breakableType == ParserContextLoopNode.class && next instanceof ParserContextBlockNode && next.getFlag(Block.IS_SYNTHETIC) == 0) {
                // label is not associated with an iteration statement, e.g.:
                // label: { for(;;) { continue label; } }
                prev = null;
            } else if (breakableType.isInstance(next)) {
                prev = breakableType.cast(next);
            }
        }
        return null;
    }

    /**
     * Prepends a statement to the current node.
     *
     * @param statement The statement to prepend
     */
    public void prependStatementToCurrentNode(final Statement statement) {
        assert statement != null;
        stack[sp - 1].prependStatement(statement);
    }

    /**
     * Appends a statement to the current Node.
     *
     * @param statement The statement to append
     */
    public void appendStatementToCurrentNode(final Statement statement) {
        assert statement != null;
        stack[sp - 1].appendStatement(statement);
    }

    /**
     * Returns the innermost function in the context.
     *
     * @return the innermost function in the context.
     */
    public ParserContextFunctionNode getCurrentFunction() {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof ParserContextFunctionNode) {
                ParserContextFunctionNode function = (ParserContextFunctionNode) stack[i];
                if (function.isCoverArrowHead()) {
                    continue;
                }
                return function;
            }
        }
        return null;
    }

    /**
     * Returns an iterator over all blocks in the context, with the top block (innermost lexical
     * context) first.
     *
     * @return an iterator over all blocks in the context.
     */
    public Iterator<ParserContextBlockNode> getBlocks() {
        return new NodeIterator<>(ParserContextBlockNode.class);
    }

    /**
     * Returns the innermost block in the context.
     *
     * @return the innermost block in the context.
     */
    public ParserContextBlockNode getCurrentBlock() {
        return getBlocks().next();
    }

    /**
     * The last statement added to the context
     *
     * @return The last statement added to the context
     */
    public Statement getLastStatement() {
        if (sp == 0) {
            return null;
        }
        final ParserContextNode top = stack[sp - 1];
        final int s = top.getStatements().size();
        return s == 0 ? null : top.getStatements().get(s - 1);
    }

    /**
     * Returns an iterator over all functions in the context, with the top (innermost open) function
     * first.
     *
     * @return an iterator over all functions in the context.
     */
    public Iterator<ParserContextFunctionNode> getFunctions() {
        return new NodeIterator<>(ParserContextFunctionNode.class);
    }

    public ParserContextFunctionNode getCurrentNonArrowFunction() {
        final Iterator<ParserContextFunctionNode> iter = getFunctions();
        while (iter.hasNext()) {
            final ParserContextFunctionNode fn = iter.next();
            if (!fn.isArrow()) {
                return fn;
            }
        }
        return null;
    }

    /**
     * Returns the innermost scope in the context.
     */
    public Scope getCurrentScope() {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof ParserContextScopableNode) {
                ParserContextScopableNode scopable = (ParserContextScopableNode) stack[i];
                return scopable.getScope();
            }
        }
        return null;
    }

    /**
     * Returns the current class node or null if not inside a class.
     */
    public ParserContextClassNode getCurrentClass() {
        final Iterator<ParserContextClassNode> iter = new NodeIterator<>(ParserContextClassNode.class);
        return iter.hasNext() ? iter.next() : null;
    }

    /**
     * Returns an iterator over all classes in the context, with the innermost class first.
     *
     * @return an iterator over all classes in the context.
     */
    public Iterator<ParserContextClassNode> getClasses() {
        return new NodeIterator<>(ParserContextClassNode.class);
    }

    /**
     * Returns an iterator over all nodes in the context.
     */
    public Iterator<ParserContextNode> getAllNodes() {
        return new NodeIterator<>(ParserContextNode.class);
    }

    /**
     * Sets function flags on the current function or provisional arrow function in the context.
     */
    public ParserContextFunctionNode setCurrentFunctionFlag(int flag) {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof ParserContextFunctionNode) {
                ParserContextFunctionNode fn = (ParserContextFunctionNode) stack[i];
                fn.setFlag(flag);
                return fn;
            }
        }
        return null;
    }

    /**
     * Propagate relevant flags from the innermost function to its parent function.
     */
    public void propagateFunctionFlags() {
        ParserContextFunctionNode current = null;
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof ParserContextFunctionNode) {
                ParserContextFunctionNode f = (ParserContextFunctionNode) stack[i];
                if (current == null) {
                    current = f;
                } else {
                    ParserContextFunctionNode parent = f;
                    current.propagateFlagsToParent(parent);
                    break;
                }
            }
        }
    }

    private class NodeIterator<T extends ParserContextNode> implements Iterator<T> {
        private int index;
        private T next;
        private final Class<T> clazz;
        private ParserContextNode until;

        NodeIterator(final Class<T> clazz) {
            this(clazz, null);
        }

        NodeIterator(final Class<T> clazz, final ParserContextNode until) {
            this.index = sp - 1;
            this.clazz = clazz;
            this.until = until;
            this.next = findNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            final T lnext = next;
            next = findNext();
            return lnext;
        }

        @SuppressWarnings("unchecked")
        private T findNext() {
            for (int i = index; i >= 0; i--) {
                final Object node = stack[i];
                if (node == until) {
                    return null;
                }
                if (clazz.isAssignableFrom(node.getClass())) {
                    index = i - 1;
                    return (T) node;
                }
            }
            return null;
        }
    }
}

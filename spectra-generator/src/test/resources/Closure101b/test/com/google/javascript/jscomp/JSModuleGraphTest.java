/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.collect.Iterables;
import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Tests for {@link JSModuleGraph}
 */
public class JSModuleGraphTest extends TestCase {

    private final JSModule A = new JSModule("A");
    private final JSModule B = new JSModule("B");
    private final JSModule C = new JSModule("C");
    private final JSModule D = new JSModule("D");
    private final JSModule E = new JSModule("E");
    private final JSModule F = new JSModule("F");
    private JSModuleGraph graph = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        B.addDependency(A);  //     __A__
        C.addDependency(A);  //    /  |  \
        D.addDependency(B);  //   B   C  |
        E.addDependency(B);  //  / \ /|  |
        E.addDependency(C);  // D   E | /
        F.addDependency(A);  //      \|/
        F.addDependency(C);  //       F
        F.addDependency(E);
        graph = new JSModuleGraph(new JSModule[]{A, B, C, D, E, F});
    }

    public void testModuleDepth() {
        assertEquals("A should have depth 0", 0, graph.getDepth(A));
        assertEquals("B should have depth 1", 1, graph.getDepth(B));
        assertEquals("C should have depth 1", 1, graph.getDepth(C));
        assertEquals("D should have depth 2", 2, graph.getDepth(D));
        assertEquals("E should have depth 2", 2, graph.getDepth(E));
        assertEquals("F should have depth 3", 3, graph.getDepth(F));
    }

    public void testDeepestCommonDep() {
        assertDeepestCommonDep(null, A, A);
        assertDeepestCommonDep(null, A, B);
        assertDeepestCommonDep(null, A, C);
        assertDeepestCommonDep(null, A, D);
        assertDeepestCommonDep(null, A, E);
        assertDeepestCommonDep(null, A, F);
        assertDeepestCommonDep(A, B, B);
        assertDeepestCommonDep(A, B, C);
        assertDeepestCommonDep(A, B, D);
        assertDeepestCommonDep(A, B, E);
        assertDeepestCommonDep(A, B, F);
        assertDeepestCommonDep(A, C, C);
        assertDeepestCommonDep(A, C, D);
        assertDeepestCommonDep(A, C, E);
        assertDeepestCommonDep(A, C, F);
        assertDeepestCommonDep(B, D, D);
        assertDeepestCommonDep(B, D, E);
        assertDeepestCommonDep(B, D, F);
        assertDeepestCommonDep(C, E, E);
        assertDeepestCommonDep(C, E, F);
        assertDeepestCommonDep(E, F, F);
    }

    public void testDeepestCommonDepInclusive() {
        assertDeepestCommonDepInclusive(A, A, A);
        assertDeepestCommonDepInclusive(A, A, B);
        assertDeepestCommonDepInclusive(A, A, C);
        assertDeepestCommonDepInclusive(A, A, D);
        assertDeepestCommonDepInclusive(A, A, E);
        assertDeepestCommonDepInclusive(A, A, F);
        assertDeepestCommonDepInclusive(B, B, B);
        assertDeepestCommonDepInclusive(A, B, C);
        assertDeepestCommonDepInclusive(B, B, D);
        assertDeepestCommonDepInclusive(B, B, E);
        assertDeepestCommonDepInclusive(B, B, F);
        assertDeepestCommonDepInclusive(C, C, C);
        assertDeepestCommonDepInclusive(A, C, D);
        assertDeepestCommonDepInclusive(C, C, E);
        assertDeepestCommonDepInclusive(C, C, F);
        assertDeepestCommonDepInclusive(D, D, D);
        assertDeepestCommonDepInclusive(B, D, E);
        assertDeepestCommonDepInclusive(B, D, F);
        assertDeepestCommonDepInclusive(E, E, E);
        assertDeepestCommonDepInclusive(E, E, F);
        assertDeepestCommonDepInclusive(F, F, F);
    }

    public void testGetTransitiveDepsDeepestFirst() {
        assertTransitiveDepsDeepestFirst(A);
        assertTransitiveDepsDeepestFirst(B, A);
        assertTransitiveDepsDeepestFirst(C, A);
        assertTransitiveDepsDeepestFirst(D, B, A);
        assertTransitiveDepsDeepestFirst(E, C, B, A);
        assertTransitiveDepsDeepestFirst(F, E, C, B, A);
    }

    public void testCoalesceDuplicateFiles() {
        A.add(JSSourceFile.fromCode("a.js", ""));

        B.add(JSSourceFile.fromCode("a.js", ""));
        B.add(JSSourceFile.fromCode("b.js", ""));

        C.add(JSSourceFile.fromCode("b.js", ""));
        C.add(JSSourceFile.fromCode("c.js", ""));

        E.add(JSSourceFile.fromCode("c.js", ""));
        E.add(JSSourceFile.fromCode("d.js", ""));

        graph.coalesceDuplicateFiles();

        assertEquals(2, A.getInputs().size());
        assertEquals("a.js", A.getInputs().get(0).getName());
        assertEquals("b.js", A.getInputs().get(1).getName());
        assertEquals(0, B.getInputs().size());
        assertEquals(1, C.getInputs().size());
        assertEquals("c.js", C.getInputs().get(0).getName());
        assertEquals(1, E.getInputs().size());
        assertEquals("d.js", E.getInputs().get(0).getName());
    }


    private void assertDeepestCommonDepInclusive(
            JSModule expected, JSModule m1, JSModule m2) {
        assertDeepestCommonDepOneWay(expected, m1, m2, true);
        assertDeepestCommonDepOneWay(expected, m2, m1, true);
    }

    private void assertDeepestCommonDep(
            JSModule expected, JSModule m1, JSModule m2) {
        assertDeepestCommonDepOneWay(expected, m1, m2, false);
        assertDeepestCommonDepOneWay(expected, m2, m1, false);
    }

    private void assertDeepestCommonDepOneWay(
            JSModule expected, JSModule m1, JSModule m2, boolean inclusive) {
        JSModule actual = inclusive ?
                graph.getDeepestCommonDependencyInclusive(m1, m2) :
                graph.getDeepestCommonDependency(m1, m2);
        if (actual != expected) {
            fail(String.format(
                    "Deepest common dep of %s and %s should be %s but was %s",
                    m1.getName(), m2.getName(),
                    expected == null ? "null" : expected.getName(),
                    actual == null ? "null" : actual.getName()));
        }
    }

    private void assertTransitiveDepsDeepestFirst(JSModule m, JSModule... deps) {
        Iterable<JSModule> actual = graph.getTransitiveDepsDeepestFirst(m);
        assertEquals(Arrays.toString(deps),
                Arrays.toString(Iterables.toArray(actual, JSModule.class)));
    }
}

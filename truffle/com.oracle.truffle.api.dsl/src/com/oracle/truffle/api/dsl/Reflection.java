/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.dsl.internal.ReflectionAccesor;
import com.oracle.truffle.api.nodes.Node;

/**
 * Contains reflection utilties for Truffle DSL. The contained utilities are only usable if the
 * operation node is annotated with {@link Reflectable}.
 *
 * @since 0.22
 * @see Reflectable
 */
public final class Reflection {

    private static final List<List<Object>> EMPTY_CACHED = Collections.unmodifiableList(Arrays.asList(Collections.emptyList()));
    private static final List<List<Object>> NO_CACHED = Collections.emptyList();

    private Reflection() {
        /* No instances */
    }

    /**
     * Returns <code>true</code> if the given node is reflectable. If something is reflectable is
     * determined by if the node is generated by Truffle DSL, if is annotated with
     * {@link Reflectable} and if the DSL implementation supports reflection.
     *
     * @param node a DSL generated node
     * @return true if the given node is reflectable
     * @since 0.22
     */
    public static boolean isReflectable(Node node) {
        return node instanceof ReflectionAccesor;
    }

    /**
     * Returns reflection information for the first specialization that matches a given method name.
     * A node must declare at least one specialization and must be annotated with
     * {@link Reflectable} otherwise an {@link IllegalArgumentException} is thrown. If multiple
     * specializations with the same method name are declared then an undefined specialization is
     * going to be returned. In such cases disambiguate them by renaming the specialzation method
     * name. The returned reflection information is not updated when the state of the given
     * operation node is updated. The implementation of this method might be slow, do not use it in
     * performance critical code.
     *
     * @param node a reflectable DSL operation with at least one specialization
     * @param methodName the method name of thes specialization to reflect
     * @return reflection info for the method
     * @since 0.22
     */
    public static ReflectedSpecialization getSpecialization(Node node, String methodName) {
        for (Object object : getReflectionData(node)) {
            Object[] fieldData = getSpecializationData(object);
            if (methodName.equals(fieldData[0])) {
                return createSpecialization(getSpecializationData(object));
            }
        }
        return null;
    }

    /**
     * Returns reflection information for all declared specializations as unmodifiable list. A given
     * node must declare at least one specialization and must be annotated with {@link Reflectable}
     * otherwise an {@link IllegalArgumentException} is thrown. The returned reflection information
     * is not updated when the state of the given operation node is updated. The implementation of
     * this method might be slow, do not use it in performance critical code.
     *
     * @param node a reflectable DSL operation with at least one specialization
     * @since 0.22
     */
    public static List<ReflectedSpecialization> getSpecializations(Node node) {
        List<ReflectedSpecialization> specializations = new ArrayList<>();
        for (Object object : getReflectionData(node)) {
            specializations.add(createSpecialization(getSpecializationData(object)));
        }
        return Collections.unmodifiableList(specializations);
    }

    @SuppressWarnings("unchecked")
    private static ReflectedSpecialization createSpecialization(Object[] fieldData) {
        String id = (String) fieldData[0];
        byte state = (byte) fieldData[1];
        List<List<Object>> cachedData = (List<List<Object>>) fieldData[2];
        if (cachedData == null || cachedData.isEmpty()) {
            if ((state & 0b01) != 0) {
                cachedData = EMPTY_CACHED;
            } else {
                cachedData = NO_CACHED;
            }
        } else {
            for (int i = 0; i < cachedData.size(); i++) {
                cachedData.set(i, Collections.unmodifiableList(cachedData.get(i)));
            }
        }
        ReflectedSpecialization s = new ReflectedSpecialization(id, state, cachedData);
        return s;
    }

    private static Object[] getSpecializationData(Object specializationData) {
        if (!(specializationData instanceof Object[])) {
            throw new IllegalStateException("Invalid reflection data.");
        }
        Object[] fieldData = (Object[]) specializationData;
        if (fieldData.length < 3 || !(fieldData[0] instanceof String) //
                        || !(fieldData[1] instanceof Byte) //
                        || (fieldData[2] != null && !(fieldData[2] instanceof List))) {
            throw new IllegalStateException("Invalid reflection data.");
        }
        return fieldData;
    }

    private static Object[] getReflectionData(Node node) {
        if (!(node instanceof ReflectionAccesor)) {
            throw new IllegalArgumentException(String.format("Provided node is not reflectable. Annotate with @%s to make a node reflectable.", Reflectable.class.getSimpleName()));
        }
        Object data = ((ReflectionAccesor) node).getReflectionData();
        if (!(data instanceof Object[])) {
            throw new IllegalStateException("Invalid reflection data.");
        }
        Object[] arrayData = (Object[]) data;
        return arrayData;
    }

    /**
     * Represents dynamic reflection information of a specialization of a DSL operation.
     *
     * @since 0.22
     */
    public static final class ReflectedSpecialization {

        private final String methodName;
        private final byte state; /* 0b000000<excluded><active> */
        private final List<List<Object>> cachedData;

        private ReflectedSpecialization(String methodName, byte state, List<List<Object>> cachedData) {
            this.methodName = methodName;
            this.state = state;
            this.cachedData = cachedData;
        }

        /**
         * Returns the method name of the reflected specialization. Please note that the returned
         * method name might not be unique for a given node.
         *
         * @since 0.22
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * Returns <code>true</code> if the specialization was active at the time when the
         * reflection was performed.
         *
         * @since 0.22
         */
        public boolean isActive() {
            return (state & 0b1) != 0;
        }

        /**
         * Returns <code>true</code> if the specialization was excluded at the time when the
         * reflection was performed.
         *
         * @since 0.22
         */
        public boolean isExcluded() {
            return (state & 0b10) != 0;
        }

        /**
         * Returns the number of dynamic specialization instances that are active of this
         * specialization.
         *
         * @since 0.22
         */
        public int getInstances() {
            return cachedData.size();
        }

        /**
         * Returns the cached state for a given specialization instance. The provided instance index
         * must be greater or equal <code>0</code> and smaller {@link #getInstances()}. The returned
         * list is unmodifiable and never <code>null</code>.
         *
         * @since 0.22
         */
        public List<Object> getCachedData(int instanceIndex) {
            if (instanceIndex < 0 || instanceIndex >= cachedData.size()) {
                throw new IllegalArgumentException("Invalid specialization index");
            }
            return cachedData.get(instanceIndex);
        }

    }

}

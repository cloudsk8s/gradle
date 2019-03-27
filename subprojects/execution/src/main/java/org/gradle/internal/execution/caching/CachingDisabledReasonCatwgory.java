/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.execution.caching;

public enum CachingDisabledReasonCatwgory {
    /**
     * Reason for disabled caching is not known.
     */
    UNKNOWN,

    /**
     * Caching has not been enabled for the build.
     */
    BUILD_CACHE_DISABLED,

    /**
     * Caching has not been enabled for the work.
     */
    NOT_CACHEABLE,

    /**
     * Condition enabling caching isn't satisfied.
     */
    ENABLE_CONDITION_NOT_SATISFIED,

    /**
     * Condition disabling caching satisfied.
     */
    DISABLE_CONDITION_SATISFIED,

    /**
     * The work has no outputs declared.
     */
    NO_OUTPUTS_DECLARED,

    /**
     * Work has declared output that is not cacheable.
     */
    NON_CACHEABLE_OUTPUT,

    /**
     * Work's outputs overlap with other work's.
     */
    OVERLAPPING_OUTPUTS,

    /**
     * The work's implementation is not cacheable.
     *
     * Reasons for non-cacheable implementations:
     * <ul>
     *     <li>the type is loaded via an unknown classloader,</li>
     *     <li>a Java lambda was used (see https://github.com/gradle/gradle/issues/5510).</li>
     * </ul>
     */
    // TODO Link to user guide instead of issue
    NON_CACHEABLE_IMPLEMENTATION,

    /**
     * Additional implementation is not cacheable. Reasons for non-cacheable task action:
     *
     * Reasons for non-cacheable implementations:
     * <ul>
     *     <li>the type is loaded via an unknown classloader,</li>
     *     <li>a Java lambda was used (see https://github.com/gradle/gradle/issues/5510).</li>
     * </ul>
     */
    // TODO Link to user guide instead of issue
    NON_CACHEABLE_ADDITIONAL_IMPLEMENTATION,

    /**
     * One of the work's inputs is not cacheable.
     *
     * Reasons for non-cacheable inputs:
     * <ul>
     *     <li>some type used as an input is loaded via an unknown classloader,</li>
     *     <li>a Java lambda was used as an input (see https://github.com/gradle/gradle/issues/5510).</li>
     * </ul>
     */
    // TODO Link to user guide instead of issue
    NON_CACHEABLE_INPUTS
}

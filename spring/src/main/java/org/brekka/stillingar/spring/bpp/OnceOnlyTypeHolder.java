/*
 * Copyright 2012 the original author or authors.
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

package org.brekka.stillingar.spring.bpp;

/**
 * Used by {@link ConfigurationBeanPostProcessor} to hold the type of a bean being configured by a value group. Used
 * when there is no actual object instance available to be captured with the group, but we still need to the type
 * information.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
class OnceOnlyTypeHolder {
    /**
     * The type of the target class being configured.
     */
    private final Class<?> target;

    /**
     * @param target
     *            The type of the target class being configured.
     */
    public OnceOnlyTypeHolder(Class<?> target) {
        this.target = target;
    }

    /**
     * Retrieve the type.
     * 
     * @return the type
     */
    public Class<?> get() {
        return target;
    }
}

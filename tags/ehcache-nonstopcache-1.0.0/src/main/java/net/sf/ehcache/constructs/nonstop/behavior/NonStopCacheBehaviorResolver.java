/**
 *  Copyright 2003-2010 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.sf.ehcache.constructs.nonstop.behavior;

import net.sf.ehcache.constructs.nonstop.NonStopCacheBehavior;

/**
 * Interface for resolving a {@link NonStopCacheBehavior}
 * 
 * @author Abhishek Sanoujam
 * 
 */
public interface NonStopCacheBehaviorResolver {

    /**
     * Resolve the {@link NonStopCacheBehavior} to be used
     * 
     * @return the resolved {@link NonStopCacheBehavior}
     */
    NonStopCacheBehavior resolveBehavior();

}

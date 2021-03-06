/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.local;

import org.gradle.api.Incubating;
import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;


/**
 * Configuration object for the local directory build cache.
 *
 * @since 3.5
 */
@Incubating
public class DirectoryBuildCache extends AbstractBuildCache {
    private Object directory;
    private int removeUnusedEntriesAfterDays = 7;

    /**
     * Returns the directory to use to store the build cache.
     */
    @Nullable
    public Object getDirectory() {
        return directory;
    }

    /**
     * Sets the directory to use to store the build cache.
     *
     * The directory is evaluated as per {@code Project.file(Object)}.
     */
    public void setDirectory(Object directory) {
        this.directory = directory;
    }

    /**
     * Used to be the target size of the build cache in megabytes, now returns {@code 0}.
     *
     * @deprecated Use {@link #getRemoveUnusedEntriesAfterDays()} instead.
     */
    @Deprecated
    public long getTargetSizeInMB() {
        DeprecationLogger.nagUserOfReplacedMethod("DirectoryBuildCache.getTargetSizeInMB()", "getRemoveUnusedEntriesAfterDays()");
        return 0;
    }

    /**
     * Used to set the target size of the build cache in megabytes, now has no effect.
     *
     * @deprecated Use {@link #setRemoveUnusedEntriesAfterDays(int)} instead.
     */
    @Deprecated
    public void setTargetSizeInMB(long targetSizeInMB) {
        DeprecationLogger.nagUserOfReplacedMethod("DirectoryBuildCache.setTargetSizeInMB()", "setRemoveUnusedEntriesAfterDays()");
    }

    /**
     * Returns the number of days after unused entries are garbage collected. Defaults to 7 days.
     *
     * @since 4.6
     */
    public int getRemoveUnusedEntriesAfterDays() {
        return removeUnusedEntriesAfterDays;
    }

    /**
     * Sets the number of days after unused entries are garbage collected. Defaults to 7 days.
     *
     * Must be greater than 1.
     *
     * @since 4.6
     */
    public void setRemoveUnusedEntriesAfterDays(int removeUnusedEntriesAfterDays) {
        if (removeUnusedEntriesAfterDays < 1) {
            throw new IllegalArgumentException("Directory build cache needs to retain entries for at least a day.");
        }
        this.removeUnusedEntriesAfterDays = removeUnusedEntriesAfterDays;
    }
}

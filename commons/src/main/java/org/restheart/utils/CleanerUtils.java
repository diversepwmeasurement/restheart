/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
 * %%
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
 * =========================LICENSE_END==================================
 */

package org.restheart.utils;

import java.lang.ref.Cleaner;

public class CleanerUtils {
    private static CleanerUtils instance = null;
    private Cleaner cleaner;

    private CleanerUtils() {
        this.cleaner = Cleaner.create();
    }

    public static CleanerUtils get() {
        if (instance == null) {
            instance = new CleanerUtils();
        }
        return instance;
    }

    public Cleaner cleaner() {
        return cleaner;
    }
}

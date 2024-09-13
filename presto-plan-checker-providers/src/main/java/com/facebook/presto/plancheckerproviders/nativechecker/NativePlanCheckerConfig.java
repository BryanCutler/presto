/*
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
package com.facebook.presto.plancheckerproviders.nativechecker;

import com.facebook.airlift.configuration.Config;
import com.facebook.airlift.configuration.ConfigDescription;

public class NativePlanCheckerConfig
{
    public static final String CONFIG_PREFIX = "native-plan-checker";
    private boolean enabled;

    public boolean isPlanValidationEnabled()
    {
        return enabled;
    }

    @Config("plan-validation-enabled")
    @ConfigDescription("Set true to enable native plan validation")
    public NativePlanCheckerConfig setPlanValidationEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }
}

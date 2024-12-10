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
package com.facebook.presto.governance;

import com.facebook.presto.governance.security.testing.TestingGovernanceAccessControlFactory;
import com.facebook.presto.governance.security.testing.TestingGovernanceSystemAccessControl;
import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.security.SystemAccessControlFactory;
import com.google.common.collect.ImmutableList;

import java.util.Collections;

public class GovernancePlugin
    implements Plugin
{
    private static boolean areFactoriesInitialized;

    public static void initializeAccessControlFactories()
    {
        // TODO: better way to initialize?
        if (!areFactoriesInitialized) {
            TestingGovernanceAccessControlFactory testingGovernanceAccessControlFactory = new TestingGovernanceAccessControlFactory();
            GovernanceManager.getInstance().addGovernanceAccessControlFactory(testingGovernanceAccessControlFactory);
            GovernanceManager.getInstance().setGovernanceAccessControl(testingGovernanceAccessControlFactory.getName(), Collections.emptyMap());
            areFactoriesInitialized = true;
        }
    }

    @Override
    public Iterable<SystemAccessControlFactory> getSystemAccessControlFactories()
    {
        initializeAccessControlFactories();
        return ImmutableList.of(new TestingGovernanceSystemAccessControl.Factory());
    }
}

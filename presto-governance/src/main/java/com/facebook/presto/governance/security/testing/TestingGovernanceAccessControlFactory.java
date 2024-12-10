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
package com.facebook.presto.governance.security.testing;

import com.facebook.presto.governance.GovernanceAccessControlFactory;
import com.facebook.presto.governance.security.GovernanceConnectorAccessControl;
import com.facebook.presto.plugin.base.security.AllowAllAccessControl;

import java.util.Map;

public class TestingGovernanceAccessControlFactory
    implements GovernanceAccessControlFactory
{
    @Override
    public String getName()
    {
        return "TESTING";
    }

    @Override
    public GovernanceConnectorAccessControl create(Map<String, String> config)
    {
        return new TestingGovernanceConnectorAccessControl(new AllowAllAccessControl());
    }
}

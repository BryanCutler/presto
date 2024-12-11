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

import com.facebook.presto.common.Subfield;
import com.facebook.presto.governance.security.GovernanceConnectorAccessControl;
import com.facebook.presto.governance.security.ViewExpression;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.connector.ConnectorAccessControl;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.plan.PlanNode;
import com.facebook.presto.spi.security.AccessControlContext;
import com.facebook.presto.spi.security.ConnectorIdentity;
import com.facebook.presto.spi.security.PrestoPrincipal;
import com.facebook.presto.spi.security.Privilege;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public class TestingGovernanceConnectorAccessControl
        extends GovernanceConnectorAccessControl
{
    public TestingGovernanceConnectorAccessControl(ConnectorAccessControl delegate)
    {
        super(delegate);
    }

    @Override
    public RowFilterData getRowFilterData()
    {
        return new TestingRowFilterData();
    }

    @Override
    public ColumnMaskingData getColumnMaskingData()
    {
        return new TestingColumnMaskingData();
    }

    public static class TestingRowFilterData
            extends RowFilterData
    {
        @Override
        protected List<ViewExpression> getRowFilterExpressions(PlanNode planNode)
        {
            return Collections.emptyList();
        }
    }

    public static class TestingColumnMaskingData
            extends ColumnMaskingData
    {
        @Override
        protected  Map<String, ViewExpression> getColumnMaskingExpressions(PlanNode planNode)
        {
            return Collections.emptyMap();
        }
    }
}

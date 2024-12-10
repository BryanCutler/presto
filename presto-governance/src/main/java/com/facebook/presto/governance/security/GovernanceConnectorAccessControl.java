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
package com.facebook.presto.governance.security;

import com.facebook.presto.plugin.base.security.ForwardingConnectorAccessControl;
import com.facebook.presto.spi.connector.ConnectorAccessControl;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public abstract class GovernanceConnectorAccessControl
        extends ForwardingConnectorAccessControl
{
    protected ConnectorAccessControl delegate;

    /*public static ConnectorAccessControl of(Supplier<ConnectorAccessControl> connectorAccessControlSupplier)
    {
        requireNonNull(connectorAccessControlSupplier, "connectorAccessControlSupplier is null");
        return new GovernanceConnectorAccessControl()
        {
            @Override
            protected ConnectorAccessControl delegate()
            {
                return connectorAccessControlSupplier.get();
            }
        };
    }*/

    public GovernanceConnectorAccessControl(ConnectorAccessControl delegate)
    {
        this.delegate = delegate;
    }

    @Override
    protected ConnectorAccessControl delegate()
    {
        return delegate;
    }

    public abstract RowFilterData getRowFilterData();

    public abstract ColumnMaskingData getColumnMaskingData();

    public static abstract class RowFilterData
    {
        protected boolean isEnabled()
        {
            return true;
        }

        protected abstract List<ViewExpression> getRowFilterExpressions();
    }

    public static abstract class ColumnMaskingData
    {
        protected boolean isEnabled()
        {
            return true;
        }
        protected abstract Map<String, ViewExpression> getColumnMaskingExpressions();
    }
}

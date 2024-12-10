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

import com.facebook.airlift.log.Logger;
import com.facebook.presto.common.QualifiedObjectName;
import com.facebook.presto.governance.security.GovernanceConnectorAccessControl;
import com.facebook.presto.governance.security.ViewExpression;
import com.facebook.presto.plugin.base.security.AllowAllAccessControl;
import com.facebook.presto.spi.connector.ConnectorAccessControl;
import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class GovernanceManager
{
    private static final Logger log = Logger.get(GovernanceManager.class);

    private final Map<String, GovernanceAccessControlFactory> governanceAccessControlFactories = new ConcurrentHashMap<>();
    private final AtomicBoolean accessControlLoading = new AtomicBoolean();
    private final GovernanceConnectorAccessControlWrapper accessControlWrapper;

    private static class Holder {
        static final GovernanceManager INSTANCE = new GovernanceManager();
    }
    private GovernanceManager()
    {
        this.accessControlWrapper = new GovernanceConnectorAccessControlWrapper();
    }

    public static GovernanceManager getInstance()
    {
        return Holder.INSTANCE;
    }

    public void addGovernanceAccessControlFactory(GovernanceAccessControlFactory accessControlFactory)
    {
        requireNonNull(accessControlFactory, "accessControlFactory is null");

        if (governanceAccessControlFactories.putIfAbsent(accessControlFactory.getName(), accessControlFactory) != null) {
            throw new IllegalArgumentException(format("Governance access control '%s' is already registered", accessControlFactory.getName()));
        }
    }

    public void setGovernanceAccessControl(String name, Map<String, String> properties)
    {
        Preconditions.checkState(accessControlLoading.compareAndSet(false, true), "Governance access control already initialized");

        log.info("-- Loading governance access control --");

        GovernanceAccessControlFactory accessControlFactory = governanceAccessControlFactories.get(name);
        checkState(accessControlFactory != null, "Governance access control %s is not registered", name);

        GovernanceConnectorAccessControl connectorAccessControl = accessControlFactory.create(properties);
        this.accessControlWrapper.setGovernanceAccessControl(connectorAccessControl);

        // TODO: register with AccessControlManager???

        log.info("-- Loaded governance access control %s --", name);
    }

    public GovernanceConnectorAccessControl getGovernanceConnectorAccessControl()
    {
        return accessControlWrapper;
    }

    private final Map<RowFilterKey, List<ViewExpression>> rowFilters = new HashMap<>();
    private final Map<ColumnMaskKey, List<ViewExpression>> columnMasks = new HashMap<>();

    public void rowFilter(QualifiedObjectName table, String identity, ViewExpression filter)
    {
        rowFilters.computeIfAbsent(new RowFilterKey(identity, table), key -> new ArrayList<>())
                .add(filter);
    }

    public void columnMask(QualifiedObjectName table, String column, String identity, ViewExpression mask)
    {
        columnMasks.computeIfAbsent(new ColumnMaskKey(identity, table, column), key -> new ArrayList<>())
                .add(mask);
    }

    public void reset()
    {
        rowFilters.clear();
        columnMasks.clear();
    }

    private static class RowFilterKey
    {
        private final String identity;
        private final QualifiedObjectName table;

        public RowFilterKey(String identity, QualifiedObjectName table)
        {
            this.identity = requireNonNull(identity, "identity is null");
            this.table = requireNonNull(table, "table is null");
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RowFilterKey that = (RowFilterKey) o;
            return identity.equals(that.identity) &&
                    table.equals(that.table);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(identity, table);
        }
    }

    private static class ColumnMaskKey
    {
        private final String identity;
        private final QualifiedObjectName table;
        private final String column;

        public ColumnMaskKey(String identity, QualifiedObjectName table, String column)
        {
            this.identity = identity;
            this.table = table;
            this.column = column;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ColumnMaskKey that = (ColumnMaskKey) o;
            return identity.equals(that.identity) &&
                    table.equals(that.table) &&
                    column.equals(that.column);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(identity, table, column);
        }
    }

    private static class GovernanceConnectorAccessControlWrapper
            extends GovernanceConnectorAccessControl
    {
        public GovernanceConnectorAccessControlWrapper()
        {
            super(new NonGovernedConnectorAccessControl());
        }

        public void setGovernanceAccessControl(GovernanceConnectorAccessControl accessControl)
        {
            this.delegate = accessControl;
        }

        @Override
        public GovernanceConnectorAccessControl delegate()
        {
            return (GovernanceConnectorAccessControl) delegate;
        }

        @Override
        public RowFilterData getRowFilterData()
        {
            return delegate().getRowFilterData();
        }

        @Override
        public ColumnMaskingData getColumnMaskingData()
        {
            return delegate().getColumnMaskingData();
        }
    }

    private static class NonGovernedConnectorAccessControl
            extends GovernanceConnectorAccessControl
    {
        public NonGovernedConnectorAccessControl()
        {
            super(new AllowAllAccessControl());
        }

        @Override
        public RowFilterData getRowFilterData()
        {
            return new RowFilterData()
            {
                @Override
                protected boolean isEnabled()
                {
                    return false;
                }

                @Override
                protected List<ViewExpression> getRowFilterExpressions()
                {
                    return Collections.emptyList();
                }
            };
        }

        @Override
        public ColumnMaskingData getColumnMaskingData()
        {
            return new ColumnMaskingData()
            {
                @Override
                protected boolean isEnabled()
                {
                    return false;
                }

                @Override
                protected Map<String, ViewExpression> getColumnMaskingExpressions()
                {
                    return Collections.emptyMap();
                }
            };
        }
    }
}

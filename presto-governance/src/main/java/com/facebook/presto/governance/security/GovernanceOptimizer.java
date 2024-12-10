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

import com.facebook.presto.spi.ConnectorPlanOptimizer;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.TableHandle;
import com.facebook.presto.spi.VariableAllocator;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.function.FunctionMetadataManager;
import com.facebook.presto.spi.function.StandardFunctionResolution;
import com.facebook.presto.spi.plan.PlanNode;
import com.facebook.presto.spi.plan.PlanNodeIdAllocator;
import com.facebook.presto.spi.relation.RowExpressionService;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static com.facebook.presto.spi.ConnectorPlanRewriter.rewriteWith;

public class GovernanceOptimizer
    implements ConnectorPlanOptimizer
{
    private final GovernanceConnectorAccessControl accessControl;
    private final RowExpressionService rowExpressionService;
    private final StandardFunctionResolution functionResolution;
    private final FunctionMetadataManager functionMetadataManager;
    protected final Function<TableHandle, ConnectorMetadata> transactionToMetadata;

    public GovernanceOptimizer(
            StandardFunctionResolution functionResolution,
            RowExpressionService rowExpressionService,
            FunctionMetadataManager functionMetadataManager,
            Function<TableHandle, ConnectorMetadata> transactionToMetadata,
            GovernanceConnectorAccessControl accessControl)
    {
        this.accessControl = requireNonNull(accessControl, "accessControl is null");
        this.functionResolution = requireNonNull(functionResolution, "functionResolution is null");
        this.rowExpressionService = requireNonNull(rowExpressionService, "rowExpressionService is null");
        this.functionMetadataManager = requireNonNull(functionMetadataManager, "functionMetadataManager is null");
        this.transactionToMetadata = requireNonNull(transactionToMetadata, "transactionToMetadata is null");
    }

    @Override
    public PlanNode optimize(PlanNode maxSubplan, ConnectorSession session, VariableAllocator variableAllocator, PlanNodeIdAllocator idAllocator)
    {
        PlanNode planNode = maxSubplan;

        GovernanceConnectorAccessControl.RowFilterData rowFilterData = accessControl.getRowFilterData();
        if (rowFilterData != null && rowFilterData.isEnabled()) {
            planNode = rewriteWith(
                    new RowFilterRewriter(functionResolution, rowExpressionService, functionMetadataManager, transactionToMetadata, session, variableAllocator, idAllocator),
                    planNode,
                    rowFilterData);
        }

        GovernanceConnectorAccessControl.ColumnMaskingData columnMaskingData = accessControl.getColumnMaskingData();
        if (columnMaskingData != null && columnMaskingData.isEnabled()) {
            planNode = rewriteWith(
                    new ColumnMaskingRewriter(functionResolution, rowExpressionService, functionMetadataManager, transactionToMetadata, session, variableAllocator, idAllocator),
                    planNode,
                    columnMaskingData);
        }

        return planNode;
    }
}

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

import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.ConnectorPlanRewriter;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.TableHandle;
import com.facebook.presto.spi.VariableAllocator;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.function.FunctionMetadataManager;
import com.facebook.presto.spi.function.StandardFunctionResolution;
import com.facebook.presto.spi.plan.Assignments;
import com.facebook.presto.spi.plan.FilterNode;
import com.facebook.presto.spi.plan.PlanNode;
import com.facebook.presto.spi.plan.PlanNodeIdAllocator;
import com.facebook.presto.spi.plan.ProjectNode;
import com.facebook.presto.spi.plan.TableScanNode;
import com.facebook.presto.spi.relation.RowExpression;
import com.facebook.presto.spi.relation.RowExpressionService;
import com.facebook.presto.spi.relation.VariableReferenceExpression;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class ColumnMaskingRewriter
        extends ConnectorPlanRewriter<GovernanceConnectorAccessControl.ColumnMaskingData>
{
    protected final RowExpressionService rowExpressionService;
    protected final Function<TableHandle, ConnectorMetadata> transactionToMetadata;
    private final ConnectorSession session;
    private final VariableAllocator variableAllocator;
    private final PlanNodeIdAllocator idAllocator;
    private final StandardFunctionResolution functionResolution;
    private final FunctionMetadataManager functionMetadataManager;

    public ColumnMaskingRewriter(
            StandardFunctionResolution functionResolution,
            RowExpressionService rowExpressionService,
            FunctionMetadataManager functionMetadataManager,
            Function<TableHandle, ConnectorMetadata> transactionToMetadata,
            ConnectorSession session,
            VariableAllocator variableAllocator,
            PlanNodeIdAllocator idAllocator)
    {
        this.functionResolution = requireNonNull(functionResolution, "functionResolution is null");
        this.rowExpressionService = requireNonNull(rowExpressionService, "rowExpressionService is null");
        this.functionMetadataManager = requireNonNull(functionMetadataManager, "functionMetadataManager is null");
        this.transactionToMetadata = requireNonNull(transactionToMetadata, "transactionToMetadata is null");
        this.session = requireNonNull(session, "session is null");
        this.variableAllocator = requireNonNull(variableAllocator, "variableAllocator is null");
        this.idAllocator = requireNonNull(idAllocator, "idAllocator is null");
    }

    @Override
    public PlanNode visitFilter(FilterNode filter, RewriteContext<GovernanceConnectorAccessControl.ColumnMaskingData> context)
    {
        if (!(filter.getSource() instanceof TableScanNode)) {
            return visitPlan(filter, context);
        }

        return visitPlan(filter, context);

        /*TableScanNode tableScan = (TableScanNode) filter.getSource();
        if (!isPushdownFilterSupported(session, tableScan.getTable())) {
            return filter;
        }

        RowExpression expression = filter.getPredicate();
        TableHandle handle = tableScan.getTable();
        ConnectorMetadata metadata = transactionToMetadata.apply(handle);

        BiMap<VariableReferenceExpression, VariableReferenceExpression> symbolToColumnMapping =
                tableScan.getAssignments().entrySet().stream().collect(toImmutableBiMap(
                        Map.Entry::getKey,
                        entry -> new VariableReferenceExpression(
                        Optional.empty(),
                        getColumnName(session, metadata, handle.getConnectorHandle(), entry.getValue()),
                        entry.getKey().getType())));

        RowExpression replacedExpression = replaceExpression(expression, symbolToColumnMapping);
        // replaceExpression() may further optimize the expression;
        // if the resulting expression is always false, then return empty Values node
        if (FALSE_CONSTANT.equals(replacedExpression)) {
            return getValuesNode(tableScan);
        }
        ConnectorPushdownFilterResult pushdownFilterResult = pushdownFilter(
                session,
                metadata,
                handle.getConnectorHandle(),
                replacedExpression,
                handle.getLayout());

        ConnectorTableLayout layout = pushdownFilterResult.getLayout();
        if (layout.getPredicate().isNone()) {
            return getValuesNode(tableScan);
        }

        TableScanNode node = getTableScanNode(tableScan, handle, pushdownFilterResult);

        RowExpression unenforcedFilter = pushdownFilterResult.getUnenforcedConstraint();
        if (!TRUE_CONSTANT.equals(unenforcedFilter)) {
            return new FilterNode(
                    tableScan.getSourceLocation(),
                    idAllocator.getNextId(),
                    node,
                    replaceExpression(unenforcedFilter, symbolToColumnMapping.inverse()));
        }

        return node;
        */
    }

    @Override
    public PlanNode visitTableScan(TableScanNode tableScan, RewriteContext<GovernanceConnectorAccessControl.ColumnMaskingData> context)
    {
        Map<VariableReferenceExpression, RowExpression> maskingExpressions = Collections.emptyMap();// = getRowFilterExpressions(session, tableScan);

        if (maskingExpressions.isEmpty()) {
            return tableScan;
        }

        Map<VariableReferenceExpression, RowExpression> maskingAssignments = new HashMap<>();
        for (Map.Entry<VariableReferenceExpression, ColumnHandle> assigment : tableScan.getAssignments().entrySet()) {
            VariableReferenceExpression variable = assigment.getKey();
            maskingAssignments.put(variable, maskingExpressions.getOrDefault(variable, variable));
        }

        //////////////////////////////////////////////

        /*
        Map<String, List<Expression>> columnMasks = analysis.getColumnMasks(table);

        PlanNode root = plan.getRoot();
        List<VariableReferenceExpression> mappings = plan.getFieldMappings();

        TranslationMap translations = new TranslationMap(plan, analysis, lambdaDeclarationToVariableMap);
        translations.setFieldMappings(mappings);

        PlanBuilder planBuilder = new PlanBuilder(translations, root);

        for (int i = 0; i < plan.getDescriptor().getAllFieldCount(); i++) {
            Field field = plan.getDescriptor().getFieldByIndex(i);

            for (Expression mask : columnMasks.getOrDefault(field.getName().get(), ImmutableList.of())) {
                planBuilder = subqueryPlanner.handleSubqueries(planBuilder, mask, mask);

                Map<VariableReferenceExpression, RowExpression> assignments = new LinkedHashMap<>();
                for (VariableReferenceExpression variable : root.getOutputVariables()) {
                    assignments.put(variable, castToRowExpression(createSymbolReference(variable)));
                }
                assignments.put(mappings.get(i), castToRowExpression(translations.rewrite(mask)));

                planBuilder = planBuilder.withNewRoot(new ProjectNode(
                        idAllocator.getNextId(),
                        planBuilder.getRoot(),
                        Assignments.copyOf(assignments)));
            }
        }
         */
        //////////////////////////////////////////////

        return new ProjectNode(
                tableScan.getSourceLocation(),
                idAllocator.getNextId(),
                tableScan,
                new Assignments(maskingAssignments),
                ProjectNode.Locality.LOCAL);

        /*
        TableHandle handle = tableScan.getTable();
        ConnectorMetadata metadata = transactionToMetadata.apply(handle);
        ConnectorPushdownFilterResult pushdownFilterResult = pushdownFilter(
                session,
                metadata,
                handle.getConnectorHandle(),
                TRUE_CONSTANT,
                handle.getLayout());
        if (pushdownFilterResult.getLayout().getPredicate().isNone()) {
            return getValuesNode(tableScan);
        }

        TableScanNode node = getTableScanNode(tableScan, handle, pushdownFilterResult);

        RowExpression unenforcedFilter = pushdownFilterResult.getUnenforcedConstraint();
        if (!TRUE_CONSTANT.equals(unenforcedFilter)) {
            throw new PrestoException(
                    GENERIC_INTERNAL_ERROR,
                    format("Unenforced filter found %s but not handled", unenforcedFilter));
        }

        return node;

         */
    }
}


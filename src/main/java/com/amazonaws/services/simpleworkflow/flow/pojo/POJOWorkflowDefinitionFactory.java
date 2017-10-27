/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazonaws.services.simpleworkflow.flow.pojo;

import java.util.Map;
import com.amazonaws.services.simpleworkflow.flow.DataConverter;
import com.amazonaws.services.simpleworkflow.flow.DecisionContext;
import com.amazonaws.services.simpleworkflow.flow.JsonDataConverter;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinition;
import com.amazonaws.services.simpleworkflow.flow.generic.WorkflowDefinitionFactory;
import com.amazonaws.services.simpleworkflow.flow.worker.CurrentDecisionContext;
import com.uber.cadence.WorkflowType;

class POJOWorkflowDefinitionFactory extends WorkflowDefinitionFactory {

    private final DataConverter converter = new JsonDataConverter();

    private final WorkflowType workflowType;

    private final POJOWorkflowImplementationFactory implementationFactory;

    private final MethodConverterPair workflowImplementationMethod;

    private final MethodConverterPair getStateMethod;

    private final Map<String, MethodConverterPair> signals;

    private final Object[] constructorArgs;

    public POJOWorkflowDefinitionFactory(POJOWorkflowImplementationFactory implementationFactory, WorkflowType workflowType,
            MethodConverterPair workflowImplementationMethod,
            Map<String, MethodConverterPair> signals, MethodConverterPair getStateMethod, Object[] constructorArgs) {
        this.implementationFactory = implementationFactory;
        this.workflowType = workflowType;
        this.workflowImplementationMethod = workflowImplementationMethod;
        this.signals = signals;
        this.getStateMethod = getStateMethod;
        this.constructorArgs = constructorArgs;
    }

    @Override
    public WorkflowType getWorkflowType() {
        return workflowType;
    }

    @Override
    public WorkflowDefinition getWorkflowDefinition(DecisionContext context) throws Exception {
        if (implementationFactory == null) {
            return null;
        }
        CurrentDecisionContext.set(context);
        Object workflowDefinitionObject;
        if (constructorArgs == null) {
            workflowDefinitionObject = implementationFactory.newInstance(context);
        }
        else {
            workflowDefinitionObject = implementationFactory.newInstance(context, constructorArgs);
        }
        return new POJOWorkflowDefinition(workflowDefinitionObject, workflowImplementationMethod, signals, getStateMethod,
                converter, context);
    }

    @Override
    public void deleteWorkflowDefinition(WorkflowDefinition instance) {
        POJOWorkflowDefinition definition = (POJOWorkflowDefinition) instance;
        implementationFactory.deleteInstance(definition.getImplementationInstance());
        CurrentDecisionContext.unset();
    }
}

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
package com.amazonaws.services.simpleworkflow.flow.generic;

import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions;
import com.amazonaws.services.simpleworkflow.flow.common.FlowConstants;
import com.uber.cadence.ChildPolicy;
import com.uber.cadence.WorkflowType;

public class StartWorkflowExecutionParameters {

    private String workflowId;
    
    private WorkflowType workflowType;
    
    private String taskList;
    
    private byte[] input;
    
    private int executionStartToCloseTimeoutSeconds = FlowConstants.USE_REGISTERED_DEFAULTS;
    
    private int taskStartToCloseTimeoutSeconds = FlowConstants.USE_REGISTERED_DEFAULTS;
    
    private java.util.List<String> tagList;
    
    private int taskPriority;

    private String lambdaRole;

    private ChildPolicy childPolicy;

    /**
     * Returns the value of the WorkflowId property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>1 - 64<br/>
     *
     * @return The value of the WorkflowId property for this object.
     */
    public String getWorkflowId() {
        return workflowId;
    }
    
    /**
     * Sets the value of the WorkflowId property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>1 - 64<br/>
     *
     * @param workflowId The new value for the WorkflowId property for this object.
     */
    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }
    
    /**
     * Sets the value of the WorkflowId property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>1 - 64<br/>
     *
     * @param workflowId The new value for the WorkflowId property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withWorkflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }
    
    
    /**
     * Returns the value of the WorkflowType property for this object.
     *
     * @return The value of the WorkflowType property for this object.
     */
    public WorkflowType getWorkflowType() {
        return workflowType;
    }
    
    /**
     * Sets the value of the WorkflowType property for this object.
     *
     * @param workflowType The new value for the WorkflowType property for this object.
     */
    public void setWorkflowType(WorkflowType workflowType) {
        this.workflowType = workflowType;
    }
    
    /**
     * Sets the value of the WorkflowType property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     *
     * @param workflowType The new value for the WorkflowType property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withWorkflowType(WorkflowType workflowType) {
        this.workflowType = workflowType;
        return this;
    }
    
    
    /**
     * Returns the value of the TaskList property for this object.
     *
     * @return The value of the TaskList property for this object.
     */
    public String getTaskList() {
        return taskList;
    }
    
    /**
     * Sets the value of the TaskList property for this object.
     *
     * @param taskList The new value for the TaskList property for this object.
     */
    public void setTaskList(String taskList) {
        this.taskList = taskList;
    }
    
    /**
     * Sets the value of the TaskList property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     *
     * @param taskList The new value for the TaskList property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withTaskList(String taskList) {
        this.taskList = taskList;
        return this;
    }
    
    
    /**
     * Returns the value of the Input property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 100000<br/>
     *
     * @return The value of the Input property for this object.
     */
    public byte[] getInput() {
        return input;
    }
    
    /**
     * Sets the value of the Input property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 100000<br/>
     *
     * @param input The new value for the Input property for this object.
     */
    public void setInput(byte[] input) {
        this.input = input;
    }
    
    /**
     * Sets the value of the Input property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 100000<br/>
     *
     * @param input The new value for the Input property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withInput(byte[] input) {
        this.input = input;
        return this;
    }
    
    
    /**
     * Returns the value of the StartToCloseTimeout property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 64<br/>
     *
     * @return The value of the StartToCloseTimeout property for this object.
     */
    public int getExecutionStartToCloseTimeout() {
        return executionStartToCloseTimeoutSeconds;
    }
    
    /**
     * Sets the value of the StartToCloseTimeout property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 64<br/>
     *
     * @param executionStartToCloseTimeoutSeconds The new value for the StartToCloseTimeout property for this object.
     */
    public void setExecutionStartToCloseTimeoutSeconds(int executionStartToCloseTimeoutSeconds) {
        this.executionStartToCloseTimeoutSeconds = executionStartToCloseTimeoutSeconds;
    }
    
    /**
     * Sets the value of the StartToCloseTimeout property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 64<br/>
     *
     * @param executionStartToCloseTimeoutSeconds The new value for the StartToCloseTimeout property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withExecutionStartToCloseTimeoutSeconds(int executionStartToCloseTimeoutSeconds) {
        this.executionStartToCloseTimeoutSeconds = executionStartToCloseTimeoutSeconds;
        return this;
    }
    
    public int getTaskStartToCloseTimeoutSeconds() {
        return taskStartToCloseTimeoutSeconds;
    }
    
    public void setTaskStartToCloseTimeoutSeconds(int taskStartToCloseTimeoutSeconds) {
        this.taskStartToCloseTimeoutSeconds = taskStartToCloseTimeoutSeconds;
    }
    
    public StartWorkflowExecutionParameters withTaskStartToCloseTimeoutSeconds(int taskStartToCloseTimeoutSeconds) {
        this.taskStartToCloseTimeoutSeconds = taskStartToCloseTimeoutSeconds;
        return this;
    }

    public ChildPolicy getChildPolicy() {
        return childPolicy;
    }
 
    public void setChildPolicy(ChildPolicy childPolicy) {
        this.childPolicy = childPolicy;
    }
    
    public StartWorkflowExecutionParameters withChildPolicy(ChildPolicy childPolicy) {
        this.childPolicy = childPolicy;
        return this;
    }
    
    /**
     * Returns the value of the TagList property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 5<br/>
     *
     * @return The value of the TagList property for this object.
     */
    public java.util.List<String> getTagList() {
        if (tagList == null) {
            tagList = new java.util.ArrayList<String>();
        }
        return tagList;
    }
    
    /**
     * Sets the value of the TagList property for this object.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 5<br/>
     *
     * @param tagList The new value for the TagList property for this object.
     */
    public void setTagList(java.util.Collection<String> tagList) {
        java.util.List<String> tagListCopy = new java.util.ArrayList<String>();
        if (tagList != null) {
            tagListCopy.addAll(tagList);
        }
        this.tagList = tagListCopy;
    }
    
    /**
     * Sets the value of the TagList property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 5<br/>
     *
     * @param tagList The new value for the TagList property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withTagList(String... tagList) {
        for (String value : tagList) {
            getTagList().add(value);
        }
        return this;
    }
    
    /**
     * Sets the value of the TagList property for this object.
     * <p>
     * Returns a reference to this object so that method calls can be chained together.
     * <p>
     * <b>Constraints:</b><br/>
     * <b>Length: </b>0 - 5<br/>
     *
     * @param tagList The new value for the TagList property for this object.
     *
     * @return A reference to this updated object so that method calls can be chained 
     *         together. 
     */
    public StartWorkflowExecutionParameters withTagList(java.util.Collection<String> tagList) {
        java.util.List<String> tagListCopy = new java.util.ArrayList<String>();
        if (tagList != null) {
            tagListCopy.addAll(tagList);
        }
        this.tagList = tagListCopy;

        return this;
    }
    
    public int getTaskPriority() {
        return taskPriority;
    }

    public void setTaskPriority(int taskPriority) {
        this.taskPriority = taskPriority;
    }

    public StartWorkflowExecutionParameters withTaskPriority(int taskPriority) {
        this.taskPriority = taskPriority;
        return this;
    }
    
    public String getLambdaRole() {
        return lambdaRole;
    }

    public void setLambdaRole(String lambdaRole) {
        this.lambdaRole = lambdaRole;
    }

    public StartWorkflowExecutionParameters withLambdaRole(String lambdaRole) {
        this.lambdaRole = lambdaRole;
        return this;
    }

    public StartWorkflowExecutionParameters createStartWorkflowExecutionParametersFromOptions(StartWorkflowOptions options, 
    		StartWorkflowOptions optionsOverride) {
    	StartWorkflowExecutionParameters parameters = this.clone();
    	
    	if (options != null) {
    		Integer executionStartToCloseTimeout = options.getExecutionStartToCloseTimeoutSeconds();
    		if (executionStartToCloseTimeout != null) {
    			parameters.setExecutionStartToCloseTimeoutSeconds(executionStartToCloseTimeout);
    		}
    		
    		Integer taskStartToCloseTimeout = options.getTaskStartToCloseTimeoutSeconds();
            if (taskStartToCloseTimeout != null) {
                parameters.setTaskStartToCloseTimeoutSeconds(taskStartToCloseTimeout);
            }
    		
    		java.util.Collection<String> tagList = options.getTagList();
    		if (tagList != null) {
    			parameters.setTagList(tagList);
    		}
    		
    		String taskList = options.getTaskList();
    		if (taskList != null && !taskList.isEmpty()) { 
    			parameters.setTaskList(taskList);
    		}
    		
            Integer taskPriority = options.getTaskPriority();
            if (taskPriority != null) {
                parameters.setTaskPriority(taskPriority);
            }

    		ChildPolicy childPolicy = options.getChildPolicy();
    		if (childPolicy != null) {
    		    parameters.setChildPolicy(childPolicy);
    		}

            String lambdaRole = options.getLambdaRole();
            if (lambdaRole != null && !lambdaRole.isEmpty()) {
                parameters.setLambdaRole(lambdaRole);
            }
        }
    	
    	if (optionsOverride != null) {
    	    Integer executionStartToCloseTimeout = optionsOverride.getExecutionStartToCloseTimeoutSeconds();
            if (executionStartToCloseTimeout != null) {
                parameters.setExecutionStartToCloseTimeoutSeconds(executionStartToCloseTimeout);
            }
            
            Integer taskStartToCloseTimeout = optionsOverride.getTaskStartToCloseTimeoutSeconds();
            if (taskStartToCloseTimeout != null) {
                parameters.setTaskStartToCloseTimeoutSeconds(taskStartToCloseTimeout);
            }
            
    		java.util.Collection<String> tagList = optionsOverride.getTagList();
    		if (tagList != null) {
    			parameters.setTagList(tagList);
    		}
    		
    		String taskList = optionsOverride.getTaskList();
    		if (taskList != null && !taskList.isEmpty()) { 
    			parameters.setTaskList(taskList);
    		}
    		
            Integer taskPriority = optionsOverride.getTaskPriority();
            if (taskPriority != null) {
                parameters.setTaskPriority(taskPriority);
            }

    		ChildPolicy childPolicy = optionsOverride.getChildPolicy();
    		if (childPolicy != null) {
    		    parameters.setChildPolicy(childPolicy);
    		}

            String lambdaRole = optionsOverride.getLambdaRole();
            if (lambdaRole != null && !lambdaRole.isEmpty()) {
                parameters.setLambdaRole(lambdaRole);
            }
        }
    	
    	return parameters;
    }
    
    /**
     * Returns a string representation of this object; useful for testing and
     * debugging.
     *
     * @return A string representation of this object.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("WorkflowId: " + workflowId + ", ");
        sb.append("WorkflowType: " + workflowType + ", ");
        sb.append("TaskList: " + taskList + ", ");
        sb.append("Input: " + input + ", ");
        sb.append("StartToCloseTimeout: " + executionStartToCloseTimeoutSeconds + ", ");
        sb.append("TagList: " + tagList + ", ");
        sb.append("TaskPriority: " + taskPriority + ", ");
        sb.append("ChildPolicy: " + childPolicy + ", ");
        sb.append("LambdaRole: " + lambdaRole + ", ");
        sb.append("}");
        return sb.toString();
    }
    
    public StartWorkflowExecutionParameters clone() {
    	StartWorkflowExecutionParameters result = new StartWorkflowExecutionParameters();
        result.setInput(input);
        result.setExecutionStartToCloseTimeoutSeconds(executionStartToCloseTimeoutSeconds);
        result.setTaskStartToCloseTimeoutSeconds(taskStartToCloseTimeoutSeconds);
        result.setTagList(tagList);
        result.setTaskList(taskList);
        result.setWorkflowId(workflowId);
        result.setWorkflowType(workflowType);
        result.setTaskPriority(taskPriority);
        result.setChildPolicy(childPolicy);
        result.setLambdaRole(lambdaRole);
        return result;
    }

}
    

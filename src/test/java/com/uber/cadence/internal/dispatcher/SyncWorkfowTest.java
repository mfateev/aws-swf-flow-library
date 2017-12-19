package com.uber.cadence.internal.dispatcher;

import com.amazonaws.services.simpleworkflow.flow.ActivityExecutionContext;
import com.amazonaws.services.simpleworkflow.flow.ActivityFailureException;
import com.amazonaws.services.simpleworkflow.flow.WorkflowExecutionAlreadyStartedException;
import com.amazonaws.services.simpleworkflow.flow.common.WorkflowExecutionUtils;
import com.amazonaws.services.simpleworkflow.flow.generic.ActivityImplementation;
import com.amazonaws.services.simpleworkflow.flow.generic.ActivityImplementationFactory;
import com.amazonaws.services.simpleworkflow.flow.generic.GenericWorkflowClientExternal;
import com.amazonaws.services.simpleworkflow.flow.generic.StartWorkflowExecutionParameters;
import com.amazonaws.services.simpleworkflow.flow.worker.ActivityTypeExecutionOptions;
import com.amazonaws.services.simpleworkflow.flow.worker.GenericActivityWorker;
import com.amazonaws.services.simpleworkflow.flow.worker.GenericWorkflowClientExternalImpl;
import com.uber.cadence.ActivityType;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowExecutionCompletedEventAttributes;
import com.uber.cadence.WorkflowService;
import com.uber.cadence.WorkflowType;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class SyncWorkfowTest {

    // TODO: Make this configuratble instead of always using local instance.
    private static final String host = "127.0.0.1";
    private static final int port = 7933;
    private static final String serviceName = "cadence-frontend";
    private static final String domain = "UnitTest";
    private static final String taskList = "UnitTest";
    private static final Log log;

    static {
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.DEBUG);

        Logger.getLogger("io.netty").setLevel(Level.INFO);
        log = LogFactory.getLog(SyncWorkfowTest.class);

    }

    private GenericWorkflowClientExternalImpl clientExternal;
    private static WorkflowService.Iface service;
    private SyncWorkflowWorker workflowWorker;
    private GenericActivityWorker activityWorker;
    private Map<WorkflowType, SyncWorkflowDefinition> definitionMap = new Hashtable<>();
    private Map<ActivityType, ActivityImplementation> activityMap = new Hashtable<>();

    private Function<WorkflowType, SyncWorkflowDefinition> factory = new Function<WorkflowType, SyncWorkflowDefinition>() {
        @Override
        public SyncWorkflowDefinition apply(WorkflowType workflowType) {
            SyncWorkflowDefinition result = definitionMap.get(workflowType);
            if (result == null) {
                throw new IllegalArgumentException("Unknown workflow type " + workflowType);
            }
            return result;
        }
    };

    @BeforeClass
    public static void setUpService() {
        WorkflowServiceTChannel.ClientOptions.Builder optionsBuilder = new WorkflowServiceTChannel.ClientOptions.Builder();
        service = new WorkflowServiceTChannel(host, port, serviceName, optionsBuilder.build());
    }

    @Before
    public void setUp() {
        activityWorker = new GenericActivityWorker(service, domain, taskList);
        activityWorker.setActivityImplementationFactory(new ActivityImplementationFactory() {
            @Override
            public ActivityImplementation getActivityImplementation(ActivityType activityType) {
                ActivityImplementation result = activityMap.get(activityType);
                if (result == null) {
                    throw new IllegalArgumentException("Unknown activity type " + activityType);
                }
                return result;
            }
        });
        workflowWorker = new SyncWorkflowWorker(service, domain, taskList);
        workflowWorker.setFactory(factory);
        clientExternal = new GenericWorkflowClientExternalImpl(service, domain);
        activityWorker.start();
        workflowWorker.start();
    }

    @After
    public void tearDown() {
        activityWorker.shutdown();
        workflowWorker.shutdown();
        definitionMap.clear();
        activityMap.clear();
    }

    @Test
    public void test() throws InterruptedException, WorkflowExecutionAlreadyStartedException, TimeoutException {
        WorkflowType type = new WorkflowType().setName("test1");
        definitionMap.put(type, (input) -> {
            byte[] a1 = Workflow.executeActivity("activity1", "activityInput".getBytes());
            return Workflow.executeActivity("activity2", a1);
        });
        ActivityType activity1Type = new ActivityType().setName("activity1");
        activityMap.put(activity1Type, new ActivityImplementation() {
            @Override
            public ActivityTypeExecutionOptions getExecutionOptions() {
                return new ActivityTypeExecutionOptions();
            }

            @Override
            public byte[] execute(ActivityExecutionContext context) throws ActivityFailureException, CancellationException {
                return "activity1".getBytes();
            }
        });
        ActivityType activity2Type = new ActivityType().setName("activity2");
        activityMap.put(activity2Type, new ActivityImplementation() {
            @Override
            public ActivityTypeExecutionOptions getExecutionOptions() {
                return new ActivityTypeExecutionOptions();
            }

            @Override
            public byte[] execute(ActivityExecutionContext context) throws ActivityFailureException, CancellationException {
                return (new String(context.getTask().getInput()) + " - activity2").getBytes();
            }
        });

        StartWorkflowExecutionParameters startParameters = new StartWorkflowExecutionParameters();
        startParameters.setExecutionStartToCloseTimeoutSeconds(60);
        startParameters.setTaskStartToCloseTimeoutSeconds(2);
        startParameters.setTaskList(taskList);
        startParameters.setInput("input".getBytes());
        startParameters.setWorkflowId("workflow1");
        startParameters.setWorkflowType(type);
        WorkflowExecution started = clientExternal.startWorkflow(startParameters);
        WorkflowExecutionCompletedEventAttributes result = WorkflowExecutionUtils.waitForWorkflowExecutionResult(service, domain, started, 5);
        assertEquals("activity1 - activity2", new String(result.getResult()));
    }
}

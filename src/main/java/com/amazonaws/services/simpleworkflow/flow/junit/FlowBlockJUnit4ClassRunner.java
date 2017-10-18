package com.amazonaws.services.simpleworkflow.flow.junit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.rules.MethodRule;
import org.junit.rules.RunRules;
import org.junit.rules.TestRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.internal.runners.statements.*;

/**
 * To be used instead of {@link BlockJUnit4ClassRunner} when testing asynchronous code. Requires
 * {@link WorkflowTest} rule (annotated with @Rule) to be present in the tested class.
 *
 * @author fateev
 */
public class FlowBlockJUnit4ClassRunner extends BlockJUnit4ClassRunner {

  private WorkflowTestBase workflowTestRule;

  private long timeout;

  private Class<? extends Throwable> expectedException;

  public FlowBlockJUnit4ClassRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected Statement withPotentialTimeout(FrameworkMethod method, final Object test,
      Statement next) {
    Test annotation = method.getAnnotation(Test.class);
    timeout = annotation.timeout();
    if (timeout > 0 && workflowTestRule != null) {
      workflowTestRule.setTestTimeoutActualTimeMilliseconds(timeout);
    }
    return next;
  }

  @Override
  protected List<MethodRule> rules(Object test) {
    List<MethodRule> superResult = super.rules(test);
    List<MethodRule> result = new ArrayList<MethodRule>();
    for (MethodRule methodRule : superResult) {
      if (WorkflowTestBase.class.isAssignableFrom(methodRule.getClass())) {
        workflowTestRule = (WorkflowTestBase) methodRule;
        workflowTestRule.setFlowTestRunner(true);
        if (timeout > 0) {
          workflowTestRule.setTestTimeoutActualTimeMilliseconds(timeout);
        }
        if (expectedException != null) {
          workflowTestRule.setExpectedException(expectedException);
        }
      } else {
        result.add(methodRule);
      }
    }
    return result;
  }

  @Override
  protected Statement possiblyExpectingExceptions(FrameworkMethod method, Object test,
      Statement next) {
    Test annotation = method.getAnnotation(Test.class);
    Class<? extends Throwable> expected = annotation.expected();
    if (expected != Test.None.class) {
      expectedException = expected;
      if (workflowTestRule != null) {
        workflowTestRule.setExpectedException(expectedException);
      }
    }
    return next;
  }

  protected Statement methodBlock(FrameworkMethod method) {
    Object test;
    try {
      test = new ReflectiveCallable() {
        @Override
        protected Object runReflectiveCall() throws Throwable {
          return createTest();
        }
      }.run();
    } catch (Throwable e) {
      return new Fail(e);
    }
    List<org.junit.rules.MethodRule> methodRules = getMethodRules(test);

    Statement statement = methodInvoker(method, test);
    statement = possiblyExpectingExceptions(method, test, statement);
    statement = withPotentialTimeout(method, test, statement);
    statement = withBefores(method, test, statement);
    // It should go before afters to ensure that all async code is done before
    // them.
    if (this.workflowTestRule != null) {
      statement = workflowTestRule.apply(statement, method, statement);
    }
    statement = withAfters(method, test, statement);
    statement = withRules(method, test, statement, methodRules);
    return statement;
  }

  private Statement withRules(FrameworkMethod method, Object target,
      Statement statement, List<MethodRule> methodRules) {
    Statement result = statement;
    result = withMethodRules(method, target, result, methodRules);
    result = withTestRules(method, target, result);
    return result;
  }

  private Statement withMethodRules(FrameworkMethod method, Object target, Statement result,
      List<MethodRule> methodRules) {
    List<TestRule> testRules = getTestRules(target);
    for (MethodRule each : methodRules) {
      if (!testRules.contains(each)) {
        result = each.apply(result, method, target);
      }
    }
    return result;
  }

  @SuppressWarnings("deprecation")
  private List<org.junit.rules.MethodRule> getMethodRules(Object target) {
    return rules(target);
  }

  /**
   * Returns a {@link Statement}: apply all non-static {@link Value} fields annotated with {@link
   * Rule}.
   *
   * @param statement The base statement
   * @return a RunRules statement if any class-level {@link Rule}s are found, or the base statement
   */
  private Statement withTestRules(FrameworkMethod method, Object target,
      Statement statement) {
    List<TestRule> testRules = getTestRules(target);
    return testRules.isEmpty() ? statement :
        new RunRules(statement, testRules, describeChild(method));
  }

}

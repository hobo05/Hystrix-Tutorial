package com.sample.logging;


import com.netflix.hystrix.HystrixInvokableInfo;
import com.netflix.hystrix.HystrixRequestLog;
import com.sample.utils.RequestScopeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Record metrics for all Hystrix Commands for a request.   This code can be used in a servlet filter.
 * Additional metrics (fallback status) can be found in the Hystrix wiki.
 * @author byuen
 *
 */
public class LoggingHelper {

    private static final Logger logger = LoggerFactory.getLogger(LoggingHelper.class);

    public static void log() {
        for (HystrixInvokableInfo<?> hystrixCommand : HystrixRequestLog.getCurrentRequest().getAllExecutedCommands()) {
            String strBuilder = "tid=" + RequestScopeObject.get() +
                    ",CommandGroup=" + hystrixCommand.getCommandGroup().name() +
                    ",Command=" + hystrixCommand.getCommandKey().name() +
                    ",ExecTime=" + hystrixCommand.getExecutionTimeInMilliseconds() +
                    ",CircuitOpen=" + (hystrixCommand.isCircuitBreakerOpen() ? 1 : 0) +
                    ",Failed=" + (hystrixCommand.isFailedExecution() ? 1 : 0) +
                    ",TimedOut=" + (hystrixCommand.isResponseTimedOut() ? 1 : 0) +
                    ",ShortCircuited=" + (hystrixCommand.isResponseShortCircuited() ? 1 : 0) +
                    ",Successful=" + (hystrixCommand.isSuccessfulExecution() ? 1 : 0) +
                    ",Rejected=" + (hystrixCommand.isResponseRejected() ? 1 : 0);
            logger.info(strBuilder);
        }

    }

}
/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.concurrent;

import org.apache.log4j.Logger;

import java.util.concurrent.*;

/**
 * A ScheduledThreadPoolExecutor that logs exceptions.
 *
 * Find unchecked exceptions thrown by scheduled tasks faster.
 *
 * ScheduledThreadPoolExecutor has the unexpected (evil?) behavior that it catches and SWALLOWS exceptions thrown by
 * your scheduled runnable/callable and then silently stops running it.  It does not log, it does not rethrow to be handled
 * by an UncaughtExceptionHandler (even if the supplied thread factory has one). What this means in practice is that
 * anyone using an ScheduledThreadPoolExecutor must catch not just Exception in their run() implementation, but
 * catch Throwable, if they want to be aware of programming error type exceptions.  This is difficult to enforce and
 * remember, especially on a large project, and especially since it not the case for normal ThreadPoolExecutors.
 */
class LoggingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private static final Logger LOG = Logger.getLogger(LoggingScheduledThreadPoolExecutor.class);

    LoggingScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
        super(corePoolSize, threadFactory);
    }

    /* Intentionally just overriding four methods exposed via ScheduledExecutorService interface, currently
     * the only way users of this package-private class will view it. */

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return super.schedule(new LoggingRunnable(command), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return super.schedule(new LoggingCallable<V>(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return super.scheduleAtFixedRate(new LoggingRunnable(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return super.scheduleWithFixedDelay(new LoggingRunnable(command), initialDelay, delay, unit);
    }

    private static class LoggingRunnable implements Runnable {
        private Runnable theRunnable;

        LoggingRunnable(Runnable theRunnable) {
            this.theRunnable = theRunnable;
        }

        @Override
        public void run() {
            try {
                theRunnable.run();
            } catch (Throwable e) {
                LOG.error("uncaught exception in ScheduledThreadPoolExecutor", e);
                // throw so that ScheduledThreadPoolExecutor will do what it normally would (not much)
                throw new RuntimeException(e);
            }
        }
    }

    private static class LoggingCallable<T> implements Callable<T> {
        private Callable<T> theRunnable;

        LoggingCallable(Callable<T> theRunnable) {
            this.theRunnable = theRunnable;
        }

        @Override
        public T call() {
            try {
                return theRunnable.call();
            } catch (Throwable e) {
                LOG.error("uncaught exception in ScheduledThreadPoolExecutor", e);
                // throw so that ScheduledThreadPoolExecutor will do what it normally would (not much)
                throw new RuntimeException(e);
            }
        }
    }

}
/*******************************************************************************
 * Copyright (c) 2023,2024 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package test.concurrency.schedasync.web;

import java.time.Month;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.concurrent.Asynchronous;
import jakarta.enterprise.concurrent.Schedule;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SchedAsyncAppScopedBean {

    /**
     * Runs every 5 seconds.
     *
     * @param countdown number of executions to stop after.
     * @return null to continue with more executions.
     *         To stop, returns a completed future with the current time in nanoseconds.
     */
    @Asynchronous(runAt = @Schedule(hours = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                                              12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23 },
                                    minutes = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
                                                12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
                                                24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                                                36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
                                                48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59 },
                                    seconds = { 0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55 }))
    CompletableFuture<Long> everyFiveSeconds(AtomicInteger countdown) {
        System.out.println("> everyFiveSeconds " + countdown);

        CompletableFuture<Long> result;
        if (countdown.decrementAndGet() == 0)
            result = Asynchronous.Result.complete(System.nanoTime());
        else
            result = null;

        System.out.println("< everyFiveSeconds " + result);
        return result;
    }

    /**
     * Runs on seconds that are divisible by 2 or 3.
     *
     * @param maxExecutions  number of executions to stop after.
     * @param executionCount for tracking the total number of executions.
     * @return null to continue with more executions.
     *         To stop, returns a completed future with the execution count and current time in nanoseconds.
     */
    @Asynchronous(runAt = { @Schedule(cron = "*/3 * * * * *"),
                            @Schedule(cron = "*/2 * * * * *") })
    CompletableFuture<long[]> everyThreeOrEvenSeconds(int maxExecutions, AtomicInteger executionCount) {
        int count = executionCount.incrementAndGet();
        System.out.println("> everyThreeOrEvenSeconds " + count);

        CompletableFuture<long[]> result = null;
        if (count >= maxExecutions) {
            result = Asynchronous.Result.getFuture();
            result.obtrudeValue(new long[] { count, System.nanoTime() });
        }

        System.out.println("< everyThreeOrEvenSeconds " + result);
        return result;
    }

    /**
     * Attempt to run on a day that doesn't exist: February 30th.
     */
    @Asynchronous(runAt = @Schedule(months = Month.FEBRUARY,
                                    daysOfMonth = 30))
    CompletionStage<String> runOnFebruary30() {
        return Asynchronous.Result.complete("Should not be running runOnFebruary30!");
    }

    /**
     * Specify an empty list of seconds to run at.
     *
     * @throws IllegalArgumentException for the invalid empty list value on the annotation.
     */
    @Asynchronous(runAt = @Schedule(seconds = {}))
    void withEmptySeconds() {
        System.out.println("Should not be running withEmptySeconds.");
    }
}

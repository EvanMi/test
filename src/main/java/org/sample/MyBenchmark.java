/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sample;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
/**
 * 结果如下，重点关注Mean和Units两个字段，组合起来是1227.928ns/op，即每次操作耗时1227.928纳秒：Mean error表示误差，或者波动
 * Benchmark                               Mode   Samples         Mean   Mean error    Units
 * o.s.MyBenchmark.testApacheBeanUtils     avgt         5   306706.144    46350.093    ns/op
 * o.s.MyBenchmark.testCglibBeanCopier     avgt         5      159.403        3.243    ns/op
 * o.s.MyBenchmark.testDeadCode            avgt         5       99.916        2.527    ns/op
 * o.s.MyBenchmark.testOrikaBeanCopy       avgt         5      825.971       15.891    ns/op
 * o.s.MyBenchmark.testSpringBeanUtils     avgt         5    12034.272      307.476    ns/op
 * <p>
 * 如果我们将@BenchmarkMode(Mode.AverageTime)与@OutputTimeUnit(TimeUnit.NANOSECONDS)的组合，改成@BenchmarkMode(Mode.Throughput)和@OutputTimeUnit(TimeUnit.MILLISECONDS)，那么基准测试结果就是每毫秒的吞吐量（即每毫秒多少次操作），结果如下，表示943.437ops/ms：
 */


/**
 * 基准测试后对代码预热总计5秒（迭代5次，每次1秒）。预热对于压测来说非常非常重要，如果没有预热过程，压测结果会很不准确。
 * # Warmup Iteration   2: 13349.349 ns/op
 * # Warmup Iteration   3: 12084.938 ns/op
 * # Warmup Iteration   4: 12032.203 ns/op
 * # Warmup Iteration   5: 12127.331 ns/op
 */
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
/**
 * 循环运行5次，每次迭代时间为1秒，总计5秒时间。
 */
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
/**
 * 表示fork多少个线程运行基准测试，如果@Fork(1)，那么就是一个线程，这时候就是同步模式。
 */
@Fork(100)
/**
 * 基准测试模式,@BenchmarkMode(Mode.AverageTime)搭配@OutputTimeUnit(TimeUnit.NANOSECONDS)
 * 表示每次操作需要的平均时间，而OutputTimeUnit申明为纳秒，所以基准测试单位是ns/op，即每次操作的纳秒单位平均时间
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MyBenchmark {

    static Unsafe unsafe = getUnsafe();

    private static final Lock lock = new ReentrantLock();

    private static Unsafe getUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            return null;
        }
    }


    @State(Scope.Benchmark)
    public static class CommonState {
        public static final long valueOffset;

        static {
            try {
                valueOffset = unsafe.objectFieldOffset
                        (AtomicInteger.class.getDeclaredField("value"));
            } catch (Exception ex) {
                throw new Error(ex);
            }
        }

        public volatile int value;
    }

    @Benchmark
    public void testCas(CommonState commonState) throws Exception {
        int x;
        do {
            x = unsafe.getIntVolatile(commonState, CommonState.valueOffset);
        } while (!unsafe.compareAndSwapInt(commonState, CommonState.valueOffset, x, x + 1));
    }

    @Benchmark
    public void testLock(CommonState commonState) throws Exception {
        lock.lock();
        try {
            commonState.value++;
        } finally {
            lock.unlock();
        }
    }

    @Benchmark
    public void testAdd(CommonState commonState) throws Exception {
        unsafe.getAndAddInt(commonState, CommonState.valueOffset, 1);
    }
}

package com.autotune.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RollingFrameBuffer Concurrency")
class RollingFrameBufferConcurrencyTest {

    @RepeatedTest(5)
    @DisplayName("Concurrent write/read does not crash")
    void testConcurrentWriteRead() throws InterruptedException {
        RollingFrameBuffer buffer = new RollingFrameBuffer(300);
        AtomicBoolean writerDone = new AtomicBoolean(false);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        // Writer thread (simulates render thread)
        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10_000; i++) {
                    buffer.record(16_666_666L + (i % 100) * 10_000L);
                }
            } catch (Throwable t) {
                error.set(t);
            } finally {
                writerDone.set(true);
            }
        });

        // Reader thread (simulates tick/evaluation thread)
        Thread reader = new Thread(() -> {
            try {
                startLatch.await();
                while (!writerDone.get()) {
                    double fps = buffer.getAverageFps();
                    double p1Low = buffer.get1PercentLowFps();
                    double p99 = buffer.get99thPercentileFrameTimeMs();
                    double slope = buffer.getFrameTimeTrendSlope();
                    boolean stutter = buffer.hasStutter();
                    // Values should be non-negative (no corruption)
                    assertTrue(fps >= 0, "FPS should be non-negative, got: " + fps);
                    assertTrue(p1Low >= 0, "P1 low should be non-negative, got: " + p1Low);
                    assertTrue(p99 >= 0, "P99 should be non-negative, got: " + p99);
                }
            } catch (Throwable t) {
                error.set(t);
            }
        });

        writer.start();
        reader.start();
        startLatch.countDown();
        writer.join(5000);
        reader.join(5000);

        assertNull(error.get(), () -> "Thread error: " + error.get());
    }

    @RepeatedTest(3)
    @DisplayName("Concurrent write/reset does not crash")
    void testConcurrentWriteReset() throws InterruptedException {
        RollingFrameBuffer buffer = new RollingFrameBuffer(100);
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5_000; i++) {
                    buffer.record(16_666_666L);
                }
            } catch (Throwable t) {
                error.set(t);
            }
        });

        Thread resetter = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    buffer.reset();
                    Thread.sleep(1);
                }
            } catch (Throwable t) {
                error.set(t);
            }
        });

        writer.start();
        resetter.start();
        startLatch.countDown();
        writer.join(5000);
        resetter.join(5000);

        assertNull(error.get(), () -> "Thread error: " + error.get());
    }

    @Test
    @DisplayName("Values remain consistent after heavy concurrent usage")
    void testConsistencyAfterConcurrency() throws InterruptedException {
        RollingFrameBuffer buffer = new RollingFrameBuffer(300);

        // Write from multiple threads simultaneously
        Thread[] writers = new Thread[4];
        CountDownLatch latch = new CountDownLatch(1);
        for (int t = 0; t < writers.length; t++) {
            writers[t] = new Thread(() -> {
                try {
                    latch.await();
                    for (int i = 0; i < 1000; i++) {
                        buffer.record(16_666_666L); // 60fps
                    }
                } catch (InterruptedException ignored) {}
            });
            writers[t].start();
        }
        latch.countDown();
        for (Thread w : writers) w.join(5000);

        // After concurrent writes, buffer should still be in a valid state
        assertTrue(buffer.isFull(), "Buffer should be full after many writes");
        double fps = buffer.getAverageFps();
        assertTrue(fps > 50 && fps < 70, "FPS should be ~60 after writing 60fps frames, got: " + fps);
    }
}

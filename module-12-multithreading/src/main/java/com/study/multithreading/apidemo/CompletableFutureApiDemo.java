package com.study.multithreading.apidemo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * CompletableFuture 方法用例（常用 + 不常用）—— 异步编排（面试重点）
 *
 * 两个核心概念：
 *   1. 任务类型：runAsync(无返回值) / supplyAsync(有返回值)
 *   2. 回调线程：thenXxx 在调用方线程执行；thenXxxAsync 在线程池执行
 *
 * 链式规则：thenApply(转换) -> thenAccept(消费) -> thenRun(收尾)，异常用 exceptionally/handle 兜底。
 */
public class CompletableFutureApiDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("========== 创建任务（常用） ==========");

        // ---- runAsync：无返回值 ----
        CompletableFuture<Void> f0 = CompletableFuture.runAsync(() ->
                System.out.println("  runAsync 在 " + Thread.currentThread().getName() + " 执行"));
        f0.join();

        // ---- supplyAsync：有返回值 ----
        CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> {
            sleep(50);
            return "商品数据";
        });
        System.out.println("  supplyAsync.get() = " + f1.get());

        // ---- 指定线程池（不指定用 ForkJoinPool.commonPool，生产建议指定） ----
        ExecutorService biz = Executors.newFixedThreadPool(4, r -> new Thread(r, "biz"));
        CompletableFuture.supplyAsync(() -> "带线程池", biz)
                .thenAccept(s -> System.out.println("  指定线程池执行: " + Thread.currentThread().getName() + " -> " + s))
                .join();

        System.out.println();
        System.out.println("========== 链式编排（常用） ==========");

        // ---- thenApply：转换（一进一出） ----
        // ---- thenAccept：消费（只进不出） ----
        // ---- thenRun：收尾（啥也不看） ----
        CompletableFuture.supplyAsync(() -> 42)
                .thenApply(n -> n * 2)                    // 84
                .thenApply(String::valueOf)               // "84"
                .thenAccept(s -> System.out.println("  thenApply->thenAccept 链式结果 = " + s))
                .thenRun(() -> System.out.println("  thenRun 收尾（不关心上一步结果）"))
                .join();

        // ---- thenCompose：扁平化，避免 Future 嵌套（串行依赖任务） ----
        CompletableFuture<Integer> composed = CompletableFuture.supplyAsync(() -> 10)
                .thenCompose(n -> CompletableFuture.supplyAsync(() -> n * 3));
        System.out.println("  thenCompose 串行依赖结果 = " + composed.join());

        // ---- thenCombine：两个任务都完成后合并（并行） ----
        CompletableFuture<String> taskA = CompletableFuture.supplyAsync(() -> "A");
        CompletableFuture<String> taskB = CompletableFuture.supplyAsync(() -> "B");
        String combined = taskA.thenCombine(taskB, (a, b) -> a + "+" + b).join();
        System.out.println("  thenCombine 并行合并 = " + combined);

        // ---- allOf：等所有任务完成 ----
        CompletableFuture<Integer> c1 = CompletableFuture.supplyAsync(() -> 1);
        CompletableFuture<Integer> c2 = CompletableFuture.supplyAsync(() -> 2);
        CompletableFuture<Integer> c3 = CompletableFuture.supplyAsync(() -> 3);
        CompletableFuture.allOf(c1, c2, c3).join();
        System.out.println("  allOf 全部完成后统一取值 = " + (c1.join() + c2.join() + c3.join()));

        // ---- anyOf：任一完成即可 ----
        Object first = CompletableFuture.anyOf(
                        CompletableFuture.supplyAsync(() -> {
                            sleep(200);
                            return "慢";
                        }),
                        CompletableFuture.supplyAsync(() -> {
                            sleep(50);
                            return "快";
                        }))
                .join();
        System.out.println("  anyOf 取最先完成 = " + first);

        System.out.println();
        System.out.println("========== 异常处理（常用） ==========");

        // ---- exceptionally：只处理异常，返回兜底值 ----
        String safe = CompletableFuture.<String>supplyAsync(() -> {
            throw new IllegalStateException("下游服务挂了");
        }).exceptionally(e -> "兜底数据").join();
        System.out.println("  exceptionally 兜底 = " + safe);

        // ---- handle：无论成败都执行（返回新值，比 exceptionally 更通用） ----
        String handled = CompletableFuture.supplyAsync(() -> "正常结果")
                .handle((r, e) -> e == null ? r + "(成功)" : "兜底")
                .join();
        System.out.println("  handle 双分支 = " + handled);

        // ---- whenComplete：感知结果但不改变结果（记日志场景） ----
        CompletableFuture.supplyAsync(() -> 1)
                .whenComplete((r, e) -> System.out.println("  whenComplete 观察到结果=" + r + "，异常=" + e))
                .join();

        System.out.println();
        System.out.println("========== CompletableFuture 不常用但有用的方法 ==========");

        // ---- applyToEither / acceptEither：谁先完成用谁的结果 ----
        String either = CompletableFuture.supplyAsync(() -> {
            sleep(150);
            return "缓存";
        }).applyToEither(CompletableFuture.supplyAsync(() -> {
            sleep(30);
            return "DB";
        }), r -> "先用" + r).join();
        System.out.println("  applyToEither 竞速取先完成 = " + either);

        // ---- orTimeout：超时自动失败（防下游卡死，生产必备） ----
        String timedOut = CompletableFuture.supplyAsync(() -> {
            sleep(1_000);
            return "太慢了";
        }).orTimeout(200, TimeUnit.MILLISECONDS)
                .exceptionally(e -> "超时兜底")
                .join();
        System.out.println("  orTimeout(200ms) 超时 -> " + timedOut);

        // ---- completeOnTimeout：超时用默认值完成（不抛异常） ----
        String defaulted = CompletableFuture.supplyAsync(() -> {
            sleep(1_000);
            return "真实数据";
        }).completeOnTimeout("默认数据", 200, TimeUnit.MILLISECONDS).join();
        System.out.println("  completeOnTimeout(200ms) -> " + defaulted);

        // ---- getNow：不阻塞，已完成返回结果，未完成返回兜底 ----
        CompletableFuture<Integer> quick = CompletableFuture.completedFuture(7);
        System.out.println("  getNow(兜底) 已完成 -> " + quick.getNow(-1));
        CompletableFuture<Integer> slow = new CompletableFuture<>();
        System.out.println("  getNow(兜底) 未完成 -> " + slow.getNow(-1) + "（不阻塞，返回兜底）");
        slow.complete(9);
        System.out.println("  complete(9) 手动完成 -> " + slow.join());

        // ---- completeExceptionally / obtrudeValue：手动置为失败 / 强制覆盖结果 ----
        CompletableFuture<String> fail = new CompletableFuture<>();
        fail.completeExceptionally(new RuntimeException("手动失败"));
        System.out.println("  completeExceptionally 后 isCompletedExceptionally=" + fail.isCompletedExceptionally());
        fail.obtrudeValue("强制覆盖");   // 即使已完成也强制改结果
        System.out.println("  obtrudeValue 强制覆盖结果 = " + fail.join());

        // ---- completedFuture / failedFuture：直接构造已完成的 Future ----
        CompletableFuture<String> doneFuture = CompletableFuture.completedFuture("现成结果");
        System.out.println("  completedFuture = " + doneFuture.join());

        // ---- delayedExecutor：延迟提交任务（重试/限流场景） ----
        CompletableFuture.supplyAsync(() -> "延迟任务", CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS))
                .thenAccept(r -> System.out.println("  delayedExecutor 延迟 100ms 后执行 -> " + r))
                .join();

        // ---- minimalCompletionStage：降级为不可手动完成的只读视图 ----
        CompletionStage<String> readOnly = CompletableFuture.completedFuture("x").minimalCompletionStage();
        System.out.println("  minimalCompletionStage 只读视图（不能 complete/cancel）");

        // ---- cancel：取消未开始的任务 ----
        CompletableFuture<Integer> cancelMe = new CompletableFuture<>();
        System.out.println("  cancel(true)=" + cancelMe.cancel(true) + "，isCancelled=" + cancelMe.isCancelled());

        // ---- 获取原始异常：ExecutionException.getCause() ----
        try {
            CompletableFuture.<Integer>failedFuture(new IllegalArgumentException("计算失败")).get();
        } catch (ExecutionException e) {
            System.out.println("  failedFuture 抛出的 ExecutionException.getCause() = " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        biz.shutdown();
        biz.awaitTermination(3, TimeUnit.SECONDS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

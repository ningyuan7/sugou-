import java.util.concurrent.*;

public class test {
    static volatile int count = 0;
    public static void main(String[] args) {

        // 1. 手动创建线程池（7大参数）
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                3,                      // 核心线程3
                6,                      // 最大线程6
                10L, TimeUnit.SECONDS,  // 非核心线程空闲10s销毁
                new ArrayBlockingQueue<>(20), // 有界队列，最多存20个等待任务
                Executors.defaultThreadFactory(),
                // 拒绝策略：提交任务的主线程执行，削峰限流
                new ThreadPoolExecutor.CallerRunsPolicy()
        );


        // 模拟100条待处理业务数据
        int totalTask = 100;
        for (int i = 1; i <= totalTask; i++) {
            int taskId = i; // lambda需要常量捕获
            pool.submit(() -> {
                try {
                    System.out.printf("线程【%s】正在处理任务%d%n",
                            Thread.currentThread().getName(), taskId);
                    // 模拟业务耗时：查询数据库、文件读写、接口调用
                    Thread.sleep(300);
                    System.out.printf("任务%d 处理完成%n", taskId);
                } catch (InterruptedException e) {
                    System.err.printf("任务%d 被中断%n", taskId);
                } catch (Exception e) {
                    System.err.printf("任务%d 执行异常：%s%n", taskId, e.getMessage());
                }
            });
        }

        // 等待所有任务执行完毕，关闭线程池
        pool.shutdown();


// 1000个线程各自自增1000次，最终达不到1000000
        for(int i=0;i<1000;i++){
            new Thread(()->{
                for(int j=0;j<10;j++){
                    count++; // 复合操作
                    System.out.println(count);
                }
            }).start();
        }

        try {
            // 最长等待5分钟，超时强制关闭
            if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        System.out.println("全部任务执行结束，线程池关闭");
    }

}
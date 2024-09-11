import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {


    private LoadBalancerStrategy randomStrategy;
    private LoadBalancerStrategy roundRobin;

    private LoadBalancer loadBalancerRandomStrategy;
    private LoadBalancer loadBalancerRoundRobin;

    @BeforeEach
    void setUp() {
        randomStrategy = new RandomStrategy();
        roundRobin = new RoundRobinStrategy();
        loadBalancerRandomStrategy = new LoadBalancer(randomStrategy);
        loadBalancerRoundRobin = new LoadBalancer(roundRobin);
    }

    @Test
    void should_add_instance() {
        //given
        String testInstance = "1";

        //when
        boolean result = loadBalancerRandomStrategy.register(testInstance);

        //then
        assertTrue(result);
    }

    @Test
    void should_add_maximum_10_instances() {
        //given
        for (int i = 1; i <= 10; i++) {
            loadBalancerRandomStrategy.register(String.valueOf(i));
        }

        //when
        boolean result = loadBalancerRandomStrategy.register("11");

        //then
        assertFalse(result);
    }

    @Test
    void should_not_allow_adding_duplicates() {
        //given
        String testInstance = "1";
        loadBalancerRandomStrategy.register(testInstance);

        //when
        boolean result = loadBalancerRandomStrategy.register("1");

        //then
        assertFalse(result);
    }

    @Test
    void should_return_null_if_no_instances_are_available() {
        // when
        String result = loadBalancerRandomStrategy.get();
        // then
        assertNull(result);
    }

    @Test
    void should_return_random_instance_by_default() {
        //given
        loadBalancerRandomStrategy.register("1");
        loadBalancerRandomStrategy.register("2");
        loadBalancerRandomStrategy.register("3");

        // when
        List<String> retrievedInstances = new ArrayList<>(List.of());
        for (int i = 0; i < 100; i++) {
            retrievedInstances.add(loadBalancerRandomStrategy.get());
        }

        //then
        Map<String, Long> distribution = retrievedInstances.stream()
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));

        // Assert all instances are returned
        assertTrue(distribution.keySet().containsAll(Arrays.asList("1", "2", "3")),
                "All instances should be returned at least once");

        // Assert reasonable distribution
        distribution.values().forEach(count ->
                assertTrue(count > 5, "Each instance should appear several times to indicate randomness")
        );
    }

    @Test
    void should_return_instances_in_round_robin_pattern() {
        // given
        loadBalancerRoundRobin.register("1");
        loadBalancerRoundRobin.register("2");
        loadBalancerRoundRobin.register("3");

        // when
        List<String> retrievedInstances = new ArrayList<>(List.of());
        for (int i = 0; i < 5; i++) {
            retrievedInstances.add(loadBalancerRoundRobin.get());
        }

        // then
        assertEquals(List.of("1", "2", "3", "1", "2"), retrievedInstances);
    }

    @RepeatedTest(100)
    @Disabled
    void should_not_be_able_to_add_more_than_10_in_concurrent_environment() throws InterruptedException {
        // given
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

        for (int i = 0; i < 10; i++) {
            int finalI = i;
            executorService.submit(() -> {
                loadBalancerRandomStrategy.register(String.valueOf(finalI));
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(loadBalancerRandomStrategy.size(), 10);
    }

    @RepeatedTest(100)
    @Disabled
    void should_keep_round_robin_pattern_in_concurrent_environment() throws InterruptedException {
        // given
        loadBalancerRoundRobin.register("1");
        loadBalancerRoundRobin.register("2");
        loadBalancerRoundRobin.register("3");

        CopyOnWriteArrayList<String> results = new CopyOnWriteArrayList<>();
        List<Thread> threadList = new ArrayList<>();

        try {
            for (int i = 0; i < 30; i++) {
                Thread thread = new Thread(() -> {
                    String result = loadBalancerRoundRobin.get();
                    synchronized (results) {
                        results.add(result);
                    }
                });
                threadList.add(thread);
                thread.start();
            }

            // Wait for all threads to finish
            for (Thread thread : threadList) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(30, results.size());
        for (int i = 0; i < results.size() - 3; i += 3) {
            assertEquals("1", results.get(i));
            assertEquals("2", results.get(i + 1));
            assertEquals("3", results.get(i + 2));
        }
    }
}
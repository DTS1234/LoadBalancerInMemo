import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LoadBalancer {

    private final List<String> instanceList = new ArrayList<>();
    private static final int MAX_SIZE = 10;
    private final LoadBalancerStrategy strategy;
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public LoadBalancer(LoadBalancerStrategy strategy) {
        this.strategy = strategy;
    }

    public boolean register(String instance) {
        writeLock.lock();
        try {
            if (size() == MAX_SIZE) {
                return false;
            }
            if (instanceList.contains(instance)) {
                return false;
            }
            instanceList.add(instance);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public String get() {
        readLock.lock();
        try {
            return this.strategy.get(this.instanceList);
        }finally {
            readLock.unlock();
        }
    }

    public int size() {
        readLock.lock();
        try {
            return this.instanceList.size();
        } finally {
            readLock.unlock();
        }
    }
}

interface LoadBalancerStrategy {
    String get(List<String> instances);
}

class RandomStrategy implements LoadBalancerStrategy {
    @Override
    public String get(List<String> instances) {
        if (instances.isEmpty()) {
            return null;
        }
        return instances.get(ThreadLocalRandom.current().nextInt(instances.size()));
    }
}

class RoundRobinStrategy implements LoadBalancerStrategy {

    private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public synchronized String get(List<String> instances) {  // Synchronize access
        if (instances.isEmpty()) {
            return null;
        }
        int index = currentIndex.getAndUpdate(i -> (i + 1) % instances.size());
        return instances.get(index);
    }
}

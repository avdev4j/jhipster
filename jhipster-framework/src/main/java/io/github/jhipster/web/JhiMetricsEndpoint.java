package io.github.jhipster.web;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.search.Search;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Endpoint(id = "jhi-metrics")
@Component
public class JhiMetricsEndpoint {

    @Autowired
    MeterRegistry meterRegistry;

    @ReadOperation
    public Map<String, Map> oneMetric() {

        Map<String, Map> results = new HashMap<>();

        // JVM stats
        results.put("jvm", this.jvmMemoryMetrics());

        // HTTP requests stats
        results.put("http.server.requests", this.httpRequestsMetrics());
        results.put("http.server.requests.all", this.httpRequestAllMetrics());

        // Cache stats
        results.put("cache", this.cacheMetrics());

        // Service stats
        results.put("services", this.serviceMetrics());

        // Database stats
        results.put("databases", this.databaseMetrics());

        // Garbage collector
        results.put("garbageCollector", this.garbageCollectorMetrics());

        results.put("processMetrics", this.processMetrics());

        return results;
    }

    private Map<String, Number> processMetrics() {
        Map<String, Number> resultsProcess = new HashMap<>();

        Gauge gauge = Search.in(this.meterRegistry).name(s -> s.contains("process.cpu.usage")).gauge();
        Double cpuUsage = gauge.value();

        resultsProcess.put("cpuUsage", cpuUsage);

        gauge = Search.in(this.meterRegistry).name(s -> s.contains("process.start.time")).gauge();
        Double startTime = gauge.value();

        resultsProcess.put("startTime", startTime);

        gauge = Search.in(this.meterRegistry).name(s -> s.contains("process.uptime")).gauge();
        Double upTime = gauge.value();

        resultsProcess.put("upTime", upTime);

        return resultsProcess;
    }

    private Map<String, Number> garbageCollectorMetrics() {
        Map<String, Number> resultsGarbageCollector = new HashMap<>();

        Collection<Timer> timers = Search.in(this.meterRegistry).name(s -> s.contains("jvm.gc.pause")).timers();
        Double gcPause = timers.stream().map(x -> x.totalTime(TimeUnit.MILLISECONDS)).reduce((x, y) -> (x + y)).orElse((double) 0);

        resultsGarbageCollector.put("pause", gcPause);

        Collection<Gauge> gauges = Search.in(this.meterRegistry).name(s -> s.contains("jvm.gc") && !s.contains("jvm.gc.pause")).gauges();
        gauges.forEach(gauge -> {
            resultsGarbageCollector.put(gauge.getId().getName(), gauge.value());
        });

        Collection<Counter> counters = Search.in(this.meterRegistry).name(s -> s.contains("jvm.gc") && !s.contains("jvm.gc.pause")).counters();
        counters.forEach(counter -> {
            resultsGarbageCollector.put(counter.getId().getName(), counter.count());
        });


        gauges = Search.in(this.meterRegistry).name(s -> s.contains("jvm.classes.loaded")).gauges();
        Double classesLoaded = gauges.stream().map(Gauge::value).reduce((x, y) -> (x + y)).orElse((double) 0);

        resultsGarbageCollector.put("classesLoaded", classesLoaded);

        Collection<FunctionCounter> functionCounters = Search.in(this.meterRegistry).name(s -> s.contains("jvm.classes.unloaded")).functionCounters();
        Double classesUnloaded = functionCounters.stream().map(FunctionCounter::count).reduce((x, y) -> (x + y)).orElse((double) 0);

        resultsGarbageCollector.put("classesUnloaded", classesUnloaded);

        return resultsGarbageCollector;
    }

    private Map<String, Map<String, Number>> databaseMetrics() {
        Map<String, Map<String, Number>> resultsDatabase = new HashMap<>();
        Collection<Timer> timers = Search.in(this.meterRegistry).name(s -> s.contains("hikari")).timers();

        timers.forEach(timer -> {
            String key = timer.getId().getName().substring(timer.getId().getName().lastIndexOf('.') + 1);

            resultsDatabase.putIfAbsent(key, new HashMap<>());

            resultsDatabase.get(key).put("count", timer.count());
            resultsDatabase.get(key).put("max", timer.max(TimeUnit.MILLISECONDS));
            resultsDatabase.get(key).put("totalTime", timer.totalTime(TimeUnit.MILLISECONDS));
            resultsDatabase.get(key).put("mean", timer.mean(TimeUnit.MILLISECONDS));

            ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();

            for (ValueAtPercentile percentile : percentiles) {
                resultsDatabase.get(key).put(String.valueOf(percentile.percentile()), percentile.value());
            }
        });

        Collection<Gauge> gauges = Search.in(this.meterRegistry).name(s -> s.contains("hikari")).gauges();

        gauges.forEach(gauge -> {
            String key = gauge.getId().getName().substring(gauge.getId().getName().lastIndexOf('.') + 1);

            resultsDatabase.putIfAbsent(key, new HashMap<>());

            resultsDatabase.get(key).put("value", gauge.value());

        });
        return resultsDatabase;
    }

    private Map<String, Map<String, Number>> serviceMetrics() {
        Map<String, Map<String, Number>> resultsService = new HashMap<>();

        Collection<Timer> timers = Search.in(this.meterRegistry).tagKeys("service").timers();

        timers.forEach(timer -> {
            String key = timer.getId().getName();

            resultsService.putIfAbsent(key, new HashMap<>());

            resultsService.get(key).put("count", timer.count());
            resultsService.get(key).put("max", timer.max(TimeUnit.MILLISECONDS));
            resultsService.get(key).put("totalTime", timer.totalTime(TimeUnit.MILLISECONDS));
            resultsService.get(key).put("mean", timer.mean(TimeUnit.MILLISECONDS));

            ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();

            for (ValueAtPercentile percentile : percentiles) {
                resultsService.get(key).put(String.valueOf(percentile.percentile()), percentile.value());
            }
        });
        return resultsService;
    }

    private Map<String, Map<String, Number>> cacheMetrics() {
        Map<String, Map<String, Number>> resultsCache = new HashMap<>();

        Collection<FunctionCounter> counters = Search.in(this.meterRegistry).name(s -> s.contains("cache")).functionCounters();

        counters.forEach(counter -> {
            String name = counter.getId().getTag("name");

            resultsCache.putIfAbsent(name, new HashMap<>());

            String key = counter.getId().getName();
            if (counter.getId().getTag("result") != null) {
                key += "." + counter.getId().getTag("result");
            }

            resultsCache.get(name).put(key, counter.count());
        });

        Collection<Gauge> gauges = Search.in(this.meterRegistry).name(s -> s.contains("cache")).gauges();

        gauges.forEach(gauge -> {
            String name = gauge.getId().getTag("name");

            resultsCache.putIfAbsent(name, new HashMap<>());

            String key = gauge.getId().getName();

            resultsCache.get(name).put(key, gauge.value());
        });
        return resultsCache;
    }

    private Map<String, Map<String, Number>> jvmMemoryMetrics() {
        Map<String, Map<String, Number>> resultsJVM = new HashMap<>();

        Search jvmUsedSearch = Search.in(this.meterRegistry).name(s -> s.contains("jvm.memory.used"));

        Collection<Gauge> gauges = jvmUsedSearch.gauges();

        gauges.forEach(gauge -> {
            String key = gauge.getId().getTag("id");

            resultsJVM.putIfAbsent(key, new HashMap<>());

            resultsJVM.get(key).put("used", gauge.value());

        });

        Search jvmMaxSearch = Search.in(this.meterRegistry).name(s -> s.contains("jvm.memory.max"));

        gauges = jvmMaxSearch.gauges();

        gauges.forEach(gauge -> {
            String key = gauge.getId().getTag("id");
            resultsJVM.get(key).put("max", gauge.value());

        });

        Search jvmCommittedSearch = Search.in(this.meterRegistry).name(s -> s.contains("jvm.memory.committed"));

        gauges = jvmCommittedSearch.gauges();

        gauges.forEach(gauge -> {
            String key = gauge.getId().getTag("id");
            resultsJVM.get(key).put("committed", gauge.value());

        });

        return resultsJVM;
    }

    private Map<String, Map<String, Number>> httpRequestsMetrics() {
        List<String> statusCode = new ArrayList<>();
        statusCode.add("200");
        statusCode.add("404");
        statusCode.add("500");

        Map<String, Map<String, Number>> resultsHTTP = new HashMap<>();

        statusCode.forEach(code -> {
            Map<String, Number> resultsHTTPPerCode = new HashMap<>();

            Collection<Timer> httpTimersStream = this.meterRegistry.find("http.server.requests").tag("status", code).timers();

            long count = httpTimersStream.stream().map(Timer::count).reduce((x, y) -> x + y).orElse(0L);
            double max = httpTimersStream.stream().map(x -> x.max(TimeUnit.MILLISECONDS)).reduce((x, y) -> x > y ? x : y).orElse((double) 0);
            double totalTime = httpTimersStream.stream().map(x -> x.totalTime(TimeUnit.MILLISECONDS)).reduce((x, y) -> (x + y)).orElse((double) 0);

            resultsHTTPPerCode.put("count", count);
            resultsHTTPPerCode.put("max", max);
            resultsHTTPPerCode.put("mean", count != 0 ? totalTime / count : 0);
            resultsHTTP.put(code, resultsHTTPPerCode);

        });

        return resultsHTTP;
    }

    private Map<String, Number> httpRequestAllMetrics() {
        Collection<Timer> timers = this.meterRegistry.find("http.server.requests").timers();
        long countAllrequests = timers.stream().map(Timer::count).reduce((x, y) -> x + y).orElse(0L);
        Map<String, Number> resultsHTTPAll = new HashMap<>();
        resultsHTTPAll.put("count", countAllrequests);

        return resultsHTTPAll;
    }

}

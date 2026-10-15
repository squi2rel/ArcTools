package com.github.squi2rel.arctools.inspect;

import arc.Events;
import arc.files.Fi;
import arc.func.Cons;
import arc.util.serialization.Jval;
import com.github.squi2rel.arc.script.ScriptEnvironment.CompiledScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Bookmark schedules and startup scripts use the same scope as interactive Tester runs.
 */
final class TesterTasks implements AutoCloseable {
    final InspectorScripts scripts;
    final TesterFiles files;
    final List<Job> jobs = new ArrayList<>();
    Consumer<Throwable> onError = ignored -> {
    };
    private boolean started;

    TesterTasks(InspectorScripts scripts, TesterFiles files) {
        this.scripts = scripts;
        this.files = files;
    }

    void start() {
        if (started) return;
        started = true;
        for (Fi file : files.startupScripts()) add(file, Jval.newObject());
        for (var entry : files.execution.asObject()) {
            Fi file = files.bookmarks.child(entry.key);
            if (file.exists()) add(file, entry.value);
        }
        tick(System.nanoTime());
    }

    void configure(Fi file, Jval config) {
        jobs.removeIf(job -> {
            if (!job.file.equals(file)) return false;
            job.close();
            return true;
        });
        if (config == null) files.execution.remove(file.name());
        else {
            files.execution.put(file.name(), config);
            if (started) add(file, config);
        }
        files.save();
    }

    void rename(Fi file, String name) {
        files.renameBookmark(file, name);
        Fi target = files.bookmarks.child(name);
        if (target.equals(file)) return;
        jobs.removeIf(job -> {
            if (!job.file.equals(file)) return false;
            job.close();
            return true;
        });
        Jval config = files.execution.get(name);
        if (started && config != null) add(target, config);
    }

    private void add(Fi file, Jval config) {
        Job job = new Job(file, config);
        jobs.add(job);
        job.resume();
    }

    void tick(long now) {
        if (scripts.busy()) return;
        for (Job job : List.copyOf(jobs)) {
            if (!job.running) continue;
            if (job.listener != null) {
                // Events emitted by a script are handled on the next tick.
                int count = job.events.size();
                for (int i = 0; i < count && job.running; i++) {
                    Object event = job.events.poll();
                    if (event == null) break;
                    job.run(event);
                }
            } else if (now >= job.next) {
                job.run(null);
                job.next = now + (long) (job.interval * 1_000_000_000L);
            }
        }
    }

    final class Job implements AutoCloseable {
        final Fi file;
        final Jval config;
        CompiledScript code;
        final ConcurrentLinkedQueue<Object> events = new ConcurrentLinkedQueue<>();
        float interval;
        int remaining, runs;
        long next;
        boolean running;
        String status = "Paused";
        Throwable error;
        Class<Object> eventType;
        Cons<Object> listener;

        Job(Fi file, Jval config) {
            this.file = file;
            this.config = config;
        }

        @SuppressWarnings("unchecked")
        void resume() {
            close();
            try {
                interval = config.getFloat("intervalSeconds", 0.1f);
                if (!Float.isFinite(interval) || interval < 0.01f)
                    throw new IllegalArgumentException("Interval must be at least 0.01 seconds");
                remaining = config.getInt("repeatCount", 0);
                if (remaining < -1) throw new IllegalArgumentException("Repeat count must be -1 or greater");
                code = scripts.compile(file.readString(), file.name(), false);
                if (config.getBool("disposable", false)) {
                    String type = config.getString("type");
                    Object value = scripts.evaluate(type, null, null, null);
                    if (!(value instanceof Class<?> cls))
                        throw new IllegalArgumentException("Event must be a Java event class");
                    eventType = (Class<Object>) cls;
                    listener = events::add;
                    Events.on(eventType, listener);
                }
                runs = 0;
                error = null;
                next = 0;
                running = true;
                status = "Running";
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        void run(Object event) {
            try {
                scripts.put("modName", "JS startup");
                if (event != null) scripts.put("$e$", event);
                scripts.execute(code, new InspectorScripts.Execution(2000), null);
                runs++;
                if (listener == null && remaining != -1 && remaining-- == 0) {
                    running = false;
                    status = "Finished";
                }
            } catch (Throwable failure) {
                fail(failure);
            }
        }

        private void fail(Throwable failure) {
            close();
            error = failure;
            status = "Error: " + InspectorReflection.error(failure);
            onError.accept(failure);
        }

        @Override
        public void close() {
            running = false;
            status = "Paused";
            if (listener != null) {
                Events.remove(eventType, listener);
                listener = null;
            }
            events.clear();
        }
    }

    @Override
    public void close() {
        for (Job job : jobs) job.close();
    }
}

package com.github.squi2rel.arctools.inspect;

import arc.files.Fi;
import arc.util.Log;
import com.github.squi2rel.arc.script.ScriptEnvironment.CompiledScript;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Execution and history behavior of modtools Tester, independent of its window.
 */
final class InspectorTester implements AutoCloseable {
    private final InspectorScripts scripts;
    final TesterFiles files;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Arc-Tester");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<Runnable> completed = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final Queue<InspectorScripts.Message> logs = new ConcurrentLinkedQueue<>();
    private CompiledScript compiled;
    private InspectorScripts.Execution execution;
    private Future<?> future;
    private boolean closed;
    boolean strict, loop, multiThread, stopIfOvertime, highlight = true;
    boolean deferMainExecution;
    volatile boolean finished = true;
    Object result;
    boolean assigned;
    Throwable error;
    String log = "", originalText;
    int historyIndex = -1;
    long revision;
    Consumer<Throwable> onError = ignored -> {
    };

    InspectorTester(InspectorScripts scripts, TesterFiles files) {
        this.scripts = scripts;
        this.files = files;
    }

    boolean compile(String source) {
        if (!finished || closed) return false;
        try {
            scripts.wrapReferences = files.enabled("wrap_ref");
            compiled = scripts.compile(source, "console.js", strict);
            error = null;
            revision++;
            return true;
        } catch (Throwable failure) {
            compiled = null;
            fail(failure);
            return false;
        }
    }

    void run(String source) {
        if (!finished || closed) return;
        logs.clear();
        historyIndex = -1;
        originalText = source;
        Fi entry = files.record(source);
        if (compile(source)) execute(compiled, entry, false);
        else files.result(entry, log);
    }

    private void execute(CompiledScript code, Fi entry, boolean watching) {
        finished = false;
        logs.clear();
        boolean background = multiThread;
        execution = new InspectorScripts.Execution(stopIfOvertime ? background ? 4000 : 2000 : 0);
        InspectorScripts.Execution current = execution;
        Runnable action = () -> {
            if (closed) return;
            Object value = null;
            Throwable failure = null;
            try {
                value = scripts.execute(code, current, files.enabled("capture_logger") ? logs::add : null);
            } catch (Throwable caught) {
                failure = caught;
            }
            Object output = value;
            Throwable caught = failure;
            Runnable finish = () -> {
                if (closed) return;
                future = null;
                execution = null;
                finished = true;
                if (caught != null) fail(caught);
                else {
                    result = output;
                    assigned = true;
                    error = null;
                    log = InspectorReflection.describe(output);
                    if (files.enabled("output_to_log") && !watching) Log.info("[TESTER]: " + log);
                    revision++;
                }
                files.result(entry, outputLog());
            };
            if (background) completed.add(finish);
            else finish.run();
        };
        if (background) future = executor.submit(action);
        else if (deferMainExecution) pending.add(action);
        else action.run();
    }

    void tick() {
        Runnable next;
        while ((next = pending.poll()) != null) next.run();
        while ((next = completed.poll()) != null) next.run();
        if (loop && finished && compiled != null && !closed && !scripts.busy()) execute(compiled, null, true);
    }

    void stop() {
        loop = false;
        if (execution != null) execution.stopped.set(true);
        revision++;
    }

    private void fail(Throwable failure) {
        error = failure;
        loop = false;
        finished = true;
        log = InspectorScripts.error(failure);
        revision++;
        if (files.enabled("output_to_log")) Log.err("[TESTER]", failure);
        if (!files.enabled("ignore_popup_error")) onError.accept(failure);
    }

    List<InspectorScripts.Message> messages() {
        return List.copyOf(logs);
    }

    int messageCount() {
        return logs.size();
    }

    String outputLog() {
        StringBuilder output = new StringBuilder();
        for (InspectorScripts.Message message : logs)
            output.append('[').append(message.level()).append("] ").append(message.text()).append('\n');
        return output.append(log).toString();
    }

    String rollHistory(String text, boolean forward) {
        List<Fi> history = files.histories();
        if (history.isEmpty() || files.maxHistory() == 0) return text;
        if (historyIndex == -1) originalText = text;
        int next = historyIndex + (forward ? 1 : -1);
        if (files.enabled("rollback_history")) {
            if (next >= history.size()) next = -1;
            if (next < -1) next = history.size() - 1;
        } else if (next < -1 || next >= history.size()) return text;
        historyIndex = next;
        if (next == -1) {
            log = "";
            revision++;
            return originalText;
        }
        Fi entry = history.get(next);
        loadHistory(entry);
        return entry.child("message.txt").readString();
    }

    void loadHistory(Fi entry) {
        log = entry.child("log.txt").exists() ? entry.child("log.txt").readString() : "";
        logs.clear();
        assigned = false;
        result = null;
        error = null;
        revision++;
    }

    java.util.function.Supplier<Object> watch(String source) {
        CompiledScript code = scripts.compile(source, "watch.js", strict);
        return () -> scripts.execute(code, new InspectorScripts.Execution(stopIfOvertime ? 2000 : 0), null);
    }

    void callbackFailure(Throwable failure) {
        fail(failure);
    }

    void clearResult() {
        result = null;
        assigned = false;
        error = null;
        log = "";
        revision++;
    }

    @Override
    public void close() {
        stop();
        closed = true;
        if (future != null) future.cancel(true);
        executor.shutdownNow();
        pending.clear();
        completed.clear();
    }
}

package com.github.squi2rel.arctools.inspect;

import arc.files.Fi;
import arc.util.serialization.Jval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TesterFiles {
    final Fi directory, history, bookmarks, startup;
    final Jval settings;
    final Jval execution;
    private long lastRecord;

    TesterFiles(Fi directory) {
        this.directory = directory;
        history = directory.child("historical record");
        bookmarks = directory.child("bookmarks");
        startup = directory.child("startup");
        history.mkdirs();
        bookmarks.mkdirs();
        startup.mkdirs();
        settings = read(directory.child("settings.json"));
        execution = read(directory.child("execution_js.json"));
    }

    private static Jval read(Fi file) {
        return file.exists() ? Jval.read(file.readString()) : Jval.newObject();
    }

    boolean enabled(String key) {
        return settings.getBool(key, key.equals("wrap_ref") || key.equals("capture_logger"));
    }

    void setting(String key, boolean value) {
        settings.put(key, value);
        save();
    }

    int maxHistory() {
        return settings.getInt("max_history_size", 40);
    }

    void maxHistory(int size) {
        if (size < 0 || size > 100) throw new IllegalArgumentException("History size must be between 0 and 100");
        settings.put("max_history_size", size);
        trimHistory();
        save();
    }

    void save() {
        directory.child("settings.json").writeString(settings.toString(Jval.Jformat.formatted));
        directory.child("execution_js.json").writeString(execution.toString(Jval.Jformat.formatted));
    }

    Fi record(String code) {
        if (maxHistory() == 0) return null;
        lastRecord = Math.max(System.currentTimeMillis(), lastRecord + 1);
        Fi entry = history.child(Long.toString(lastRecord));
        while (entry.exists()) entry = history.child(Long.toString(++lastRecord));
        entry.child("message.txt").writeString(code);
        trimHistory();
        return entry;
    }

    void result(Fi entry, String text) {
        if (entry != null && entry.exists()) entry.child("log.txt").writeString(text);
    }

    private void trimHistory() {
        List<Fi> entries = histories();
        for (int i = Math.max(0, maxHistory()); i < entries.size(); i++) entries.get(i).deleteDirectory();
    }

    List<Fi> histories() {
        return list(history, true);
    }

    List<Fi> bookmarks() {
        return list(bookmarks, false);
    }

    private static List<Fi> list(Fi directory, boolean directories) {
        ArrayList<Fi> files = new ArrayList<>();
        for (Fi file : directory.list()) {
            if (file.isDirectory() == directories && (!directories || file.child("message.txt").exists()))
                files.add(file);
        }
        files.sort(Comparator.comparingLong(Fi::lastModified).reversed().thenComparing(Fi::name, Comparator.reverseOrder()));
        return files;
    }

    Fi bookmark(String name, String code) {
        if (name.isBlank() || !name.equals(new java.io.File(name).getName()) || name.equals(".") || name.equals("..") || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid bookmark filename");
        }
        Fi file = bookmarks.child(name);
        file.writeString(code);
        return file;
    }

    void deleteBookmark(Fi file) {
        file.delete();
        execution.remove(file.name());
        save();
    }

    void renameBookmark(Fi file, String name) {
        Fi target = bookmark(name, file.readString());
        if (target.equals(file)) return;
        if (execution.has(file.name())) execution.put(name, execution.remove(file.name()));
        file.delete();
        save();
    }

    List<Fi> startupScripts() {
        ArrayList<Fi> files = new ArrayList<>();
        startup.walk(file -> {
            if (!file.isDirectory() && file.extEquals("js")) files.add(file);
        });
        files.sort(Comparator.comparing(Fi::path));
        return files;
    }
}

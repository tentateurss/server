package ru.eclipsia.core.quest;

import java.util.HashMap;
import java.util.Map;

/**
 * Прогресс игрока по квесту
 */
public class QuestProgress {
    
    private final String questId;
    private QuestStatus status;
    private final Map<String, Integer> progress;
    private final long startedAt;
    private long completedAt;
    
    public QuestProgress(String questId) {
        this.questId = questId;
        this.status = QuestStatus.IN_PROGRESS;
        this.progress = new HashMap<>();
        this.startedAt = System.currentTimeMillis();
        this.completedAt = 0;
    }
    
    public String getQuestId() { return questId; }
    public QuestStatus getStatus() { return status; }
    public Map<String, Integer> getProgress() { return progress; }
    public long getStartedAt() { return startedAt; }
    public long getCompletedAt() { return completedAt; }
    
    /**
     * Обновить прогресс
     */
    public void updateProgress(String key, int value) {
        progress.put(key, value);
    }
    
    /**
     * Увеличить прогресс
     */
    public void incrementProgress(String key, int amount) {
        progress.put(key, progress.getOrDefault(key, 0) + amount);
    }
    
    /**
     * Получить прогресс
     */
    public int getProgress(String key) {
        return progress.getOrDefault(key, 0);
    }
    
    /**
     * Завершить квест
     */
    public void complete() {
        this.status = QuestStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }
    
    /**
     * Провалить квест
     */
    public void fail() {
        this.status = QuestStatus.FAILED;
    }
    
    /**
     * Статусы квеста
     */
    public enum QuestStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}

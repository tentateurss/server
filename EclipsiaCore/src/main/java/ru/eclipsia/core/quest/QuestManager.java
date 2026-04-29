package ru.eclipsia.core.quest;

import org.bukkit.entity.Player;
import ru.eclipsia.core.EclipsiaCore;

import java.util.*;

/**
 * Менеджер квестов
 */
public class QuestManager {
    
    private static QuestManager instance;
    private final EclipsiaCore plugin;
    private final Map<String, Quest> quests;
    private final Map<UUID, Map<String, QuestProgress>> playerQuests;
    
    private QuestManager(EclipsiaCore plugin) {
        this.plugin = plugin;
        this.quests = new HashMap<>();
        this.playerQuests = new HashMap<>();
        loadDefaultQuests();
    }
    
    public static void initialize(EclipsiaCore plugin) {
        if (instance == null) {
            instance = new QuestManager(plugin);
        }
    }
    
    public static QuestManager getInstance() {
        return instance;
    }
    
    /**
     * Загрузить дефолтные квесты
     */
    private void loadDefaultQuests() {
        // Квест 1: Первые шаги
        Quest firstSteps = new Quest.Builder(
            "first_steps",
            "§6Первые шаги",
            "Убейте 5 зомби на Берегу",
            Quest.QuestType.KILL_MOBS,
            1
        )
        .objective("mob_type", "ZOMBIE")
        .objective("count", 5)
        .reward("exp", 100)
        .reward("orbs", 50)
        .build();
        
        quests.put(firstSteps.getId(), firstSteps);
        
        // Квест 2: Испытание силы
        Quest strengthTest = new Quest.Builder(
            "strength_test",
            "§cИспытание силы",
            "Победите Хранителя Врат",
            Quest.QuestType.KILL_BOSS,
            5
        )
        .objective("boss_type", "GATEKEEPER")
        .objective("count", 1)
        .reward("exp", 500)
        .reward("orbs", 200)
        .build();
        
        quests.put(strengthTest.getId(), strengthTest);
        
        // Квест 3: Исследователь
        Quest explorer = new Quest.Builder(
            "explorer",
            "§bИсследователь",
            "Посетите город Эликиум",
            Quest.QuestType.REACH_LOCATION,
            1
        )
        .objective("location", "elikium_city")
        .objective("radius", 20)
        .reward("exp", 200)
        .reward("orbs", 100)
        .build();
        
        quests.put(explorer.getId(), explorer);
        
        plugin.getLogger().info("Загружено квестов: " + quests.size());
    }
    
    /**
     * Получить квест по ID
     */
    public Quest getQuest(String questId) {
        return quests.get(questId);
    }
    
    /**
     * Получить все квесты
     */
    public Collection<Quest> getAllQuests() {
        return quests.values();
    }
    
    /**
     * Начать квест для игрока
     */
    public boolean startQuest(Player player, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) return false;
        
        Map<String, QuestProgress> playerQuestMap = playerQuests.computeIfAbsent(
            player.getUniqueId(), k -> new HashMap<>()
        );
        
        if (playerQuestMap.containsKey(questId)) {
            player.sendMessage("§cВы уже выполняете этот квест!");
            return false;
        }
        
        QuestProgress progress = new QuestProgress(questId);
        playerQuestMap.put(questId, progress);
        
        player.sendMessage("§aНовый квест: §6" + quest.getName());
        player.sendMessage("§7" + quest.getDescription());
        
        return true;
    }
    
    /**
     * Получить прогресс квеста
     */
    public QuestProgress getQuestProgress(Player player, String questId) {
        Map<String, QuestProgress> playerQuestMap = playerQuests.get(player.getUniqueId());
        if (playerQuestMap == null) return null;
        return playerQuestMap.get(questId);
    }
    
    /**
     * Получить все активные квесты игрока
     */
    public List<QuestProgress> getActiveQuests(Player player) {
        Map<String, QuestProgress> playerQuestMap = playerQuests.get(player.getUniqueId());
        if (playerQuestMap == null) return new ArrayList<>();
        
        return playerQuestMap.values().stream()
            .filter(p -> p.getStatus() == QuestProgress.QuestStatus.IN_PROGRESS)
            .toList();
    }
    
    /**
     * Обновить прогресс квеста
     */
    public void updateProgress(Player player, String questId, String key, int value) {
        QuestProgress progress = getQuestProgress(player, questId);
        if (progress == null) return;
        
        progress.updateProgress(key, value);
        checkQuestCompletion(player, questId);
    }
    
    /**
     * Увеличить прогресс квеста
     */
    public void incrementProgress(Player player, String questId, String key, int amount) {
        QuestProgress progress = getQuestProgress(player, questId);
        if (progress == null) return;
        
        progress.incrementProgress(key, amount);
        checkQuestCompletion(player, questId);
    }
    
    /**
     * Проверить завершение квеста
     */
    private void checkQuestCompletion(Player player, String questId) {
        Quest quest = getQuest(questId);
        QuestProgress progress = getQuestProgress(player, questId);
        
        if (quest == null || progress == null) return;
        if (progress.getStatus() != QuestProgress.QuestStatus.IN_PROGRESS) return;
        
        // Проверяем выполнение целей
        boolean completed = true;
        for (Map.Entry<String, Object> entry : quest.getObjectives().entrySet()) {
            if (entry.getKey().equals("count")) {
                int required = (int) entry.getValue();
                int current = progress.getProgress("count");
                if (current < required) {
                    completed = false;
                    break;
                }
            }
        }
        
        if (completed) {
            completeQuest(player, questId);
        }
    }
    
    /**
     * Завершить квест
     */
    private void completeQuest(Player player, String questId) {
        Quest quest = getQuest(questId);
        QuestProgress progress = getQuestProgress(player, questId);
        
        if (quest == null || progress == null) return;
        
        progress.complete();
        
        // Выдаем награды
        for (Map.Entry<String, Object> entry : quest.getRewards().entrySet()) {
            switch (entry.getKey()) {
                case "exp":
                    int exp = (int) entry.getValue();
                    // TODO: Добавить опыт через API
                    player.sendMessage("§a+§e" + exp + " §aопыта");
                    break;
                case "orbs":
                    int orbs = (int) entry.getValue();
                    // TODO: Добавить орбы через API
                    player.sendMessage("§a+§6" + orbs + " §aорбов");
                    break;
            }
        }
        
        player.sendMessage("§a§l✔ Квест завершен: §6" + quest.getName());
        player.sendTitle("§6Квест завершен!", "§e" + quest.getName(), 10, 40, 10);
    }
}

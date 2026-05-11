package ru.eclipsia.builder.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import ru.eclipsia.builder.util.FloatingText;

import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.Random;

/**
 * Процедурная генерация города Эликий и базового ландшафта в мире
 * {@code world}. Полностью независима от {@link BeachGenerator} —
 * совпадает с ним только по архитектурному паттерну (8 фаз +
 * {@link RegionPainter} + PDC-маркер + {@link FloatingText}).
 *
 * <p><b>Игровой контекст</b>: игрок убивает Хранителя Врат на Береге,
 * подходит к появившемуся порталу-арке (мир {@code beach}, точка
 * {@code (0.5, 16, 154.5)}) и телепортируется в этот мир на точку
 * {@link #SPAWN_X}/{@link #SPAWN_Y}/{@link #SPAWN_Z} = {@code (0, 75, 38)} —
 * прямо ПЕРЕД южными воротами Эликия. Через арку видны улицы и собор.
 *
 * <p><b>8 фаз генерации</b> (порядок строгий — каждая фаза опирается на
 * предыдущую):
 * <ol>
 *   <li>{@link #phase1Landscape ЛАНДШАФТ} — мостовая под городом
 *       (POLISHED_DEEPSLATE на y=70 внутри городского полигона). Внешний
 *       мир остаётся плоским из {@code flatSettings} EclipsiaBuilder
 *       (bedrock 5 + stone 55 + dirt 9 + grass 1 = ровный y=0..70).</li>
 *   <li>{@link #phase2Vegetation РАСТИТЕЛЬНОСТЬ} — для города почти
 *       пусто (несколько горшков); внешние биомы — отдельный PR.</li>
 *   <li>{@link #phase3Decorations ДЕКОРАЦИИ} — мелкие детали (бочки,
 *       цветы, дрова); реализация в PR 5.</li>
 *   <li>{@link #phase4Paths УЛИЦЫ} — изгибающиеся улицы 3-7 блоков
 *       шириной с фонарями; реализация в PR 4.</li>
 *   <li>{@link #phase5PointsOfInterest ТОЧКИ ИНТЕРЕСА} — таверна,
 *       кузница, лавка, гильдия, склад, рынок, колодец, жилые дома;
 *       реализация в PR 4.</li>
 *   <li>{@link #phase6Structures КРУПНЫЕ СТРУКТУРЫ} — стена-полигон 22
 *       вершины с воротами и башнями (PR 2), собор со шпилем-глазом
 *       (PR 3), южные горы (PR 2).</li>
 *   <li>{@link #phase7FloatingText НАДПИСИ} — названия ворот, зданий,
 *       площади, границ карты; реализация в PR 5.</li>
 *   <li>{@link #phase8Spawnpoint ТОЧКА СПАВНА} — фиксируем
 *       {@link World#setSpawnLocation} на (0, 75, 38) и помечаем мир
 *       PDC-маркером {@link #GENERATED_FLAG}.</li>
 * </ol>
 *
 * <p><b>Идемпотентность</b>: если PDC-маркер уже выставлен, генератор
 * сразу возвращается. Чтобы заставить мир регенерироваться, надо либо
 * сбросить маркер ({@link #resetMarker()}), либо удалить папку мира.
 * Версия маркера привязана к структуре генератора — при крупных правках
 * (например, +PR 2) {@link #GENERATED_FLAG} инкрементируется и старые
 * миры пересоздаются автоматически.
 */
public final class WorldGenerator {

    // =========================================================================
    // ВЕРСИОНИРОВАНИЕ
    // =========================================================================

    /**
     * PDC-ключ на мире: «Эликий уже сгенерирован». Версия меняется при
     * структурных правках, чтобы сервер пересобрал город при апдейте.
     *
     * <p><b>v6</b>: PR 1 — полный rewrite. Скелет 8 фаз, мостовая под
     * городом (POLISHED_DEEPSLATE), точка спавна перед южными воротами
     * (0, 75, 38), плоский внешний мир из flatSettings.
     *
     * <p><b>v7</b>: PR 2 — стена-полигон 22 вершины из COBBLED_DEEPSLATE +
     * DEEPSLATE_BRICKS, 4 ворот с арками, башни через 18-22 блока
     * ({@link ElikiumWall}), южная горная гряда z=50..190 / x=±200,
     * h=18..60 ({@link WorldMountains}, шум SimplexOctaveGenerator).
     *
     * <p><b>v8</b>: PR 2.5 — масштаб ×2. Городской полигон ±50 → ±100
     * (~160×160 вместо ~80×80); стены 9×3 → 18×5 (высота×толщина);
     * башни d=5/h=14 → d=9/h=28; ворота 5×7 → 7×12; горы отодвинуты
     * z=130..380 и расширены x=±400, h=30..100 (пики до y=170);
     * спавн игрока (0,75,38) → (0,75,78); собор (15,-5) → (30,-10).
     *
     * <p><b>v9</b>: PR 2.7 — ещё ×1.5 (итого ×3 от исходника). Полигон ±100 →
     * ±150 (~240×240); стены 18×5 → 27×9 (высота×толщина, сечение ×3.6);
     * башни d=9/h=28 → d=15/h=42; ворота 7×12 → 11×18; горы отодвинуты
     * z=170..480, x=±600, h=40..130 (пики до y=200, выше облаков);
     * спавн (0,75,78) → (0,75,118); собор (30,-10) → (45,-15).
     *
     * <p><b>v10</b>: PR 3 — собор Эликий построен. Готическое здание
     * 60×84 на y=70..99 (стены), двускатная крыша до y=120, центральный
     * шпиль до y=189, флаг и маяк до y=196. На южном фронтоне y≈106 —
     * «Глаз Эликия» 11×11 (LAPIS+EMERALD+END_ROD), 4 луча END_ROD.
     * Контрфорсы (6 пар), витражи (10 пролётов), южный портал 9×16.
     * SpireParticles обновляет координаты на (45.5, 189.5, -14.5).
     *
     * <p><b>v11</b>: PR 3.5 — полная переделка собора в готический
     * тёмно-фиолетовый стиль. Cruciform plan (неф ±15 × ±42, трансепт
     * ±30 × ±7), 7 башен (1 центральная 11×11 шпиль y=187 + 2 фасадные
     * 7×7 шпили y=144 + 4 угловых пинакля 5×5 y=130), стрельчатые арки,
     * витражи PURPLE_STAINED_GLASS со SHROOMLIGHT-подсветкой, контрфорсы
     * с пинаклями, парящий «Глаз Эликия» на y=201 (AMETHYST 3×3×3 +
     * 3 кольца END_ROD). SpireParticles переезжает на Глаз и переходит
     * на DRAGON_BREATH+WITCH (фиолетовые). Старые v6..v10-миры несовместимы.
     *
     * <p><b>v14</b>: PR 3.8 — собор в фокусе по фидбэку фото-референса.
     * Глаз поднят y=201→y=211 ({@code EYE_Y_OFFSET 14→24}) и подкреплён
     * 4 видимыми блочными колоннами END_ROD (видны издалека даже без
     * частиц). {@link SpireParticles} перешёл на overload
     * {@code spawnParticle(..., null, force=true)} — пакеты идут всем
     * игрокам в view-distance, миндаль увеличен ×1.6 (LID_A 7.5→12).
     * Закрыты дыры в торцах нефа/трансепта (новый {@code buildGableCaps()}),
     * добавлены: окно-роза на южном фронтоне, готические часы на южном
     * фронтоне, аркбутаны (flying buttresses) от пинаклей контрфорсов
     * к стене нефа, аметистовые жилы на 12 контрфорсах, SOUL_LANTERN-сконсы
     * по фасаду, цветочные клумбы по периметру. Флаги переделаны: на 2
     * фасадных башнях — большие чёрно-фиолетово-золотые штандарты на
     * BLACKSTONE_WALL-древке (cloth 3×8 из WOOL+GOLD_BLOCK), на 4 угловых
     * пинаклях — компактные флюгеры END_ROD+LIGHTNING_ROD вместо
     * квадратных wool-полотен; на центральной башне ниже Глаза —
     * 4-сторонний городской штандарт.
     *
     * <p><b>v15</b>: PR 3.9 — полировка собора: закрыты «долины» на 4
     * внутренних углах cruciform (где скат нефа встречается с трансептом),
     * Глаз стал ЖИРНЕЕ — контур миндаля/склеры в 3 параллельных слоях
     * + 8 радиальных лучей SOUL_FIRE_FLAME, вокруг ВСЕГО собора —
     * пассивная аура частиц (PORTAL+SOUL_FIRE_FLAME+ENCHANTMENT_TABLE
     * в радиусе ~50 блоков), интерьер — ребристые своды от потолка
     * к колоннам, расширенный 3-ступенчатый алтарь с реликварием,
     * 5 люстр-кандел SHROOMLIGHT над нефом, красный ковёр от портала,
     * хорные скамьи у апсиды. Наружный декор — flying buttresses
     * (диагональные арки от пинаклей к стене нефа), крокеты END_ROD
     * на рёбрах центрального шпиля, карнизы POLISHED_BLACKSTONE_BRICK_STAIRS
     * на y=82 и y=92, trifolium-аркада на восточном и западном
     * торцах трансепта.
     *
     * <p><b>v16</b>: PR 3.10 — фидбэк по v15:
     * <ul>
     *   <li>Откачена выпирающая «долина» в крыше — заменена на гладкое
     *       hip-слияние трансептовой и нефовой крыш, без выпуклостей.</li>
     *   <li>«Pencil-реликварий» (стопка GOLD/QUARTZ/GOLD на алтаре) убран,
     *       вместо него — низкий алтарный камень + БОЛЬШОЙ ЗОЛОТОЙ КРЕСТ
     *       5×7 на стене апсиды (GOLD_BLOCK + END_ROD-сияние + 4 SOUL_LANTERN).</li>
     *   <li>Южные двери удалены — открытая арка с RED_CARPET, выходящим
     *       на крыльцо.</li>
     *   <li>Выпирающие наружные детали: 8 каменных гаргулий на углах
     *       нефа+трансепта (выступают на 2-3 блока с END_ROD-«языком»),
     *       2 высокие статуи святых у южного портала (POLISHED_BLACKSTONE
     *       тело + PIGLIN_HEAD голова + END_ROD-нимб), балкон-пюпитр
     *       на южном фасаде между этажами, 8 реликвариев-витрин на стенах нефа
     *       (PURPLE_GLASS + GOLD_BLOCK + END_ROD).</li>
     *   <li>Богатый интерьер: 2 трона епископа+бискупа за алтарём
     *       (PURPLE_GLAZED_TERRACOTTA + GOLD_BLOCK), подсвечники-сталагмиты
     *       (POINTED_DRIPSTONE + END_ROD + SOUL_LANTERN) каждые 6 z-точек
     *       вдоль обеих стен нефа, 4 подвесных PURPLE_BANNER между колоннами,
     *       2 витражные стенки PURPLE_STAINED_GLASS между нефом и трансептом.</li>
     *   <li>Частицы ВНУТРИ собора (4E, все 4 типа): VILLAGER_HAPPY над алтарём,
     *       DRAGON_BREATH над пересечением, END_ROD по 6 колоннам,
     *       ENCHANTMENT_TABLE в апсиде.</li>
     *   <li>Аура снаружи (5BCD комбо): SPELL_WITCH потоки со шпилей наверх,
     *       4 SOUL_FIRE_FLAME-столба на углах собора (80 блоков), плюс
     *       сохранена пассивная аура (PORTAL+SOUL+ENCHANT в радиусе 50).</li>
     *   <li>Тёмный готический сад по ВСЕМУ периметру собора —
     *       PODZOL/COARSE_DIRT база, AZALEA/ROSE_BUSH/LILAC/DARK_OAK_SAPLING
     *       рандомно, 8 SOUL_LANTERN-фонарей на DARK_OAK_FENCE-столбиках.</li>
     * </ul>
     *
     * <p><b>v17</b>: PR 3.11 — финальная полировка по фидбэку v16:
     * <ul>
     *   <li>Крыша L-углов: заменена «диагональная» hip на ПИРАМИДНУЮ заливку
     *       (k×k квадрат на каждом уровне) — полностью без щелей.</li>
     *   <li>Готическая остроконечная АРКА на южном портале (STAIRS-jambs,
     *       END_ROD-сияние по контуру, GOLD+AMETHYST keystone, 2 лансет-витража
     *       по бокам) вместо квадратной двери-арки.</li>
     *   <li>6 выпирающих BAY-WINDOWS на длинных стенах нефа (3 на каждой,
     *       PURPLE_GLASS+GOLD рамка, выступают на 2 блока).</li>
     *   <li>Полигональная апсида — 5 вертикальных контрфорсов-выступов
     *       на северной стене, каждый с лансет-витражом и крокетом END_ROD.</li>
     *   <li>5 БОЛЬШИХ готических люстр-канделябров 5×5 (GOLD-крест +
     *       GLOWSTONE центр + END_ROD рожки + 8 SOUL_LANTERN/SHROOMLIGHT)
     *       на y=98, видны с пола и галереи.</li>
     *   <li>2-Й ЭТАЖ (Triforium gallery) — 2-блочная галерея вдоль
     *       обеих стен нефа на y=88, БАЛКОН в апсиде с видом на
     *       Большой Золотой Крест, 2 трона епископа на балконе,
     *       4 угловые лестницы в нефе.</li>
     *   <li>Потолочное освещение: SHROOMLIGHT-решётка (12 шт) на y=115
     *       + GLOWSTONE-вкрапления + 8 SOUL_LANTERN над колоннами.</li>
     *   <li>Центральная башня (вариант B): 4 ОТКРЫТЫЕ АРКИ у основания
     *       (видно через башню), 4 BELL на y=120 (по 4 сторонам),
     *       VIEWING PLATFORM y=130 с DARK_OAK скамьями + 4 SOUL_LANTERN
     *       на углах + витражные PURPLE_GLASS-окна по 4 сторонам.</li>
     * </ul>
     *
     * <p><b>v18</b>: PR 3.12 — серьёзная полировка по фидбэку v17:
     * <ul>
     *   <li>Крыша L-углов: убрана ступенчатая пирамида + диагональный
     *       POLISHED_BLACKSTONE-шрам, заменено на тонкое ровное hip-ребро
     *       45° по диагонали из DARK_OAK_LOG.</li>
     *   <li>Старые SHROOMLIGHT-канделябры (y=88) ОТКЛЮЧЕНЫ —
     *       больше не накладываются на новые большие 5×5 (y=98).</li>
     *   <li>Готическая АРКА на южном портале — теперь и САМ ПРОЁМ имеет
     *       стрельчатую форму (вырезана «балка», AIR заполняет gothic shape
     *       внутри арки).</li>
     *   <li>2-Й ЭТАЖ переделан: пропуск зоны пересечения нефа+трансепта,
     *       ШИРОКИЙ балкон 15×4 в апсиде с 4 SOUL_LANTERN + 2 трона +
     *       LECTERN, 2 ПАРАДНЫЕ лестницы 3×17 у южного входа (вместо
     *       4 кривых угловых).</li>
     *   <li>Внешний золотой крест 5×7 на северной стене апсиды
     *       (виден с улицы и сверху).</li>
     *   <li>FLECHE — декоративный шпиль 13 блоков на коньке апсиды
     *       (POLISHED_BLACKSTONE_BRICKS + END_ROD + AMETHYST на пике).</li>
     *   <li>КОЗЫРЁК (porch overhang) над южным входом — выступающая крыша
     *       3×11 STAIRS + 2 поддерживающие колонны + SOUL_LANTERN на CHAIN.</li>
     *   <li>4 НИШИ со статуями святых на южном фасаде (PIGLIN_HEAD-головы,
     *       нимбы END_ROD, балдахины STAIRS).</li>
     *   <li>Декоративные крокеты END_ROD по конькам нефа и трансепта.</li>
     * </ul>
     */
    public static final String GENERATED_FLAG = "eclipsia_world_generated_v32";

    // =========================================================================
    // ГЕОМЕТРИЯ ГОРОДА
    // =========================================================================

    /** Уровень мостовой города (соответствует {@code flatSettings} мира). */
    public static final int CITY_FLOOR_Y = 70;

    /**
     * Полигон городской стены — 22 вершины, неправильная форма (НЕ квадрат).
     * Координаты {@code (x, z)} обходятся по часовой стрелке. Используется:
     * <ul>
     *   <li>в фазе 1 — для замощения внутренней площади
     *       (POLISHED_DEEPSLATE);</li>
     *   <li>в фазе 6 (PR 2) — для трассировки самой стены и расстановки
     *       башен через 15-20 блоков по периметру.</li>
     * </ul>
     */
    public static final int[][] CITY_POLYGON = {
            {-126, -120}, {-114, -144}, { -60, -150}, {   0, -144}, {  60, -150},
            { 126, -135}, { 144, -105}, { 150,  -45}, { 144,    0}, { 150,   45},
            { 135,  105}, { 120,  114}, {  90,  120}, {  45,  126}, {   0,  120},
            { -45,  126}, { -90,  120}, {-120,  114}, {-135,  105},
            {-144,   45}, {-150,    0}, {-144,  -45}, {-126, -120},
    };

    // =========================================================================
    // КООРДИНАТЫ ЗДАНИЙ (для PR 2-5)
    // =========================================================================

    /** Центр собора. Принципиально НЕ в (0,0): смещён на восток (см. ТЗ). */
    public static final int CATHEDRAL_X = 45;
    public static final int CATHEDRAL_Z = -15;

    /** Координаты ворот: {@code [x, z]}. */
    // ВАЖНО: координаты ворот должны лежать НА полигоне (вершина или
    // ребро) — иначе wall trace не катит «gate slice» в этих клетках,
    // и проёма в стене не будет (как было до v29). Исправлено в v29:
    // все 4 ворот ровно на вершинах полигона, gate slice прорезает
    // настоящий проход в стене.
    public static final int[] SOUTH_GATE = {    0,  120 };  // вершина (0, 120)
    public static final int[] NORTH_GATE = {    0, -144 };  // вершина (0, -144)
    public static final int[] EAST_GATE  = {  144,    0 };  // вершина (144, 0)
    public static final int[] WEST_GATE  = { -150,    0 };  // вершина (-150, 0)

    // =========================================================================
    // ТОЧКА СПАВНА ИГРОКА
    // =========================================================================

    /**
     * Точка появления игрока после портала. Игрок стоит ПЕРЕД южными
     * воротами (z=35): на z=38 он смотрит на арку, за аркой — улицы
     * города и силуэт собора.
     *
     * <p>Сюда же телепортирует {@code GatekeeperArena} после убийства
     * Хранителя — координаты должны совпадать.
     */
    // v32: спавн перенесён в боковой грот (см. buildSpawnCave) —
    // игрок появляется внутри пещеры, не в чистом каньоне.
    public static final int SPAWN_X = -22;
    public static final int SPAWN_Y = CITY_FLOOR_Y + 1; // 71
    /**
     * z=130 — 10 блоков южнее вершины полигона/гейтхауса (0, 120),
     * в южном каньоне. Игрок стоит лицом к северу, перед ним арка
     * южных ворот, по бокам — стенки каньона из {@link WorldMountains}.
     */
    public static final int SPAWN_Z = 140;

    // =========================================================================
    // КООРДИНАТЫ ШПИЛЯ (для SpireParticles)
    // =========================================================================

    /**
     * Координаты «глаза» на вершине шпиля собора — читаются
     * {@link SpireParticles}. До постройки собора указывают на ожидаемую
     * точку (45.5, 189.5, -14.5), после {@code CathedralBuilder.build()}
     * перезаписываются на фактическую (на случай будущего смещения).
     */
    public static volatile double spireCenterX = CATHEDRAL_X + 0.5;
    public static volatile double spireCenterY = CITY_FLOOR_Y + 141 + 0.5; // y=211.5 (Глаз, v14)
    public static volatile double spireCenterZ = CATHEDRAL_Z + 0.5;

    // =========================================================================
    // СОСТОЯНИЕ
    // =========================================================================

    private final Plugin plugin;
    private final World world;

    /** Заполняется в фазе 5; читается фазой 7 для расстановки FloatingText. */
    private ElikiumCity city;

    public WorldGenerator(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public boolean isAlreadyGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        Byte v = world.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == (byte) 1;
    }

    private void markGenerated() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    /** Сбросить маркер (используется командой принудительной регенерации). */
    public void resetMarker() {
        NamespacedKey key = new NamespacedKey(plugin, GENERATED_FLAG);
        world.getPersistentDataContainer().remove(key);
    }

    // =========================================================================
    // ГЛАВНАЯ ТОЧКА ВХОДА
    // =========================================================================

    /**
     * Запустить генерацию всех 8 фаз. {@code onFinish} вызывается, когда
     * последняя операция RegionPainter применена в мир.
     */
    public void generate(Runnable onFinish) {
        if (isAlreadyGenerated()) {
            plugin.getLogger().info("WorldGenerator(" + GENERATED_FLAG
                    + "): уже сгенерировано, пропуск.");
            world.setSpawnLocation(SPAWN_X, SPAWN_Y, SPAWN_Z);
            if (onFinish != null) onFinish.run();
            return;
        }

        plugin.getLogger().info("WorldGenerator: начинаю генерацию '"
                + world.getName() + "' (" + GENERATED_FLAG + ")…");
        long seed = world.getSeed() ^ 0xE11C1A77L; // «ELIKIA77»
        Random rng = new Random(seed);

        // На случай повторной попытки — стираем все наши FloatingText.
        FloatingText.removeAll(plugin, world);

        RegionPainter p = new RegionPainter(plugin, world, seed);
        p.begin();

        // ===== Фаза 1: ландшафт (мостовая внутри полигона) =====
        phase1Landscape(p, rng);

        // ===== Фаза 2: растительность (заглушка) =====
        phase2Vegetation(p, rng);

        // ===== Фаза 3: декорации (заглушка) =====
        phase3Decorations(p, rng);

        // ===== Фаза 4: улицы (заглушка) =====
        phase4Paths(p, rng);

        // ===== Фаза 5: точки интереса — здания (заглушка) =====
        phase5PointsOfInterest(p, rng);

        // ===== Фаза 6: крупные структуры — стена, ворота, собор (заглушка) =====
        phase6Structures(p, rng);

        // ===== Фаза 7: FloatingText (заглушка) =====
        // Запустится после flush() в onFinish — сущности можно спавнить
        // только когда блоки уже на месте.

        // Финал: применяем все накопленные операции одной асинхронной заливкой.
        p.flush(() -> {
            phase7FloatingText(rng);
            phase8Spawnpoint();
            markGenerated();
            plugin.getLogger().info("WorldGenerator: '" + world.getName()
                    + "' готов (8 фаз).");
            if (onFinish != null) onFinish.run();
        });
    }

    // =========================================================================
    // ФАЗА 1: ЛАНДШАФТ
    // =========================================================================

    /**
     * Замостить внутреннюю площадь городского полигона блоками
     * {@link Material#POLISHED_DEEPSLATE} на уровне {@link #CITY_FLOOR_Y}.
     *
     * <p>Внешний мир (за пределами полигона) НЕ трогается — он плоский
     * по {@code flatSettings} (bedrock 5 + stone 55 + dirt 9 + grass 1,
     * поверхность y=70). В PR 2-5 здесь добавятся горы, реки, биомы.
     *
     * <p>Также очищаем 60 блоков воздуха над мостовой — на случай, если
     * мир уже был заселён сущностями/деревьями ванильной генерации.
     */
    // =========================================================================
    // РЕЛЬЕФ + ОСТРОВ + КАНАЛ
    // =========================================================================

    private SimplexNoiseGenerator terrainNoise;

    /** Высота городского рельефа. */
    public int getCityHeight(int x, int z) {
        if (terrainNoise == null) return CITY_FLOOR_Y;
        double northRise = Math.max(0, (-z - 30) / 120.0) * 3.0;
        double detail = terrainNoise.noise(x * 0.012, z * 0.012) * 1.5;
        int h = CITY_FLOOR_Y + (int) Math.round(northRise + detail);
        return Math.max(CITY_FLOOR_Y, h);
    }

    /** Канал: синусоида z = 40 + 15*sin(x/45), ширина 6 блоков. */
    public static boolean isCanal(int x, int z) {
        double centerZ = 40.0 + 15.0 * Math.sin(x / 45.0);
        return Math.abs(z - centerZ) <= 3.0;
    }

    /** Центр канала Z для данного X. */
    public static double canalCenterZ(int x) {
        return 40.0 + 15.0 * Math.sin(x / 45.0);
    }

    /** Мост через канал (5 мостов, ширина 5 блоков каждый). */
    private static boolean isBridge(int x) {
        return (Math.abs(x) < 5) || (Math.abs(x + 55) < 5) || (Math.abs(x - 55) < 5)
                || (Math.abs(x + 110) < 4) || (Math.abs(x - 110) < 4);
    }

    /** Расстояние точки до ближайшего ребра полигона. */
    private static double distToPolygonEdge(int px, int pz) {
        return distanceToCityPolygon(px, pz);
    }

    /** Расстояние до ближайших из 4 ворот (евклидово, в плоскости XZ).
     *  Используется ElikiumHouses чтобы не ставить дома вплотную к гейтхаусам
     *  (в радиусе 18 блоков от точки ворот ничего не строим). */
    public static double distanceToNearestGate(int px, int pz) {
        int[][] gates = { SOUTH_GATE, NORTH_GATE, EAST_GATE, WEST_GATE };
        double minD = Double.MAX_VALUE;
        for (int[] g : gates) {
            double dx = px - g[0], dz = pz - g[1];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < minD) minD = d;
        }
        return minD;
    }

    /**
     * Расстояние от точки {@code (px, pz)} до ближайшего ребра городского
     * полигона. Публично — {@link WorldMountains}/{@link OuterTerrain}
     * читают его, чтобы знать, насколько близко к стене они находятся
     * и не строить горы прямо на стене.
     */
    public static double distanceToCityPolygon(int px, int pz) {
        double minDist = Double.MAX_VALUE;
        int n = CITY_POLYGON.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double d = ptSegDist(px, pz,
                    CITY_POLYGON[i][0], CITY_POLYGON[i][1],
                    CITY_POLYGON[j][0], CITY_POLYGON[j][1]);
            if (d < minDist) minDist = d;
        }
        return minDist;
    }

    private static double ptSegDist(double px, double pz,
                                     double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az;
        double len2 = dx * dx + dz * dz;
        if (len2 < 1e-9) return Math.sqrt((px - ax) * (px - ax) + (pz - az) * (pz - az));
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / len2));
        double cx = ax + t * dx, cz = az + t * dz;
        return Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
    }

    private void phase1Landscape(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase1: горная долина + город + канал…");
        terrainNoise = new SimplexNoiseGenerator(rng.nextLong());

        // === ЧАСТЬ A: Территория за пределами полигона — без воды и без
        //              «острова». Делаем «беговую дорожку» вдоль стены
        //              и лёгкий каменный foothill, остальной рельеф
        //              достроит {@link WorldMountains} в фазе 6. ===
        // Узкое кольцо вокруг полигона (skirt 0..7) — мостовая
        // POLISHED_DEEPSLATE/ANDESITE, ровно как в городе. Это убирает
        // ванильную траву около стен и даёт «бульвар» под стенами.
        // Без водного рва: визуально город теперь часть горной долины.
        int skirtMin = -250, skirtMax = 250;
        for (int x = skirtMin; x <= skirtMax; x++) {
            for (int z = skirtMin; z <= skirtMax; z++) {
                if (isInsideCityPolygon(x, z)) continue;

                double dist = distanceToCityPolygon(x, z);
                if (dist > 7.5) continue; // skirt-кольцо

                // Замостить y=70 темным камнем, очистить воздух выше.
                int bucket = Math.floorMod(x * 7 + z * 13, 10);
                Material mat = bucket < 7 ? Material.POLISHED_DEEPSLATE
                        : Material.DEEPSLATE_BRICKS;
                // Заполняем дёрн вниз, чтобы не было дырки в склоне.
                for (int y = CITY_FLOOR_Y - 2; y < CITY_FLOOR_Y; y++) {
                    p.place(x, y, z, Material.DEEPSLATE);
                }
                p.place(x, CITY_FLOOR_Y, z, mat);
                // Очистить воздух над дорожкой.
                for (int y = CITY_FLOOR_Y + 1; y <= CITY_FLOOR_Y + 8; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }

        // === ЧАСТЬ A2: Южный каньон — декорированная тропа от южных ворот ===
        // Игрок появляется на (0, 75, 130) перед южными воротами (0, 120)
        // и идёт к ним по этой тропе. Каньон шириной 60 блоков (x∈[-30..30]),
        // длиной 170 блоков (z=121..290). v31: вместо ровной COBBLESTONE-полосы
        // — мощёный «ковёр» POLISHED_DEEPSLATE с золотыми акцентами,
        // обочины из MOSSY_COBBLESTONE с травой, лужи воды в канавках.
        for (int x = -30; x <= 30; x++) {
            for (int z = 121; z <= 290; z++) {
                if (isInsideCityPolygon(x, z)) continue;
                // Только если ещё не покрыто скиртом из части A.
                double dist = distanceToCityPolygon(x, z);
                if (dist <= 7.5) continue;

                // Подстилающий слой DEEPSLATE.
                for (int y = CITY_FLOOR_Y - 2; y < CITY_FLOOR_Y; y++) {
                    p.place(x, y, z, Material.DEEPSLATE);
                }
                Material floor;
                int absX = Math.abs(x);
                if (absX <= 3) {
                    // Центральный «ковёр» 7×... блоков — POLISHED_DEEPSLATE
                    // с золотыми поясами GILDED_BLACKSTONE каждые 6 блоков.
                    if (z % 6 == 0) {
                        floor = Material.GILDED_BLACKSTONE;
                    } else if (absX == 0) {
                        floor = Material.POLISHED_DEEPSLATE;
                    } else {
                        int bucket = Math.floorMod(x * 11 + z * 5, 10);
                        floor = bucket < 8 ? Material.POLISHED_DEEPSLATE
                                : Material.DEEPSLATE_BRICKS;
                    }
                } else if (absX <= 8) {
                    // Боковая мощёная полоса — COBBLESTONE/MOSSY (классика).
                    int bucket = Math.floorMod(x * 11 + z * 5, 10);
                    floor = bucket < 6 ? Material.COBBLESTONE
                            : Material.MOSSY_COBBLESTONE;
                } else if (absX <= 18) {
                    // Обочина — травянистая, со щебнем.
                    int bucket = Math.floorMod(x * 7 + z * 13, 10);
                    if (bucket < 5) floor = Material.COBBLED_DEEPSLATE;
                    else if (bucket < 7) floor = Material.GRAVEL;
                    else if (bucket < 9) floor = Material.MOSSY_COBBLESTONE;
                    else floor = Material.PODZOL;
                } else {
                    floor = Material.COBBLED_DEEPSLATE;
                }
                p.place(x, CITY_FLOOR_Y, z, floor);
                // Очистить воздух над тропой (на случай grass из flatworld).
                for (int y = CITY_FLOOR_Y + 1; y <= CITY_FLOOR_Y + 6; y++) {
                    p.place(x, y, z, Material.AIR);
                }
            }
        }

        // === ЧАСТЬ A2c: Пещера-спавн на (0, 75, 130) ===
        // Игрок телепортируется из Берега в эту точку. Раньше он
        // появлялся в чистом поле — нет ощущения «прохода». v31:
        // строим вокруг спавна пещерную кашалотную пасть, открытую
        // на север (к воротам), чтобы игрок «выходил из недр».
        buildSpawnCave(p);

        // === ЧАСТЬ A2b: Декор каньона — фонари, статуи, лужи, кустарники ===
        // Делаем каньон не просто прямой кишкой, а живой готической дорогой.
        for (int z = 138; z <= 280; z += 8) {
            // Фонарные столбы попеременно — ближе/дальше от тропы для зигзага.
            int side = (z / 8) % 2 == 0 ? -10 : 10;
            buildCanyonLanternPost(p, side, z);
            buildCanyonLanternPost(p, -side, z + 4);
        }
        // Каменные пьедесталы-статуи каждые 24 блока по обоим бортам.
        for (int z = 150; z <= 270; z += 24) {
            buildCanyonStatue(p, -16, z);
            buildCanyonStatue(p, +16, z + 12);
        }
        // AMETHYST_CLUSTER пятна на земле возле стенок каньона.
        for (int z = 132; z <= 285; z += 5) {
            int x = (z * 7919) % 5 == 0 ? -23 : +23;
            // 30% шанс кластера, иначе просто wet patch.
            if ((z * 31 + 17) % 7 == 0) {
                p.place(x, CITY_FLOOR_Y, z, Material.SMOOTH_BASALT);
                p.place(x, CITY_FLOOR_Y + 1, z, Material.AMETHYST_CLUSTER);
            } else if ((z * 31 + 17) % 7 == 1) {
                // Маленькая лужа в выщербине.
                p.place(x, CITY_FLOOR_Y - 1, z, Material.STONE_BRICKS);
                p.place(x, CITY_FLOOR_Y, z, Material.WATER);
            } else if ((z * 31 + 17) % 7 == 2) {
                // Кустарник DEAD_BUSH — мрачный готический штрих.
                p.place(x, CITY_FLOOR_Y + 1, z, Material.DEAD_BUSH);
            }
        }

        // === ЧАСТЬ B: Внутри полигона — заполнение опоры + мостовая + канал ===
        int paved = 0;
        for (int x = -150; x <= 150; x++) {
            for (int z = -150; z <= 126; z++) {
                if (!isInsideCityPolygon(x, z)) continue;

                int groundY = getCityHeight(x, z);
                boolean canal = isCanal(x, z) && !isBridge(x)
                        && !insideCathedralZone(x, z)
                        && !insidePlazaZone(x, z);

                if (canal) {
                    // Канал: каменное дно + 4 блока воды (y=67..70)
                    for (int y = CITY_FLOOR_Y - 5; y <= CITY_FLOOR_Y - 4; y++) {
                        p.place(x, y, z, Material.STONE_BRICKS);
                    }
                    for (int y = CITY_FLOOR_Y - 3; y <= CITY_FLOOR_Y; y++) {
                        p.place(x, y, z, Material.WATER);
                    }
                } else {
                    for (int y = CITY_FLOOR_Y; y <= groundY; y++) {
                        if (y == groundY) {
                            int bucket = Math.floorMod(x * 7 + z * 13, 10);
                            Material mat = bucket < 7 ? Material.POLISHED_DEEPSLATE
                                    : Material.DEEPSLATE_BRICKS;
                            p.place(x, y, z, mat);
                        } else {
                            p.place(x, y, z, Material.STONE);
                        }
                    }
                }
                int clearFrom = canal ? CITY_FLOOR_Y + 1 : groundY + 1;
                for (int dy = clearFrom; dy <= CITY_FLOOR_Y + 60; dy++) {
                    p.place(x, dy, z, Material.AIR);
                }
                paved++;
            }
        }

        // === ЧАСТЬ C: Берега канала — каменные перила ===
        for (int x = -150; x <= 150; x++) {
            for (int z = -150; z <= 126; z++) {
                if (!isInsideCityPolygon(x, z)) continue;
                if (!isCanal(x, z) || isBridge(x) || insideCathedralZone(x, z)
                        || insidePlazaZone(x, z)) continue;
                for (int[] off : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                    int nx = x + off[0], nz = z + off[1];
                    if (!isCanal(nx, nz) || isBridge(nx)) {
                        p.place(x, CITY_FLOOR_Y + 1, z, Material.STONE_BRICK_WALL);
                        break;
                    }
                }
            }
        }

        plugin.getLogger().info("WorldGenerator/phase1: остров + " + paved
                + " блоков мостовой + канал.");
    }

    /**
     * v32: Пещера-грот в БОКОВОЙ скале каньона (запад, x≈-25), открытая
     * НА ВОСТОК в каньон через большую готическую арку. Игрок выходит
     * из недр горы и идёт на север к арке Эликиума, а не появляется
     * в чистом поле посреди дороги.
     *
     * <p>Точка телепорта (0, 75, 130) перенесена в GatekeeperArena
     * на (-22, 75, 140) — внутрь грота. Вне грота, у выхода в каньон,
     * стоит большая арка в стиле берегового портала.
     */
    private void buildSpawnCave(RegionPainter p) {
        int cx = -25, cz = 140; // центр купола в западной стене каньона
        int yFloor = CITY_FLOOR_Y; // 70
        int yCeil = CITY_FLOOR_Y + 8; // 78

        // 1) Эллипсоидный грот 16×14×8 с куполом.
        int radX = 8;
        int radZ = 7;
        int radY = 8;
        for (int dx = -radX; dx <= radX; dx++) {
            for (int dz = -radZ; dz <= radZ; dz++) {
                for (int dy = 0; dy <= radY; dy++) {
                    double ex = (double) dx / radX;
                    double ez = (double) dz / radZ;
                    double ey = (double) dy / radY;
                    double r = ex * ex + ez * ez + ey * ey;
                    if (r > 1.05) continue;
                    int x = cx + dx;
                    int z = cz + dz;
                    int y = yFloor + dy;
                    if (r > 0.85) {
                        int hash = (x * 7 + z * 13 + y * 31);
                        Material shell = (hash & 7) == 0 ? Material.BLACKSTONE
                                : (hash & 7) == 1 ? Material.TUFF
                                : (hash & 7) == 2 ? Material.COBBLED_DEEPSLATE
                                : Material.DEEPSLATE;
                        p.place(x, y, z, shell);
                    } else {
                        p.place(x, y, z, Material.AIR);
                    }
                }
            }
        }

        // 2) Восточный «выход» — большая стрельчатая арка в восточной
        //    стене (x = cx + radX = -17). Открывает 7×6 проём в каньон.
        int archX = cx + radX; // -17
        for (int dz = -3; dz <= 3; dz++) {
            for (int dy = 0; dy <= 6; dy++) {
                int absDz = Math.abs(dz);
                // Стрельчатый профиль (заострение наверху).
                if (absDz == 3 && dy >= 5) continue;
                if (absDz == 2 && dy >= 6) continue;
                // Очистить 4 блока в глубину наружу — туннель к каньону.
                for (int dxOut = 0; dxOut <= 4; dxOut++) {
                    p.place(archX + dxOut, yFloor + dy, cz + dz, Material.AIR);
                }
            }
        }
        // 2a) Каменная кромка арки снаружи — GILDED_BLACKSTONE-замковый камень.
        for (int dz = -4; dz <= 4; dz++) {
            int absDz = Math.abs(dz);
            int topDy = absDz <= 1 ? 7 : (absDz <= 2 ? 6 : (absDz <= 3 ? 5 : 4));
            for (int dy = 0; dy <= topDy; dy++) {
                if (dy < topDy && absDz < 4) continue; // только кромка
                Material edge;
                if (dy == topDy && absDz == 0) edge = Material.GILDED_BLACKSTONE;
                else if (dy == topDy) edge = Material.POLISHED_BLACKSTONE;
                else edge = Material.DEEPSLATE_BRICKS;
                p.place(archX + 4, yFloor + dy, cz + dz, edge);
            }
        }
        // 2b) Замковый «глаз» AMETHYST_BLOCK + LANTERN над аркой.
        p.place(archX + 4, yFloor + 8, cz, Material.AMETHYST_BLOCK);
        p.place(archX + 4, yFloor + 9, cz, Material.LIGHTNING_ROD);
        p.place(archX + 4, yFloor + 8, cz - 1, Material.SOUL_LANTERN);
        p.place(archX + 4, yFloor + 8, cz + 1, Material.SOUL_LANTERN);

        // 3) Пол грота — POLISHED_DEEPSLATE с GILDED-кругом в центре.
        for (int dx = -radX; dx <= radX; dx++) {
            for (int dz = -radZ; dz <= radZ; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double r = (double) (dx * dx) / (radX * radX)
                         + (double) (dz * dz) / (radZ * radZ);
                if (r > 1.0) continue;
                int dist2 = dx * dx + dz * dz;
                Material floor;
                if (dist2 <= 1) floor = Material.AMETHYST_BLOCK;
                else if (dist2 <= 9) floor = Material.GILDED_BLACKSTONE;
                else floor = Material.POLISHED_DEEPSLATE;
                p.place(x, yFloor - 1, z, Material.DEEPSLATE);
                p.place(x, yFloor, z, floor);
            }
        }
        // Дорожка-«коврик» от центра грота к выходной арке.
        for (int dx = 0; dx <= radX + 3; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material mat = dz == 0 ? Material.GILDED_BLACKSTONE
                        : Material.POLISHED_DEEPSLATE;
                p.place(cx + dx, yFloor, cz + dz, mat);
            }
        }

        // 4) Подсветка — SOUL_LANTERN на цепях у потолка по кругу.
        for (int[] off : new int[][]{
                {-5, -3}, {3, -3}, {-5, 3}, {3, 3}, {-7, 0}, {2, 0}}) {
            int x = cx + off[0];
            int z = cz + off[1];
            p.place(x, yCeil - 1, z, Material.CHAIN);
            p.place(x, yCeil - 2, z, Material.SOUL_LANTERN);
        }

        // 5) Аметистовые «друзы» по стенам.
        for (int[] off : new int[][]{
                {-7, 0}, {-6, 5}, {-6, -5}, {-3, 6}, {-3, -6}}) {
            int x = cx + off[0];
            int z = cz + off[1];
            p.place(x, yFloor + 1, z, Material.AMETHYST_CLUSTER);
        }

        // 6) Туннель снаружи арки до каньона — расчищаем коридор
        //    от x = archX+5 до x = -10 (вход в каньон, |x|<=30).
        for (int x = archX + 5; x <= -10; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    p.place(x, yFloor + dy, cz + dz, Material.AIR);
                }
                // мостовая POLISHED_DEEPSLATE по дну туннеля
                p.place(x, yFloor - 1, cz + dz, Material.DEEPSLATE);
                Material floor = dz == 0 ? Material.GILDED_BLACKSTONE
                        : Material.POLISHED_DEEPSLATE;
                p.place(x, yFloor, cz + dz, floor);
            }
            // потолок
            for (int dz = -2; dz <= 2; dz++) {
                p.place(x, yFloor + 6, cz + dz,
                        Math.abs(dz) == 2 ? Material.COBBLED_DEEPSLATE
                                : Material.POLISHED_DEEPSLATE);
            }
        }

        // 7) Большая «береговая» арка на ВЫХОДЕ туннеля в каньон (x=-10).
        //    Стилизована под портал на пляже — DARK_OAK_LOG колонны
        //    + STONE_BRICKS перекрытие + 4 SEA_LANTERN.
        int gateX = -10;
        for (int dy = 0; dy <= 6; dy++) {
            p.place(gateX, yFloor + dy, cz - 3, Material.POLISHED_BLACKSTONE_BRICKS);
            p.place(gateX, yFloor + dy, cz + 3, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int dz = -3; dz <= 3; dz++) {
            p.place(gateX, yFloor + 7, cz + dz,
                    Math.abs(dz) == 0 ? Material.GILDED_BLACKSTONE
                            : Material.POLISHED_BLACKSTONE_BRICKS);
        }
        p.place(gateX, yFloor + 8, cz, Material.AMETHYST_BLOCK);
        p.place(gateX, yFloor + 9, cz, Material.LIGHTNING_ROD);
        p.place(gateX - 1, yFloor + 6, cz - 2, Material.SEA_LANTERN);
        p.place(gateX - 1, yFloor + 6, cz + 2, Material.SEA_LANTERN);
        p.place(gateX + 1, yFloor + 6, cz - 2, Material.SEA_LANTERN);
        p.place(gateX + 1, yFloor + 6, cz + 2, Material.SEA_LANTERN);
    }

    /**
     * Фонарный столб для каньона: DARK_OAK_FENCE 4 блока + SOUL_LANTERN
     * сверху + CHAIN-кронштейн в сторону тропы.
     */
    private void buildCanyonLanternPost(RegionPainter p, int x, int z) {
        for (int dy = 1; dy <= 3; dy++) {
            p.place(x, CITY_FLOOR_Y + dy, z, Material.DARK_OAK_FENCE);
        }
        p.place(x, CITY_FLOOR_Y + 4, z, Material.SOUL_LANTERN);
        // Пьедестал.
        p.place(x, CITY_FLOOR_Y, z, Material.POLISHED_BLACKSTONE);
    }

    /**
     * Каменная статуя стража у обочины каньона: пьедестал
     * POLISHED_BLACKSTONE 1×1×2, корпус из COBBLED_DEEPSLATE,
     * аметистовый «глаз» сверху.
     */
    private void buildCanyonStatue(RegionPainter p, int x, int z) {
        // Пьедестал 3×3.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(x + dx, CITY_FLOOR_Y, z + dz,
                        Material.POLISHED_BLACKSTONE);
            }
        }
        // Корпус 1×1×3.
        p.place(x, CITY_FLOOR_Y + 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
        p.place(x, CITY_FLOOR_Y + 2, z, Material.COBBLED_DEEPSLATE);
        p.place(x, CITY_FLOOR_Y + 3, z, Material.COBBLED_DEEPSLATE);
        // Голова + аметистовый «глаз».
        p.place(x, CITY_FLOOR_Y + 4, z, Material.AMETHYST_BLOCK);
        // Вертикальные «крылья» по бокам — DARK_OAK_FENCE.
        p.place(x, CITY_FLOOR_Y + 5, z, Material.SOUL_TORCH);
    }

    // =========================================================================
    // ФАЗА 2: РАСТИТЕЛЬНОСТЬ (PR 5 — внешние биомы; внутри города почти пусто)
    // =========================================================================

    private void phase2Vegetation(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase2: растительность — TODO в PR 5.");
    }

    // =========================================================================
    // ФАЗА 3: ДЕКОРАЦИИ (PR 5 — бочки, цветы, дрова, телега, колокол)
    // =========================================================================

    private void phase3Decorations(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase3: декорации — TODO в PR 5.");
    }

    // =========================================================================
    // ФАЗА 4: УЛИЦЫ (PR 4 — изгибающиеся 3-7 блоков, фонари)
    // =========================================================================

    private void phase4Paths(RegionPainter p, Random rng) {
        // Улицы строятся вместе со зданиями в ElikiumCity (фаза 5).
        // Здесь явный no-op оставлен для совместимости со схемой 8 фаз.
    }

    // =========================================================================
    // ФАЗА 5: ТОЧКИ ИНТЕРЕСА — ЗДАНИЯ + УЛИЦЫ + ПЛОЩАДЬ (PR 3.15)
    // =========================================================================

    private void phase5PointsOfInterest(RegionPainter p, Random rng) {
        plugin.getLogger().info("WorldGenerator/phase5: интерьер города (улицы, площади, POI, дома, декор).");
        this.city = new ElikiumCity(plugin, p, rng);
        // Регистрируем зоны ворот как occupied ДО строительства домов
        registerGateZones();
        this.city.build();
    }

    /** Зоны ворот (±12 блоков) отмечены как occupied чтобы дома не наезжали. */
    private void registerGateZones() {
        if (city == null) return;
        int[][] gates = {SOUTH_GATE, NORTH_GATE, EAST_GATE, WEST_GATE};
        for (int[] g : gates) {
            city.occupied.add(new ElikiumCity.Footprint(
                    g[0] - 12, g[1] - 12, g[0] + 12, g[1] + 12));
        }
    }

    // =========================================================================
    // ФАЗА 6: КРУПНЫЕ СТРУКТУРЫ — СТЕНА, ВОРОТА, СОБОР (PR 2 + PR 3)
    // =========================================================================

    private void phase6Structures(RegionPainter p, Random rng) {
        plugin.getLogger().info(
                "WorldGenerator/phase6: стена + горы + собор.");

        // PR 2 / часть A: стена-полигон + 4 ворот + башни.
        new ElikiumWall(plugin, p, rng).build();

        // PR 2 / часть B: южные горы (z=170..480, x=±600, h=40..130).
        new WorldMountains(plugin, p, rng).build();

        // PR 3: собор на (45, -15) 60×84, h=120. Шпиль с глазом и флагом
        // обновляет WorldGenerator.spireCenterX/Y/Z для SpireParticles.
        new CathedralBuilder(plugin, p, rng).build();
    }

    // =========================================================================
    // ФАЗА 7: FLOATING TEXT (PR 5)
    // =========================================================================

    private void phase7FloatingText(Random rng) {
        if (city == null) {
            plugin.getLogger().info("WorldGenerator/phase7: FloatingText — нет POI (город не построен).");
            return;
        }
        int placed = 0;
        for (ElikiumCity.POI poi : city.getPois()) {
            try {
                FloatingText.createLocationTitle(plugin, world,
                        poi.x + 0.5, poi.y + 0.1, poi.z + 0.5,
                        poi.title, poi.subtitle);
                placed++;
            } catch (Throwable t) {
                plugin.getLogger().warning("WorldGenerator/phase7: не удалось спавнить FloatingText '"
                        + poi.title + "': " + t.getMessage());
            }
        }
        plugin.getLogger().info("WorldGenerator/phase7: размещено " + placed + " FloatingText.");
    }

    // =========================================================================
    // ФАЗА 8: ТОЧКА СПАВНА
    // =========================================================================

    /**
     * Зафиксировать точку спавна мира на {@link #SPAWN_X}/{@link #SPAWN_Y}/
     * {@link #SPAWN_Z} = (0, 75, 38) — игрок появится прямо перед южными
     * воротами Эликия (ворота на z=35, спавн на z=38, лицом на север).
     */
    private void phase8Spawnpoint() {
        Location spawn = new Location(world, SPAWN_X + 0.5, SPAWN_Y, SPAWN_Z + 0.5);
        world.setSpawnLocation(spawn);
        plugin.getLogger().info("WorldGenerator/phase8: spawn = ("
                + SPAWN_X + ", " + SPAWN_Y + ", " + SPAWN_Z + ")");
    }

    // =========================================================================
    // УТИЛИТЫ ПОЛИГОНА
    // =========================================================================

    /**
     * Точка-в-полигоне (ray casting). Используется для замощения внутренней
     * площади города и (в PR 2) для трассировки стены.
     *
     * <p>Алгоритм классический: пускаем горизонтальный луч в +X из точки
     * (px, pz) и считаем количество пересечений с рёбрами полигона —
     * нечётное число пересечений = точка внутри.
     */
    /** Точка внутри зоны собора? Публичный доступ для канала/рельефа. */
    public static boolean insideCathedralZone(int x, int z) {
        return x >= CATHEDRAL_X - 34 && x <= CATHEDRAL_X + 34
            && z >= CATHEDRAL_Z - 46 && z <= CATHEDRAL_Z + 46;
    }

    /**
     * v32: исключение для канала — городские площади. Канал проходил
     * по краю Соборной площади (x∈[35..55], z∈[25..51]) и Рыночной
     * площади, выглядело как «постройка через канаву». Исключаем.
     */
    public static boolean insidePlazaZone(int x, int z) {
        // Соборная площадь ElikiumPlazas.CATH_PLAZA — расширим запас на 4 блока.
        if (x >= 35 - 4 && x <= 55 + 4 && z >= 25 - 4 && z <= 51 + 4) return true;
        // Рыночная площадь ElikiumPlazas.MARKET_PLAZA — расширим запас на 4 блока.
        if (x >= -39 - 4 && x <= -21 + 4 && z >= 54 - 4 && z <= 76 + 4) return true;
        return false;
    }

    public static boolean isInsideCityPolygon(int px, int pz) {
        boolean inside = false;
        int n = CITY_POLYGON.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = CITY_POLYGON[i][0], zi = CITY_POLYGON[i][1];
            int xj = CITY_POLYGON[j][0], zj = CITY_POLYGON[j][1];
            boolean intersect = ((zi > pz) != (zj > pz))
                    && (px < (double) (xj - xi) * (pz - zi) / (zj - zi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
}

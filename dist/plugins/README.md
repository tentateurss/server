# dist/plugins

Готовые к деплою JAR-ы плагинов. Скопируй файлы из этой папки в
`plugins/` своего сервера и сделай `/reload` (или рестарт).

| Файл                  | Источник                                       |
| --------------------- | ---------------------------------------------- |
| `EclipsiaCore.jar`    | `EclipsiaCore/target/EclipsiaCore-*.jar`       |
| `EclipsiaItems.jar`   | `EclipsiaItems/target/EclipsiaItems-*.jar`     |
| `EclipsiaSkills.jar`  | `EclipsiaSkills/target/EclipsiaSkills.jar`     |
| `EclipsiaMobs.jar`    | `EclipsiaMobs/target/EclipsiaMobs-*.jar`       |
| `EclipsiaBuilder.jar` | `EclipsiaBuilder/target/EclipsiaBuilder.jar`   |
| `EclipsiaLobby.jar`   | `EclipsiaLobby/target/EclipsiaLobby.jar`       |
| `EclipsiaPerks.jar`   | `EclipsiaPerks/target/EclipsiaPerks-*.jar`     |

Пересборка вручную:

```bash
for m in EclipsiaCore EclipsiaItems EclipsiaSkills EclipsiaMobs \
         EclipsiaBuilder EclipsiaLobby EclipsiaPerks; do
  (cd "$m" && mvn -q -DskipTests clean install) || break
done
```

После сборки JAR-ы лежат в `<module>/target/`. Перенос в `dist/plugins/`
сделан вручную в этом коммите, чтобы тебе не нужно было поднимать Maven
локально.

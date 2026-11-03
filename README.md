# VPS Cockpit

Android-клиент для [Cockpit](https://cockpit-project.org/): SSH-туннель с телефона на VPS и WebView с мобильным Matrix Core UI (монитор / пульт).

**Community-сборка не содержит серверных паролей, ключей и сертификатов.** Подключение настраивает пользователь при первом запуске.

## Что нужно на сервере

1. SSH-доступ (логин + пароль)
2. Установленный Cockpit (часто порт `9090` на localhost)
3. Cockpit не обязан быть открыт в интернет - приложение ходит через SSH

## Что вводит пользователь в приложении

| Поле | Смысл | Типичное значение |
|------|--------|-------------------|
| Host / IP | адрес VPS | `203.0.113.10` или домен |
| SSH user | пользователь SSH | например `root` |
| SSH password | пароль SSH | — |
| SSH port | порт SSH | `22` |
| Cockpit port | порт Cockpit на сервере | `9090` |
| Local port | порт туннеля на телефоне | `19090` |

Данные хранятся только на устройстве. Долгий тап по строке статуса - сменить сервер.

## Сборка Community

```bash
./gradlew :app:assembleCommunityDebug
```

APK: `app/build/outputs/apk/community/debug/app-community-debug.apk`  
Application id: `ru.srr.vpscockpit.community`

Нужны JDK 17+ и Android SDK.

## Flavors

| Flavor | Application id | Назначение |
|--------|----------------|------------|
| `community` | `ru.srr.vpscockpit.community` | публичный билд, форма входа |
| `personal` | `ru.srr.vpscockpit` | локальный приватный билд (секреты не в git) |

Personal-секреты и ключи живут только в локальном `app/src/personal/` и `vps-secrets.properties` (оба в `.gitignore`).

## Как это работает

1. Приложение поднимает SSH local-forward: `127.0.0.1:<local> → 127.0.0.1:<cockpit>` на сервере
2. Открывает Cockpit по HTTPS на localhost телефона
3. Поверх - Matrix Core HUD (метрики, узлы, быстрые действия)

## Лицензия / автор

by SpustyaRukovaRecords

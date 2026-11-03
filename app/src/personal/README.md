# Personal flavor (локально)

Сюда кладутся файлы, которых нет в git:

- `res/raw/vps_ssh_key` — SSH private key (опционально)
- `res/raw/cockpit_ca` — CA сертификат Cockpit (опционально)
- `assets/vps_ssh_key` — если нужен дубль ключа в assets

Корневой `vps-secrets.properties` — host/user/password для personal BuildConfig.

Community-сборка эти файлы не использует.

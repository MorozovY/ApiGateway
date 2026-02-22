# Настройка доступа к ymorozov.ru с VPN

## Проблема

С VPN: ymorozov.ru не открывается (трафик идет через VPN туннель)
Без VPN: ymorozov.ru открывается (прямой доступ к 193.41.78.204)

## Решение: файл hosts

Добавляем локальную DNS запись, чтобы ymorozov.ru резолвился в localhost.

### Автоматическая настройка (Рекомендуется)

1. Запустите PowerShell **от администратора**:
   ```
   Правой кнопкой на Windows PowerShell → Запустить от имени администратора
   ```

2. Выполните команду:
   ```powershell
   cd G:\Projects\ApiGateway
   .\setup-local-domain.ps1
   ```

3. Готово! Проверьте:
   ```
   http://ymorozov.ru/
   ```

### Ручная настройка

1. Откройте Блокнот **от администратора**
2. Откройте файл: `C:\Windows\System32\drivers\etc\hosts`
3. Добавьте в конец файла:
   ```
   127.0.0.1 ymorozov.ru
   ```
4. Сохраните файл
5. Очистите DNS кеш:
   ```cmd
   ipconfig /flushdns
   ```

### Проверка

```bash
# Проверьте резолв
ping ymorozov.ru
# Должен показать: 127.0.0.1

# Проверьте HTTP
curl http://ymorozov.ru/
# Должен вернуть HTML
```

## Как работает

```
С VPN:
  ymorozov.ru → hosts файл → 127.0.0.1 → localhost:80 → nginx → приложение ✓

Без VPN:
  ymorozov.ru → hosts файл → 127.0.0.1 → localhost:80 → nginx → приложение ✓

С другого компьютера (без hosts):
  ymorozov.ru → DNS → 193.41.78.204 → роутер → nginx → приложение ✓
```

## Откат изменений

Чтобы вернуть доступ через интернет (без localhost):

1. Откройте `C:\Windows\System32\drivers\etc\hosts` от администратора
2. Удалите строку `127.0.0.1 ymorozov.ru`
3. Сохраните и выполните `ipconfig /flushdns`

## Альтернатива: Split Tunneling

Если используете VPN клиент с поддержкой split tunneling, добавьте исключение:

```
IP: 193.41.78.204/32
Действие: Bypass VPN (идти напрямую)
```

Настройка зависит от VPN клиента (OpenVPN, WireGuard, Cisco AnyConnect и т.д.).

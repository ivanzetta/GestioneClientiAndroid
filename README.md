# Gestione Clienti Android — sorgenti 0.6

**Prototipo NON compilato e NON testato su dispositivo. Non usare ancora con dati reali.**

## Novità
- Backup portatile completo di clienti e visite, cifrato AES-256-GCM con password (PBKDF2-HMAC-SHA256, 210.000 iterazioni, salt e nonce casuali).
- Ripristino transazionale: sostituisce integralmente clienti e visite solo dopo decifratura e validazione.
- Se la password del backup viene persa, non è possibile recuperarlo.

## Limiti
- Il database SQLite **attivo resta non cifrato**. L'autenticazione biometrica/PIN protegge l'interfaccia, non i file locali.
- Backup e ripristino sono operazioni sincrone sul thread UI: grandi archivi possono bloccare temporaneamente l'interfaccia.
- Import/export Excel non cifrati; conservare i file in modo sicuro.
- Mancano build APK, test strumentali, hardening della sicurezza e verifica della compatibilità con Pixel 6.
- Conservare separatamente una copia del backup prima di ogni ripristino.

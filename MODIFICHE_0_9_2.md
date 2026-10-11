# YellowKunde 0.9.2 — sorgenti in lavorazione

Implementato nei sorgenti: CAP, Info1–Info5 con checkbox e migrazione database da v2 a v3, personalizzazione etichette con conferma opzionale di azzeramento per tutti i clienti, filtro zone multiplo, sessione sbloccata nel passaggio fra app con invalidazione allo spegnimento schermo, appuntamento con durata 60 minuti, nome sviluppatore nelle informazioni, aggiornamento numero versione, CAP in indirizzi e Excel con compatibilità intestazioni precedenti.

## Verifiche necessarie prima di installare o distribuire
- Non è stato possibile compilare l'APK in questo ambiente: manca Android SDK/Gradle.
- Verificare comportamento autenticazione su Pixel 6 e Samsung A35, soprattutto dopo blocco schermo.
- Testare import/export Excel con vecchi e nuovi file e ripristino backup.
- Icona launcher e icone della homepage richiedono verifica grafica sul dispositivo.
- Il backup cifrato attuale salva i dati clienti ma non le etichette personalizzate nelle preferenze: fare attenzione durante il cambio telefono.
- Non disinstallare la versione precedente: fare un backup prima di aggiornare e usare la stessa chiave di firma.

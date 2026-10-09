# Gestione Clienti — aggiornamento 0.7.1

Pacchetto completo basato sul progetto Android e sulla versione 0.7.

## Funzioni incluse
- Grafica giallo Schachermayer (`#FAB300`), pulsanti arrotondati.
- Ordinamento clienti per nome, codice, città, zona, settore, ultima visita o visite annuali; crescente/decrescente e preferenza salvata.
- Schermata Opzioni con Excel, backup/ripristino, sicurezza, informazioni e lingua.
- Interfaccia italiano/tedesco con preferenza salvata.
- Permesso USE_BIOMETRIC nel manifest.
- Versione 0.7.1 (versionCode 8).

## Aggiornamento
1. Eseguire un backup e conservarlo fuori dal telefono.
2. Copiare il contenuto di questo progetto nel repository GitHub mantenendo la struttura delle cartelle.
3. Commit, push e compilazione con GitHub Actions.
4. Installare il nuovo APK senza disinstallare l'app, se firmato con la stessa chiave.

ATTENZIONE: gli APK debug compilati su runner diversi possono avere firme diverse. Se l'installazione restituisce INSTALL_FAILED_UPDATE_INCOMPATIBLE, non disinstallare prima di aver valutato la perdita dei dati e salvato un backup verificato. Non è stata verificata la cifratura del database locale.

Questa versione è codice sorgente; compilazione e test sul dispositivo sono ancora da eseguire.

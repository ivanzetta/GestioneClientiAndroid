# Compilare l'APK senza computer

Il progetto include un workflow GitHub Actions per compilare l'APK nel cloud.

1. Da smartphone, crea un repository GitHub **privato** e carica i file del progetto (compresa la cartella nascosta `.github/workflows`). GitHub via browser mobile potrebbe richiedere la modalità sito desktop; l'upload di un ZIP non ne estrae automaticamente i file.
2. Apri il repository > Actions > Compila APK Android > Run workflow.
3. Se la compilazione riesce, apri il risultato dell'esecuzione > Artifacts > GestioneClienti-debug-apk, scarica e decomprimi l'archivio; contiene `app-debug.apk`.
4. L'APK debug è destinato solo a prove, non a dati reali di clienti. Installare APK esterni richiede un'autorizzazione esplicita di Android e comporta rischi: usa soltanto il file compilato dal tuo repository.

## Stato
Questo workflow non è stato eseguito né verificato in questa sessione. L'APK non è ancora stato prodotto. Il database locale resta non cifrato; non usare dati reali finché non sono completati sicurezza e test.

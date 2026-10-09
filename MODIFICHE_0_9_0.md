# YellowKunde 0.9.0 - progetto sorgente

- Nome app YellowKunde e icona gialla senza logo aziendale (ricostruzione grafica).
- Attivazione offline al primo avvio con codice condiviso concordato; nel codice è incluso l’hash SHA-256 del codice, non il codice in chiaro.
- Barra elenco clienti compatta con azioni Nuovo, Filtri, Ordina, Opzioni; selettore Zona e azzeramento filtri.
- Identificativo applicazione invariato per consentire aggiornamenti.
- Base: sorgenti 0.8.5, inclusi i precedenti interventi Outlook.

## Limiti e verifiche
- La verifica offline non impedisce estrazione/aggiramento da parte di utenti esperti.
- Compilazione e prova su dispositivi Android non eseguite in questo ambiente.
- Per aggiornare un'app installata serve firmare l'APK con la stessa chiave di firma precedente.
- La pubblicazione Play richiede ulteriori verifiche, firma AAB, policy e test.

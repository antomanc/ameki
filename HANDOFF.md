# Handoff — App meteo Android open source con dati Google (WeatherNext)

> Documento di passaggio per l'agente che svilupperà il progetto. Contiene contesto, decisioni già prese, requisiti e criteri di "fatto".
> Nome del progetto: **Ameki** (あめき · 雨木). Nel testo originale `<app>`.

---

## 1. Contesto e obiettivo

Su Android i widget meteo "ricchi" di Google esistono solo sui Pixel. Sui Samsung (il device di riferimento è un **Galaxy Z Fold 8**) c'è solo il widget di riepilogo At a Glance, che è povero. Inoltre Google sta spostando la sua schermata meteo dentro i risultati di ricerca.

Le previsioni di Google sono tra le migliori disponibili (modello AI **WeatherNext 2** di DeepMind + MetNet + sistemi tradizionali) e sono accessibili agli sviluppatori tramite la **Weather API di Google Maps Platform**.

**Non esiste nessun progetto open source** che usi questa API per un'app o un widget Android: le app FOSS esistenti (Breezy Weather, WeatherMaster, Meteograph, ecc.) usano Open-Meteo, OpenWeather o i servizi nazionali.

**Obiettivo:** un'app meteo Android open source, moderna e bellissima, con **più widget belli, responsivi e funzionanti**, che usi di default i dati Google ma permetta di scegliere **Open-Meteo** come alternativa senza key.

---

## 2. Principi (in ordine di priorità)

1. **I widget sono il prodotto principale.** Devono essere davvero belli, curati come quelli dei Pixel o meglio, adattarsi a più dimensioni e aggiornarsi in modo affidabile.
2. **UX semplicissima.** Niente menu profondi né opzioni ovunque. Setup iniziale in pochi passi, impostazioni chiare. Se una cosa si può avere con un default ragionevole, si usa il default.
3. **Grafica moderna:** Material 3 Expressive, Material You (colori dinamici), animazioni morbide, tipografia curata. Riferimento visivo: la schermata meteo di Google/Pixel Weather (attuale, oraria, 10 giorni, schede dettagli), ma con identità propria.
4. **Modulare.** I provider dati sono intercambiabili e le impostazioni (es. intervallo di aggiornamento) sono centralizzate, così aggiungere opzioni domani è banale.
5. **Open source pulito:** niente key nel repo, niente tracking, compatibile con F-Droid.

---

## 3. Decisioni tecniche già prese

| Area | Scelta |
|---|---|
| Linguaggio | Kotlin |
| UI app | Jetpack Compose + Material 3 (Expressive) + dynamic color |
| Widget | **Jetpack Glance** (`SizeMode.Responsive` / `Exact`), con Material You nei widget |
| Background | WorkManager (periodico, rispetta Doze e batteria) |
| Rete | Ktor o Retrofit + kotlinx.serialization |
| Cache | Room (ultimo snapshot per ogni località), così widget e app funzionano offline e non fanno chiamate a ogni apertura |
| Preferenze | DataStore; API key cifrata con Android Keystore |
| DI | Hilt (o Koin, a scelta motivata) |
| minSdk | 31 (Android 12: dynamic color nei widget). targetSdk: l'ultimo stabile |
| Licenza | GPL-3.0 (da confermare con Antonio; alternativa MIT) |
| Distribuzione | GitHub Releases (build firmate da CI) → poi F-Droid |

### Struttura moduli suggerita

```
:app                    → entry point, navigazione, DI
:core:model             → modello dominio unico (WeatherSnapshot, Current, Hourly, Daily, Alert, Location, Units)
:core:data              → repository, cache Room, scheduler aggiornamenti, settings
:core:ui                → design system: tema, icone meteo, componenti condivisi app+widget
:provider:api           → interfaccia WeatherProvider + capabilities
:provider:google        → implementazione Google Weather API
:provider:openmeteo     → implementazione Open-Meteo
:feature:onboarding     → setup iniziale
:feature:home           → schermata meteo
:feature:locations      → gestione località
:feature:settings       → impostazioni
:widget                 → tutti i widget Glance
```

**Regola chiave:** UI e widget conoscono **solo** il modello di `:core:model`. Ogni provider mappa la propria risposta su quel modello e dichiara cosa supporta:

```kotlin
interface WeatherProvider {
    val id: ProviderId
    val capabilities: Set<Capability>   // CURRENT, HOURLY, DAILY, ALERTS, AIR_QUALITY, POLLEN, ...
    suspend fun fetch(location: GeoPoint, units: Units, lang: String): WeatherSnapshot
}
```

La UI nasconde le sezioni per cui il provider attivo non ha dati, senza errori o schede vuote. Aggiungere un terzo provider significa solo un nuovo modulo.

---

## 4. Provider dati

### 4.1 Google Maps Platform — Weather API (default consigliato)

- Base URL: `https://weather.googleapis.com`. Endpoint (`GET`, parametri `key`, `location.latitude`, `location.longitude`, `unitsSystem`, `languageCode`):
  - `/v1/currentConditions:lookup` — condizioni attuali
  - `/v1/forecast/hours:lookup` — fino a 240 ore (**paginato**: gestire `pageSize`/`pageToken`, verificare i limiti nella doc)
  - `/v1/forecast/days:lookup` — fino a 10 giorni (contiene anche alba/tramonto e fasi lunari)
  - `/v1/history/hours:lookup` — ultime 24 ore (opzionale, es. "ieri a quest'ora")
  - `/v1/publicAlerts:lookup` — allerte ufficiali (es. "Allerta temporale violento – Lazio")
- La doc elenca anche "minute forecast" e "weather maps": verificare disponibilità e costi prima di usarli.
- **Costo:** SKU "Weather Usage", 10.000 chiamate/mese gratis, poi 0,15 $ ogni 1000. Serve un progetto Google Cloud **con fatturazione attiva** anche restando nella fascia gratuita.
- **Modello open source: ogni utente usa la propria API key.** Nessuna key nel repo o nell'APK.
- **Attribuzione obbligatoria:** rispettare le [policy di attribuzione della Weather API](https://developers.google.com/maps/documentation/weather/policies) sia nell'app sia nei widget (vale anche per le icone/condizioni Google, se usate).
- **Niente geocoding** in questa API: per la ricerca città usare il geocoding di Open-Meteo (gratis), oppure `android.location.Geocoder`. Places API costa: evitarla.

Doc: https://developers.google.com/maps/documentation/weather — reference REST: https://developers.google.com/maps/documentation/weather/reference/rest

### 4.2 Open-Meteo (alternativa senza key)

- `https://api.open-meteo.com/v1/forecast` (current, hourly, daily in una sola chiamata), geocoding `https://geocoding-api.open-meteo.com/v1/search`, air quality `https://air-quality-api.open-meteo.com/v1/air-quality`.
- Gratis senza key **per uso non commerciale**. Indicare la licenza dati (CC BY 4.0) nella schermata info.
- Allerte meteo: Open-Meteo non ne offre in modo completo. La capability `ALERTS` va dichiarata assente e la UI si adatta.
- Extra opzionale: Open-Meteo espone anche il modello grezzo WeatherNext 2 (`/v1/ensemble?models=google_weathernext2_ensemble`, 0,25°, dati ogni 6 ore). Non è il prodotto rifinito della Weather API: al massimo un'opzione avanzata, non il default.

---

## 5. Onboarding (setup iniziale)

Massimo 3–4 schermate, tutte saltabili tranne la scelta della fonte:

1. **Benvenuto** con breve spiegazione.
2. **Scegli la fonte dati**, con due card chiare:
   - **Google** (consigliato, più preciso, richiede una tua API key gratuita) → apre una **guida passo-passo integrata**: crea un progetto su Google Cloud, attiva la fatturazione, abilita "Weather API", crea la key, **restringila** (solo Weather API + package/firma Android), imposta un limite di quota giornaliero. Poi un campo per incollare la key con un pulsante **"Verifica"** che fa una chiamata di test e mostra un esito chiaro.
   - **Open-Meteo** (gratis, nessuna configurazione) → si procede subito.
3. **Posizione:** permesso di localizzazione (con spiegazione) oppure ricerca manuale della città.
4. **Suggerimento widget:** mostra un'anteprima e usa `requestPinAppWidget` per aggiungerlo alla home con un tocco.

La fonte si può cambiare in qualunque momento dalle impostazioni.

---

## 6. App — schermata principale

Contenuti (riferimento: screenshot Google Meteo fornito da Antonio):

- Località e temperatura attuale grande, condizione, percepita, max/min
- **Banner allerte** ben visibile, che si apre su un dettaglio con fonte e validità
- Previsione oraria a scorrimento orizzontale (temp, icona, % pioggia)
- Previsione a 10 giorni (icona, % pioggia, barra min/max stile range)
- Schede dettagli: vento (bussola), umidità e punto di rugiada, UV, pressione, visibilità, alba/tramonto (arco solare), fase lunare
- Dettagli orari a tab: precipitazioni / vento / umidità, con grafico
- Pull-to-refresh, orario dell'ultimo aggiornamento sempre visibile e discreto
- Più località salvate (swipe o lista), località attuale opzionale

**Sfondo/atmosfera dinamica** in base a condizione e ora (gradiente o animazione leggera, mai pesante). Supporto completo a tema chiaro/scuro.

### Layout adattivo (Fold 8)

- Usare `WindowSizeClass` / Material 3 Adaptive. **Schermo esterno** a una colonna. **Schermo interno aperto** a due colonne (es. attuale + orario a sinistra, 10 giorni + dettagli a destra) oppure list-detail con le località.
- Gestire la continuità tra i due schermi senza perdere lo stato.

---

## 7. Widget — il cuore del progetto

Tutti con Glance, **responsivi** (`SizeMode.Responsive` con più breakpoint, così ogni widget mostra più informazioni man mano che si ingrandisce), dynamic color, angoli arrotondati di sistema, leggibili su qualsiasi wallpaper.

Set iniziale (ogni widget ha un'anteprima reale nel selettore: `previewLayout` / generated previews):

| Widget | Dimensioni min → max | Contenuto |
|---|---|---|
| **Compatto** | 2×1 → 2×2 | temp + icona + condizione, max/min se c'è spazio |
| **Orario** | 4×1 → 4×2 | attuale + prossime 5–8 ore |
| **Giornaliero** | 4×2 → 4×3 | attuale + prossimi 5–7 giorni con barre min/max |
| **Completo** | 4×3 → 5×5 | attuale, allerta, orario, giornaliero |
| **Allerta/Pioggia** | 2×2 → 4×2 | badge allerta attiva oppure probabilità di pioggia nelle prossime ore |

Requisiti comuni:

- **Configurazione per istanza** (activity di configurazione Glance): località (attuale o salvata), stile (dinamico/trasparente/scuro), opacità dello sfondo. Riconfigurabile con pressione prolungata.
- Il tocco apre l'app sulla località del widget; area secondaria opzionale per un refresh manuale.
- I widget leggono **solo dalla cache**. L'aggiornamento lo fa il worker condiviso, mai una chiamata di rete per ogni widget.
- Stati gestiti con eleganza: primo caricamento, offline (dati vecchi con indicazione discreta "aggiornato alle 09:10"), key mancante o non valida (messaggio + tocco porta alle impostazioni).
- Attribuzione della fonte dati discreta ma presente dove richiesta.
- Testare su launcher Samsung One UI (schermo esterno e interno del Fold, griglie diverse) e su Pixel Launcher.

---

## 8. Aggiornamenti e impostazioni

- **Intervallo di aggiornamento configurabile** dall'utente (minimo 15 min per il limite di WorkManager; opzioni es. 15/30/60/120/180 min). Default: **30 min** per Google, 30 min per Open-Meteo.
- Con Google, nelle impostazioni mostrare una **stima delle chiamate mensili** (località × chiamate per refresh × refresh/mese) confrontata con la fascia gratuita da 10.000, con un avviso se si supera. Ottimizzazioni:
  - allerte e 10 giorni si possono aggiornare meno spesso delle condizioni attuali (es. ogni 2–3 ore)
  - aggiornare solo le località usate da widget o aperte di recente
  - backoff esponenziale sugli errori
- Altre impostazioni (poche e chiare): fonte dati e key, unità (°C/°F, km/h/m/s/nodi, mbar/hPa/inHg, mm/in), tema (sistema/chiaro/scuro), formato orario, lingua dati, notifiche allerte (opzionale, fase 2).
- Tutte le impostazioni passano da un unico `SettingsRepository` tipizzato, così aggiungerne una nuova richiede una riga di modello e una voce di UI.

---

## 9. Qualità, privacy, open source

- Niente analytics o tracking. La posizione resta sul dispositivo e viene inviata solo al provider scelto.
- Key mai loggata, cifrata a riposo, campo mascherato.
- Test: unit test sui mapper dei provider (fixture JSON reali salvate in `test/resources`), test del repository e della cache, screenshot test dei widget e delle schermate principali (es. Roborazzi/Paparazzi) in più dimensioni e temi.
- CI su GitHub Actions: lint, test, build release firmata, artefatti nelle Releases.
- Build riproducibile, nessuna dipendenza proprietaria obbligatoria. Valutare un flavor `fdroid` senza Play Services (posizione via `LocationManager`).
- README chiaro con screenshot, guida alla key Google, sezione privacy e contributing.
- Localizzazione: italiano e inglese dall'inizio, stringhe pronte per Weblate/Crowdin.

---

## 10. Roadmap suggerita

1. **Fondamenta:** moduli, modello dominio, `WeatherProvider`, provider Open-Meteo (più facile da testare senza key), cache Room, worker.
2. **Provider Google** con paginazione oraria, allerte, gestione errori e quota, verifica della key.
3. **App:** onboarding, schermata principale, località, impostazioni, layout adattivo Fold.
4. **Widget:** prima Compatto e Orario fatti benissimo, poi Giornaliero, Completo e Allerta.
5. **Rifinitura:** animazioni, sfondi dinamici, stati vuoti/errore, accessibilità (TalkBack, contrasto, font scaling).
6. **Release:** CI, README, prima release su GitHub, richiesta di inclusione su F-Droid.

## 11. Definizione di "fatto" per la v1

- [ ] Funziona end-to-end con **entrambi** i provider, con cambio fonte a caldo
- [ ] Onboarding completabile in meno di 1 minuto con Open-Meteo e in pochi minuti con Google seguendo la guida integrata
- [ ] Almeno 4 widget responsivi, configurabili per istanza, belli su schermo esterno e interno del Fold 8
- [ ] Intervallo di aggiornamento configurabile e rispettato; stima delle chiamate visibile con Google
- [ ] Offline: app e widget mostrano l'ultimo dato con il suo orario
- [ ] Attribuzioni corrette per Google e Open-Meteo
- [ ] Nessuna key o segreto nel repo; CI verde; README completo

---

### Domande aperte da chiarire con Antonio

- Nome del progetto e licenza definitiva (GPL-3.0 vs MIT)
- Notifiche per le allerte meteo già nella v1 o dopo?
- Icone meteo: set proprio (consigliato, per coerenza tra provider) o icone fornite da Google?

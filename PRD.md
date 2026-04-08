# PRD — Tinder for Dogs 🐾

> **Version:** 1.0 · **Status:** Draft · **Date:** 2026-04-08

---

## Problem Statement

I proprietari di cani in contesto urbano faticano a trovare compagni di gioco adatti ai loro animali: gli incontri avvengono per pura casualità al parco, senza alcuna garanzia di compatibilità tra i cani. Non esiste oggi uno strumento che combini geolocalizzazione, compatibilità tra razze e pianificazione degli incontri in un'unica esperienza. Tinder for Dogs risolve questo problema permettendo ai proprietari di scoprire, matchare e incontrarsi con altri cani compatibili nelle vicinanze.

---

## Target Users & Personas

### Persona 1 — Marco, il proprietario urbano attivo
- **Profilo:** 28–40 anni, vive in città, lavora full-time, porta il cane al parco nei weekend e la sera. Tecnologicamente abituato alle app mobile, usa già app di dating e social.
- **Pain points:** Al parco incontra sempre gli stessi cani o cani incompatibili per taglia/carattere. Non sa in anticipo chi troverà.
- **Goals:** Trovare compagni di gioco stabili e compatibili per il suo cane, pianificare uscite in anticipo, evitare incontri problematici.

### Persona 2 — Laura, la proprietaria attenta al benessere del cane
- **Profilo:** 35–55 anni, ha un cane di taglia piccola o media, molto attenta alla socializzazione corretta dell'animale. Usa lo smartphone quotidianamente.
- **Pain points:** Teme incontri con cani troppo grandi o aggressivi. Vorrebbe sapere in anticipo con chi si incontrerà.
- **Goals:** Garantire al suo cane incontri sicuri e piacevoli, costruire una rete sociale canina affidabile.

---

## Value Proposition

Tinder for Dogs è la prima app che mette il cane al centro: ogni match è basato su compatibilità oggettiva tra razze (taglia, temperamento, energia), non solo sulla vicinanza geografica. A differenza dei gruppi Facebook o del caso del parco, l'app combina algoritmo di compatibilità, geolocalizzazione approssimativa e chat tra proprietari per trasformare ogni uscita in un incontro di qualità.

---

## Feature List

### F-01  Profilo del Cane  · Priority: P0
**Descrizione:** Ogni utente crea uno o più profili per i propri cani, con informazioni su razza, taglia, età, carattere e foto (opzionali).
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to create a profile for my dog with breed, size, age and temperament, so that other owners can evaluate compatibility before matching.
**Success metrics:** % di profili completati, tasso di completamento del campo razza.
**Constraints / notes:** Le foto sono opzionali. La razza è obbligatoria perché è il dato chiave per l'algoritmo di compatibilità. Un proprietario può gestire più cani.

---

### F-02  Algoritmo di Compatibilità tra Razze  · Priority: P0
**Descrizione:** Il sistema filtra e ordina i cani mostrati in base a regole di compatibilità tra razze (taglia, livello di energia, temperamento). Un Alano non verrà mostrato a un Pinscher, ad esempio.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to see only dogs that are compatible with mine by breed and size, so that I avoid unsafe or unpleasant encounters.
**Success metrics:** Tasso di match reciproci, feedback positivi post-incontro, tasso di segnalazione di incontri problematici.
**Constraints / notes:** Le regole di compatibilità si basano su dati cinofili consolidati (non ML in v1). Definire e mantenere la matrice di compatibilità tra razze è un requisito editoriale/tecnico critico.

---

### F-03  Geolocalizzazione Approssimativa  · Priority: P0
**Descrizione:** L'app mostra la distanza approssimativa tra i cani (es. "a 500m") senza rivelare la posizione esatta del proprietario, per tutelare la privacy.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to see how far other dogs are without exposing my exact location, so that I can find nearby companions while keeping my privacy.
**Success metrics:** % di utenti che abilitano la geolocalizzazione, distanza media dei match.
**Constraints / notes:** Posizione esatta mai esposta. Raggio configurabile dall'utente. Conformità GDPR obbligatoria.

---

### F-04  Swipe & Match  · Priority: P0
**Descrizione:** I proprietari scorrono i profili dei cani nelle vicinanze e compatibili. Il match avviene solo quando entrambi i proprietari esprimono interesse ("Woof"). Solo dopo il match si attiva la chat.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to swipe on nearby compatible dogs and match only when interest is mutual, so that I only interact with owners who are genuinely interested.
**Success metrics:** Numero di match generati per utente attivo, tasso di conversione swipe → match.
**Constraints / notes:** In v1 freemium: numero di swipe giornalieri limitati per utenti free. Gli utenti premium hanno swipe illimitati.

---

### F-05  Chat tra Proprietari  · Priority: P0
**Descrizione:** Dopo un match reciproco, i due proprietari possono comunicare via chat in-app per organizzare l'incontro.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to chat with matched owners directly in the app, so that I can organize a playdate without sharing personal contact details.
**Success metrics:** % di match che generano almeno un messaggio, numero medio di messaggi per conversazione.
**Constraints / notes:** La chat è disponibile solo post-match. Nessun contatto diretto prima del match.

---

### F-06  Disponibilità in Tempo Reale  · Priority: P0
**Descrizione:** I proprietari possono indicare quando sono disponibili per un incontro — sia in tempo reale ("sono al parco adesso") che pianificato ("disponibile sabato mattina").
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to signal my real-time or planned availability, so that I can find other dogs to meet spontaneously or in advance.
**Success metrics:** % di utenti che usano la funzione disponibilità, correlazione tra disponibilità condivisa e incontri confermati.
**Constraints / notes:** La disponibilità in tempo reale richiede consenso attivo dell'utente (non sempre attiva).

---

### F-07  Conferma Incontro Reale  · Priority: P1
**Descrizione:** Dopo aver organizzato un incontro via chat, i proprietari possono confermare che l'incontro è avvenuto. Questo dato alimenta il KPI principale del prodotto.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to confirm that a playdate actually happened, so that the app can track real-world success and improve my dog's profile.
**Success metrics:** Numero di incontri reali confermati per mese, tasso di conversione match → incontro confermato.
**Constraints / notes:** La conferma è volontaria. Potrebbe incentivare con badge o punti in versioni future.

---

### F-08  Social Login  · Priority: P0
**Descrizione:** Gli utenti si registrano e accedono tramite account social (Google, Facebook) senza necessità di creare credenziali separate.
**Personas served:** Marco, Laura
**User story:** As a new user, I want to sign up with my existing social account, so that I can start using the app quickly without managing another password.
**Success metrics:** Tasso di completamento onboarding, % di login social vs altri metodi.
**Constraints / notes:** Obbligatorio supportare almeno Google e Facebook in v1.

---

### F-09  Modello Freemium  · Priority: P1
**Descrizione:** L'app è gratuita con limiti di utilizzo (swipe giornalieri limitati, funzionalità base). Gli utenti premium accedono a swipe illimitati, visibilità aumentata e funzionalità avanzate.
**Personas served:** Marco, Laura
**User story:** As a free user, I want to use the core matching features at no cost, so that I can try the app before deciding to subscribe.
**Success metrics:** Tasso di conversione free → premium, ARPU (Average Revenue Per User).
**Constraints / notes:** Definire in v1 il limite esatto di swipe giornalieri per utenti free. I contenuti premium da definire nel dettaglio prima del lancio.

---

### F-10  App Mobile + Web  · Priority: P1
**Descrizione:** L'app è disponibile sia su mobile (iOS e Android) che su browser web, con esperienza coerente su entrambe le piattaforme.
**Personas served:** Marco, Laura
**User story:** As a dog owner, I want to access the app from my phone or my computer, so that I can manage my dog's profile and matches from any device.
**Success metrics:** % di sessioni mobile vs web, tasso di retention per piattaforma.
**Constraints / notes:** Mobile è il canale primario. Web può essere una versione semplificata in v1.

---

## Non-Functional Requirements

| ID | Category | Requirement | Acceptance Criterion | Priority | Source |
|---|---|---|---|---|---|
| NFR-01 | Privacy | La posizione esatta dell'utente non deve mai essere esposta ad altri utenti | Nessuna risposta API o UI rivela coordinate precise; solo distanza approssimativa in km/m | P0 | explicit |
| NFR-02 | Security | I dati personali e di localizzazione devono essere protetti in transito e a riposo | Tutti i dati trasmessi via HTTPS/TLS 1.2+; dati sensibili cifrati a riposo con AES-256 | P0 | implicit |
| NFR-03 | Compliance | L'app deve essere conforme al GDPR per gli utenti europei | Privacy policy approvata da consulente legale; consenso esplicito per geolocalizzazione; diritto alla cancellazione implementato entro 30 giorni | P0 | implicit |
| NFR-04 | Performance | Le schermate principali (feed swipe, chat) devono caricarsi rapidamente | Time-to-interactive ≤ 2s su connessione 4G per il 90° percentile degli utenti | P1 | implicit |
| NFR-05 | Scalability | Il sistema deve reggere la crescita degli utenti senza interventi manuali | Architettura in grado di scalare a 100.000 utenti attivi mensili senza refactoring | P1 | implicit |
| NFR-06 | Availability | L'app deve essere disponibile in modo affidabile | Uptime ≥ 99.5% misurato mensilmente; manutenzione programmata fuori orario di punta | P1 | implicit |
| NFR-07 | Usability | L'onboarding deve essere completabile rapidamente da un nuovo utente | ≥ 80% degli utenti completa la creazione del profilo cane entro 5 minuti dalla registrazione | P1 | implicit |
| NFR-08 | Observability | Il team deve poter monitorare KPI chiave in tempo reale | Dashboard operativa con match/giorno, incontri confermati/settimana, errori critici; alert automatici su anomalie | P1 | implicit |
| NFR-09 | Maintainability | La matrice di compatibilità tra razze deve essere aggiornabile senza deploy | Matrice gestita via CMS o configurazione esterna, modificabile da un operatore non-tecnico | P2 | implicit |
| NFR-10 | Portability | L'app mobile deve supportare le versioni OS più diffuse | iOS ≥ 16 e Android ≥ 10 coperti; test su almeno 5 dispositivi fisici per piattaforma | P2 | implicit |

### Unaddressed Categories
Le seguenti categorie NFR non hanno copertura esplicita nella spec attuale:
- **Reliability** — Nessuna strategia definita per backup dei dati, disaster recovery o gestione dei messaggi in-app in caso di downtime.
- **Compliance (App Store)** — Le policy di Apple App Store e Google Play per app con geolocalizzazione e contenuti UGC potrebbero richiedere requisiti aggiuntivi.

---

## Out of Scope — v1
- Matching per accoppiamento (breeding) — previsto per v2
- Algoritmo di compatibilità basato su ML / feedback degli incontri reali
- Badge di vaccinazione o certificazioni veterinarie
- Funzionalità social (gruppi, eventi, community)
- Notifiche push avanzate e geofencing
- Versione per rifugi o allevatori professionali
- Marketplace (prodotti, servizi per cani)
- Selezione del mercato geografico basata su statistiche di densità canina — da ricercare prima del lancio

---

## Open Questions
- **Q1 [Business]:** Qual è il limite di swipe giornalieri per utenti free? Questo numero è critico per bilanciare conversione premium e retention free.
- **Q2 [Algorithm]:** Chi mantiene e aggiorna la matrice di compatibilità tra razze? Serve un consulente cinofilo o ci si basa su fonti pubbliche?
- **Q3 [Market]:** In quale città si lancia v1? La scelta dovrebbe basarsi su dati di densità canina urbana (es. Milano, Roma, Zurigo).
- **Q4 [Premium]:** Oltre agli swipe illimitati, quali funzionalità premium si includono in v1? (Es. boost visibilità, super-woof, filtri avanzati)
- **Q5 [Legal]:** È necessario un consulente legale per la privacy policy e i termini di servizio prima del lancio, soprattutto per la gestione della geolocalizzazione in ambito GDPR?
- **Q6 [Web]:** La versione web in v1 è paritetica al mobile o una versione ridotta? Questo impatta significativamente il budget di sviluppo.

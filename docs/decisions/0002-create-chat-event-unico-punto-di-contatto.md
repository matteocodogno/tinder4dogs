---
status: accepted
date: 2026-09-28
decision-makers: Matteo Pelucco
---

# `CreateChatEvent` in-process come unico punto di contatto con la chat

## Contesto e problema

La chat post-match (F-09, spec `post-match-chat`) vive nel proprio package vertical-slice
([ADR-0001](0001-chat-come-nuovo-package-vertical-slice.md)). Una chat deve nascere
quando due cani fanno match reciproco, ma il flusso di swipe e match (F-07/F-08)
non esiste ancora e sarà scritto da un'altra spec. Oggi non c'è neppure un
concetto di match salvato: la compatibilità viene calcolata a ogni richiesta
(`tech.md`).

La steering chiede dipendenze tra package in una sola direzione, acicliche e
visibili. Come fa la chat a sapere che deve creare una conversazione senza
accoppiarsi agli interni di un flusso che non esiste ancora, e senza creare un
ciclo tra `match` e `chat`?

## Fattori decisionali

* Un solo punto di contatto tra funzionalità, esplicito e con un contratto
  scritto, così che F-07/F-08 possano essere sviluppate in modo indipendente.
* Grafo delle dipendenze aciclico: `match → chat → dog`.
* Proporzionalità: un singolo servizio Spring Boot, MVC sincrono, un solo
  database e **nessuna nuova dipendenza**.
* Un match già confermato non deve fallire perché la creazione della chat non è andata a buon fine.
* Creazione idempotente: un evento duplicato non deve generare una seconda chat.

## Opzioni considerate

* Evento applicativo Spring in-process `CreateChatEvent`, di proprietà di `chat`, pubblicato dal flusso di match tramite `ApplicationEventPublisher`
* Chiamata diretta dal flusso di match a un metodo di `ChatService`
* Message broker esterno (per esempio Kafka o RabbitMQ)
* La chat legge lo stato dei match da un altro package (polling o query)

## Esito della decisione

Opzione scelta: "`CreateChatEvent` in-process", perché dà a F-07/F-08 un
contratto unico e documentato (`data class CreateChatEvent(val dogAId: Long, val dogBId: Long)`),
non introduce infrastruttura né dipendenze e mantiene il grafo aciclico.
È l'**unico** punto di contatto tra le funzionalità. L'altro evento della
feature, `MessageAcceptedEvent`, resta interno a `chat`.

Il contratto, come definito nel design:

* **Proprietà**: la classe sta in `chat/CreateChatEvent.kt` e il package `chat`
  ne possiede payload, semantica e regole di idempotenza. Il flusso di match
  dipenderà da `chat` solo attraverso questa classe (`match → chat`, mai il contrario).
* **Consegna**: `ApplicationEventPublisher`, sincrona e in-process. Il publisher
  la emette solo dopo che il match è stato salvato in modo durevole, con due id di
  cane validi e distinti. Gli id non sono ordinati.
* **Idempotenza**: è garantita da chi consuma l'evento. La coppia viene
  normalizzata in `(min, max)` e cercata prima di inserirla; il vincolo
  `uq_chat_pair` (con `ck_chat_pair_order`) fa da ultima difesa contro le
  richieste duplicate concorrenti, dopo le quali il consumatore rilegge la chat esistente.
* **Rifiuto**: un evento non valido (cane sconosciuto, stesso cane due volte)
  viene registrato con un log WARN e ignorato. Non viene creata nessuna chat e
  non viene lanciata nessuna eccezione verso il flusso che ha emesso l'evento.

### Conseguenze

* Positivo, perché F-07/F-08 conoscono solo una data class con due `Long`, non
  il service, i repository o le tabelle della chat.
* Positivo, perché lo stesso evento ripetuto porta sempre alla stessa chat, sia
  nel codice sia nel database.
* Positivo, perché non servono nuove dipendenze Maven né infrastruttura, e il
  listener si testa a livello unitario chiamando `ChatService.onMatched` con un
  evento costruito a mano.
* Negativo, perché l'evento in-process non è durevole: se il processo si ferma
  tra il commit del match e la creazione della chat, o se la creazione viene
  rifiutata, la chat non esiste e non c'è nessun retry. L'unica traccia è un log WARN.
* Negativo, perché ignorare un evento non valido è una **deviazione voluta** dalla
  convenzione del `require` all'inizio del metodo (`AGENTS.md`). È limitata a
  questo listener, ma nasconde gli errori del publisher, che si vedono solo nei log.
* Negativo, perché la consegna sincrona fa girare la creazione della chat sul
  thread del publisher e ne allunga il tempo di risposta.
* Neutro, perché il nome imperativo ("Create…") è coerente con il fatto che il
  contratto appartiene a `chat`: il publisher chiede che una chat esista, non annuncia un fatto proprio.

### Verifica

* In code review: solo `CreateChatEvent` attraversa il confine di `chat/` verso
  altre funzionalità. Nessun import da `chat/` dentro `dog/`. Nessun import da
  `match/` dentro `chat/`.
* Test unitari in `ChatServiceTest`:
  * `creating a chat for a matched pair stores the pair in canonical order`
  * `a duplicate create chat event returns the existing chat instead of saving a second one`
  * `a create chat event for an unknown dog is rejected without saving`
  * `a create chat event naming the same dog twice is rejected without saving`

## Pro e contro delle opzioni

### `CreateChatEvent` in-process

* Positivo, perché disaccoppia publisher e chat con un solo tipo condiviso e documentato.
* Positivo, perché non richiede infrastruttura né dipendenze.
* Negativo, perché non è durevole e non ha retry.

### Chiamata diretta a `ChatService`

* Positivo, perché è la soluzione più semplice da leggere.
* Negativo, perché il flusso di match dipenderebbe dall'API del service della
  chat, compresa la firma del metodo e le sue eccezioni. Ogni refactoring del
  service diventerebbe una modifica tra funzionalità.

### Message broker esterno

* Positivo, perché dà durabilità, retry e disaccoppiamento tra processi.
* Negativo, perché è sproporzionato per un servizio singolo: aggiunge
  infrastruttura, dipendenze e test di integrazione che il progetto oggi non ha.

### La chat legge lo stato dei match

* Negativo, perché oggi non esiste nessun match salvato da leggere.
* Negativo, perché invertirebbe la direzione della dipendenza (`chat → match`)
  e legherebbe la chat agli interni di F-07/F-08.

## Ulteriori informazioni

* Fonte: `.kiro/specs/post-match-chat/design.md`, sezioni *Architecture Pattern &
  Boundary Map*, *Boundary Commitments* e *chat / contract*.
* Condizioni per rivedere la decisione (dai *Revalidation Triggers* del design):
  * F-07/F-08 definiscono un nome, un payload o una semantica di emissione diversi da `CreateChatEvent`;
  * la chat esce da questo processo: gli eventi Spring in-process smettono di
    funzionare e `CreateChatEvent` deve diventare un contratto tra servizi;
  * la perdita di chat diventa inaccettabile: il passo naturale è un outbox
    transazionale nello stesso database, prima di pensare a un broker.
* ADR correlato: [ADR-0001](0001-chat-come-nuovo-package-vertical-slice.md).

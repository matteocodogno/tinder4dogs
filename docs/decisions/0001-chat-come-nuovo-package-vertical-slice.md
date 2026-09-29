---
status: accepted
date: 2026-09-28
decision-makers: Matteo Pelucco
---

# Chat come nuovo package vertical-slice, come `dog` e `match`

## Contesto e problema

La chat tra i proprietari di due cani è una funzionalità nuova e va collocata nel
codice. Il servizio è organizzato per concetto: ogni package (`dog/`, `match/`)
contiene il proprio modello, la persistenza e la superficie HTTP, senza cartelle
per livello come `controller/`, `service/` o `repository/`. Le dipendenze tra
package vanno in una sola direzione (oggi `match` legge da `dog`, e `dog` non sa
nulla di `match`) e devono restare acicliche.

Dove deve stare il codice della chat, e con quale struttura?

## Fattori decisionali

* Coerenza con l'organizzazione "un package per concetto" di `.kiro/steering/structure.md`.
* Il grafo delle dipendenze tra package deve restare aciclico e leggibile.
* Una funzionalità si deve poter leggere dall'inizio alla fine in una sola directory.
* Nessun accoppiamento nascosto: una modifica che tocca due package deve essere visibile.

## Opzioni considerate

* Nuovo package vertical-slice `chat/`, con entità, repository, service, controller e DTO propri
* Chat dentro il package `match/`, visto che una chat nasce da un match
* Package trasversali per livello (`controller/`, `service/`, …) condivisi dalle funzionalità

## Esito della decisione

Opzione scelta: "nuovo package vertical-slice `chat/`", perché è il pattern già
usato da `dog` e `match`, tiene la chat leggibile in una sola directory e fa sì
che i punti di contatto con le altre funzionalità siano espliciti invece che
nascosti. L'unico punto di contatto tra funzionalità è l'evento in-process
`CreateChatEvent`, descritto in [ADR-0002](0002-create-chat-event-unico-punto-di-contatto.md).

### Conseguenze

* Positivo, perché chi conosce `dog` e `match` trova la chat dove se l'aspetta e
  organizzata allo stesso modo (naming, `Request`/`Response`, mapping `of()`).
* Positivo, perché la chat possiede il proprio schema: tabelle e changeset Liquibase
  sono suoi e non modificano quelli di `dog` o `match`.
* Positivo, perché la chat si può testare a livello unitario, isolata dagli altri package.
* Neutro, perché la chat dipende da `dog` in sola lettura (`Dog` e `DogRepository`):
  ne verifica l'esistenza e mostra il nome dell'altro cane. `dog` non sa nulla
  della chat, e la catena resta aciclica (`match → chat → dog`).
* Negativo, perché le tabelle `chat` e `message` sono il primo stato salvato che
  deriva da un match, e vivono in `chat` anche se il concetto di match
  appartiene a F-07/F-08.
* Negativo, perché il pattern si basa solo su convenzioni: nel build non c'è niente
  che impedisca a un'altra classe di importare dall'interno di `chat/`.

### Verifica

In code review: `chat/` contiene tutto quello che riguarda la chat. Nessun codice
della chat vive in `match/` o `dog/`, e nessuna nuova cartella per livello compare
sotto `com.ai4dev.tinder4dogs`. Ogni commit che tocca `chat/` insieme a un altro
package lo dichiara nel messaggio, come chiede `AGENTS.md`.

## Pro e contro delle opzioni

### Nuovo package vertical-slice `chat/`

* Positivo, perché è coerente con il resto del codice e con la steering.
* Positivo, perché le dipendenze verso gli altri package sono poche ed esplicite.
* Neutro, perché serve un meccanismo per sapere quando creare una chat (vedi ADR-0002).

### Chat dentro `match/`

* Positivo, perché eviterebbe un punto di contatto tra package: il match potrebbe
  creare la chat direttamente.
* Negativo, perché `match` oggi calcola i punteggi su richiesta e non salva niente;
  aggiungere messaggi persistiti gli darebbe un secondo compito, senza legami con il primo.
* Negativo, perché mescola due concetti in un package e contraddice "un package per concetto".

### Package trasversali per livello

* Negativo, perché contraddice esplicitamente `structure.md` e renderebbe la chat
  l'unica funzionalità organizzata in modo diverso.

## Ulteriori informazioni

* Fonte: `.kiro/specs/post-match-chat/design.md`, sezioni *Architecture Pattern &
  Boundary Map* e *File Structure Plan*. Il package contiene `CreateChatEvent`,
  `Chat`, `Message`, `ChatRepository`, `MessageRepository`, `ChatService`,
  `ChatDeliveryService` e `ChatController`, più il changeset `005-create-chat.sql`.
  Fuori dal package cambia solo l'indice Liquibase.
* ADR correlato: [ADR-0002](0002-create-chat-event-unico-punto-di-contatto.md).

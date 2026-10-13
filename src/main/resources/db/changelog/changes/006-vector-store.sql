--liquibase formatted sql

--changeset tinder4dogs:006-vector-store
--comment: the table the search index lives in, and the three extensions it needs
--
-- The index stopped being a runtime detail here.
--
-- Until this changeset the knowledge index lived in the application's heap: a
-- `SimpleVectorStore`, rebuilt on demand, gone at every restart, invisible to
-- anything but the process that built it. That was the right thing for one lab
-- and it is not a thing a product can ship: a user's first question would pay
-- for embedding the whole corpus, and a second instance would pay again.
--
-- Moving it here makes it the database's problem, which is where the rest of
-- this product's state already lives. It also makes it OUR problem in three
-- new ways, and every one of them is a line in this file:
--
--   1. an EXTENSION. `vector` is not in a stock PostgreSQL. It is in the image
--      this repository's compose file now starts, and on a managed database it
--      is whatever the provider allows. CREATE EXTENSION on `vector` needs
--      superuser (`hstore` and `uuid-ossp` are trusted and need only the
--      database owner), so on the way to production this line is a conversation
--      with whoever owns that database and not a deployment step.
--   2. a DIMENSION IN THE SCHEMA. `vector(1536)` is the embedding model's
--      output size, written into a column type. Changing the embedding model is
--      therefore a migration plus a re-embedding of the entire corpus, and the
--      reference says plainly that changing dimensions means re-creating the
--      table. There is no configuration flag that makes it cheap.
--      A limit that follows from the same line: a `vector` column holds up to
--      16,000 dimensions but an HNSW or IVFFlat index supports at most 2,000,
--      so an embedding model like text-embedding-3-large at 3,072 dimensions
--      cannot be indexed at all. The model choice is constrained by the storage.
--   3. an OWNER. Spring AI can create this table itself, and in this product it
--      must not: `spring.ai.vectorstore.pgvector.initialize-schema` is false in
--      application.yaml, Liquibase owns DDL, and a library creating tables
--      behind its back is how two sources of truth start. False is also the
--      framework's own default in 2.0; it is written out there anyway.
--
-- The shape below is Spring AI's own. `PgVectorStore` reads the columns `id`,
-- `content`, `metadata` and `embedding` by those names and writes with
-- `INSERT ... ON CONFLICT (id) DO UPDATE`, so the names are an interface rather
-- than a preference. `metadata` is `json` and the filter converter casts it to
-- `jsonb` per query, which is also the framework's choice and not ours.
--
-- NO INDEX HERE, on purpose. An index on the embedding column is approximate
-- nearest neighbour search: it trades recall for speed, and this product has a
-- measured recall number to compare against before it trades any of it away.
-- That trade is changeset 007, after the measurement.
--
-- IF NOT EXISTS everywhere, for the same reason as changeset 005: the code
-- resets at every module tag and the compose volume does not.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);
-- The rollback drops the table and NOT the extensions: something else in this
-- database may be using them, and dropping `vector` would take every vector
-- column in the database with it.
--rollback DROP TABLE IF EXISTS vector_store;

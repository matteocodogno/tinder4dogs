--liquibase formatted sql

--changeset tinder-for-dogs:003-seed-dogs-lugano-milano dbms:postgresql
INSERT INTO dog_profiles (name, breed, size, age, gender, bio, city, created_at, updated_at)
VALUES
    -- Lugano dogs
    ('Luna',    'Labrador Retriever', 'LARGE',       3, 'FEMALE', 'Adoro correre lungo il Lago di Lugano e fare nuoto. Cerco compagni di avventura!', 'Lugano', now(), now()),
    ('Biscotto','Golden Retriever',   'LARGE',       5, 'MALE',   'Appassionato di escursioni sul Monte San Salvatore. Ottimo con i bambini e con gli altri cani.', 'Lugano', now(), now()),
    ('Fiocco',  'Maltese',            'SMALL',       2, 'MALE',   'Cucciolo vivace del centro di Lugano. Mi piace giocare al Parco Ciani e fare nuove amicizie.', 'Lugano', now(), now()),
    ('Stella',  'Border Collie',      'MEDIUM',      4, 'FEMALE', 'Intelligente e piena di energia. Adoro i giochi di agilità e le lunghe passeggiate in collina.', 'Lugano', now(), now()),
    ('Argo',    'Bernese Mountain Dog','EXTRA_LARGE', 6, 'MALE',   'Grande e affettuoso. Nato per la montagna, adoro il fresco del Ticino.', 'Lugano', now(), now()),
    ('Perla',   'Beagle',             'SMALL',       1, 'FEMALE', 'Cucciola curiosa e sempre di buon umore. Vivo vicino al quartiere Molino Nuovo.', 'Lugano', now(), now()),
    ('Rocco',   'German Shepherd',    'LARGE',       7, 'MALE',   'Lealissimo e protettivo. Cerco un amico per le passeggiate serali sul lungolago.', 'Lugano', now(), now()),

    -- Milano dogs
    ('Briciola','Chihuahua',          'SMALL',       3, 'FEMALE', 'Piccola ma coraggiosa! Vivo in Navigli e adoro i caffè dog-friendly del quartiere.', 'Milano', now(), now()),
    ('Django',  'French Bulldog',     'SMALL',       4, 'MALE',   'Amo passeggiare nei Giardini Indro Montanelli e fare aperitivo in zona Brera.', 'Milano', now(), now()),
    ('Nuvola',  'Samoyed',            'LARGE',       2, 'FEMALE', 'Soffice come una nuvola! Adoro il Parco Sempione e le sessioni di fotografie con i passanti.', 'Milano', now(), now()),
    ('Falco',   'Dobermann',          'LARGE',       5, 'MALE',   'Elegante e atletico come la città che abito. Cerco partner per corse mattutine al Parco Nord.', 'Milano', now(), now()),
    ('Pepita',  'Shiba Inu',          'MEDIUM',      3, 'FEMALE', 'Indipendente e carismatica. Vivo in zona Isola e adoro esplorare i mercatini del weekend.', 'Milano', now(), now()),
    ('Dante',   'Boxer',              'LARGE',       6, 'MALE',   'Esuberante e giocoso. Frequento il dog park di CityLife ogni mattina.', 'Milano', now(), now()),
    ('Gaia',    'Australian Shepherd','MEDIUM',      4, 'FEMALE', 'Energica e sempre pronta all''avventura. Adoro i corsi di agility e il Parco delle Cave.', 'Milano', now(), now());

--rollback DELETE FROM dog_profiles WHERE city IN ('Lugano', 'Milano') AND name IN ('Luna','Biscotto','Fiocco','Stella','Argo','Perla','Rocco','Briciola','Django','Nuvola','Falco','Pepita','Dante','Gaia');

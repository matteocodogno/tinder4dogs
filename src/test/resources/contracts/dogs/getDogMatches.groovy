import org.springframework.cloud.contract.spec.Contract

// GET /api/v1/dogs/{dogId}/matches?limit=3 — successful retrieval (200)
// Schema source: DogMatchController.findMatches() → DogMatchListResponse{ matches: List<DogMatchEntry> }
Contract.make {
    description "GET /api/v1/dogs/{dogId}/matches returns 200 with the top N compatible dogs"

    request {
        method GET()
        urlPath($(
            consumer(regex('/api/v1/dogs/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/matches')),
            producer('/api/v1/dogs/f47ac10b-58cc-4372-a567-0e02b2c3d479/matches')
        )) {
            queryParameters {
                parameter('limit', $(consumer(equalTo('3')), producer('3')))
            }
        }
        headers {
            header('Accept', applicationJson())
        }
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            matches: [
                [
                    id                : $(producer(anyUuid()), consumer('a1b2c3d4-e5f6-7890-abcd-ef1234567890')),
                    name              : $(producer(anyNonEmptyString()), consumer('Max')),
                    breed             : $(producer(anyNonEmptyString()), consumer('Golden Retriever')),
                    size              : $(producer(anyOf('SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE')), consumer('LARGE')),
                    age               : $(producer(anyPositiveInt()), consumer(4)),
                    gender            : $(producer(anyOf('MALE', 'FEMALE')), consumer('FEMALE')),
                    compatibilityScore: $(producer(anyDouble()), consumer(0.70))
                ]
            ]
        )
    }
}

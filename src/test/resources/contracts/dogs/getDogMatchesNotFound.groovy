import org.springframework.cloud.contract.spec.Contract

// GET /api/v1/dogs/{dogId}/matches — dog not found (404)
// NOTE: There is no standalone GET /api/v1/dogs/{id} endpoint.
//       The 404 is produced by DogMatchController when the source dog does not exist.
//       The error body is ErrorResponse{code, message} — not Spring ProblemDetail.
Contract.make {
    description "GET /api/v1/dogs/{dogId}/matches returns 404 when the dog does not exist"

    request {
        method GET()
        urlPath($(
            consumer(regex('/api/v1/dogs/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/matches')),
            producer('/api/v1/dogs/00000000-0000-0000-0000-000000000000/matches')
        )) {
            queryParameters {
                parameter('limit', $(consumer(equalTo('1')), producer('1')))
            }
        }
        headers {
            header('Accept', applicationJson())
        }
    }

    response {
        status NOT_FOUND()
        headers {
            contentType(applicationJson())
        }
        body(
            code   : $(producer(equalTo('DOG_NOT_FOUND')), consumer('DOG_NOT_FOUND')),
            message: $(producer(regex('Dog not found: .*')), consumer('Dog not found: 00000000-0000-0000-0000-000000000000'))
        )
    }
}

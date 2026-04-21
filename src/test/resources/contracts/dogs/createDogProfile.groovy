import org.springframework.cloud.contract.spec.Contract

// POST /api/v1/dogs — successful creation (201)
// Schema source: DogProfileController.create() → CreateDogProfileRequest → DogProfileResponse
Contract.make {
    description "POST /api/v1/dogs creates a dog profile and returns 201 Created"

    request {
        method POST()
        url '/api/v1/dogs'
        headers {
            contentType(applicationJson())
        }
        body(
            name  : $(consumer(regex('[a-zA-Z0-9 ]{1,100}')), producer('Buddy')),
            breed : $(consumer(regex('[a-zA-Z0-9 ]{1,100}')), producer('Labrador')),
            size  : $(consumer(anyOf('SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE')), producer('MEDIUM')),
            age   : $(consumer(anyPositiveInt()), producer(3)),
            gender: $(consumer(anyOf('MALE', 'FEMALE')), producer('MALE'))
        )
    }

    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            id       : $(producer(anyUuid()), consumer('f47ac10b-58cc-4372-a567-0e02b2c3d479')),
            name     : $(producer(anyNonEmptyString()), consumer('Buddy')),
            breed    : $(producer(anyNonEmptyString()), consumer('Labrador')),
            size     : $(producer(anyOf('SMALL', 'MEDIUM', 'LARGE', 'EXTRA_LARGE')), consumer('MEDIUM')),
            age      : $(producer(anyPositiveInt()), consumer(3)),
            gender   : $(producer(anyOf('MALE', 'FEMALE')), consumer('MALE')),
            createdAt: $(producer(anyIso8601DateTime()), consumer('2024-01-01T00:00:00.000Z'))
        )
    }
}

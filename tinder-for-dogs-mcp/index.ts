import {MCPServer, object} from 'mcp-use/server';
import {z} from 'zod';


const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';

// CreateDogProfileRequest (Kotlin) mirrored in Zod.
// Note: size/gender are enum values in backend; keep as non-empty strings
// unless you want to hardcode enum literals.
const createDogProfileRequestSchema = z
    .object({
        name: z.string().trim().min(1).max(100),
        breed: z.string().trim().min(1).max(100),
        size: z.string().trim().min(1),
        age: z.number().int().min(0).max(30),
        gender: z.string().trim().min(1),
        bio: z.string().max(500).optional(),
    })
    .strict();


// Helper: typed fetch wrapper for the Spring Boot REST API
async function apiCall<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown,
): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
        method,
        headers: {'Content-Type': 'application/json'},
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
        const error = await res.text();
        throw new Error(`API ${method} ${path} failed (${res.status}): ${error}`);
    }
    return await res.json() as Promise<T>;
}

const server = new MCPServer({
    name: 'tinder-for-dogs-mcp',
    version: '1.0.0',
    description: 'MCP server for the Tinder for Dogs backend API',
});

// Tool 1: Create a dog profile
server.tool(
    {
        name: 'create_dog_profile',
        description:
            'Create a new dog profile by calling POST /api/v1/dogs. ' +
            'Input must match CreateDogProfileRequest (required fields only, validated by backend). ' +
            'Returns the created DogProfileResponse on success (HTTP 201). ',
        schema: z.object({
            request: createDogProfileRequestSchema.describe('CreateDogProfileRequest payload'),
        }),
    },
    async (request) => {
        try {
            const createdDogProfile = await apiCall<unknown>('POST', '/api/v1/dogs', request.request);

            return object({
                success: true,
                endpoint: 'POST /api/v1/dogs',
                data: createdDogProfile,
            });
        } catch (err) {
            return object({
                success: false,
                endpoint: 'POST /api/v1/dogs',
                error: err instanceof Error ? err.message : 'Unknown error',
            });
        }
    },
);

// Tool 2: Get best match for a dog
server.tool(
    {
        name: 'get_best_match',
        description:
            'Get the best matches for a dog by calling GET /api/v1/dogs/{dogId}/matches. ' +
            'Requires a valid dogId UUID and accepts an optional limit from 1 to 10. ' +
            'If limit is omitted, the backend uses the default value 1. ' +
            'Returns a DogMatchListResponse on success. ' +
            'Returns HTTP 404 with code DOG_NOT_FOUND if the dog does not exist. ' +
            'Returns HTTP 400 with code VALIDATION_ERROR if dogId or limit are invalid.',
        schema: z.object({
            dogId: z.string().uuid().describe('UUID of the dog to find matches for'),
            limit: z
                .coerce.number()
                .int()
                .min(1)
                .max(10)
                .optional()
                .describe('Optional maximum number of matches to return, from 1 to 10. Defaults to 1'),
        }),
    },
    async ({dogId, limit}) => {
        try {
            const query = typeof limit === 'number' ? `?limit=${limit}` : '';
            const matches = await apiCall<unknown>('GET', `/api/v1/dogs/${dogId}/matches${query}`);

            return object({
                success: true,
                endpoint: 'GET /api/v1/dogs/{dogId}/matches',
                data: matches,
            });
        } catch (err) {
            return object({
                success: false,
                endpoint: 'GET /api/v1/dogs/{dogId}/matches',
                error: err instanceof Error ? err.message : 'Unknown error',
            });
        }
    },
);


const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;
console.log(`Server running on port ${PORT}`);
// Start the server
server.listen(PORT);

import { array, MCPServer, object } from 'mcp-use/server';
import { z } from 'zod';

const API_BASE = process.env.API_BASE_URL ?? 'http://localhost:8080';

// Helper: typed fetch wrapper for the Spring Boot REST API
async function apiCall<T>(
    method: 'GET' | 'POST' | 'PUT' | 'DELETE',
    path: string,
    body?: unknown,
): Promise<T> {
    const res = await fetch(`${API_BASE}${path}`, {
        method,
        headers: { 'Content-Type': 'application/json' },
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
        description: `Creates a new dog profile in the Tinder for Dogs platform.
        Use this tool when the user wants to register, add, or create a new dog.
        Returns the created profile including the assigned dog ID.`,
        schema: z.object({
            name: z.string().min(1).max(100).describe('Dog\'s name'),
            breed: z.string().min(1).max(100).describe('Dog\'s breed (e.g. Labrador, Poodle)'),
            size: z.enum([ 'SMALL', 'MEDIUM', 'LARGE', 'EXTRA-LARGE' ]).describe('Dog\'s size category'),
            age: z.number().int().min(0).max(30).describe('Dog\'s age in years'),
            gender: z.enum([ 'MALE', 'FEMALE' ]).describe('Dog\'s gender'),
            bio: z.string().max(500).optional().describe('Dog\'s bio (max characters)'),
        }),
    },
    async ({ name, breed, size, age, gender, bio }) => {
        return object(await apiCall('POST', '/api/v1/dogs', { name, breed, size, age, gender, bio }));
    },
);

// Tool 2: Get best match for a dog
server.tool(
    {
        name: 'get_best_match',
        description: `Returns the best compatibility matches for a given dog.
        Use this when the user asks who their dog should meet, which dog is most
        compatible, or wants match recommendations for a specific dog ID.
        Returns the top matching dog profiles with a compatibility score.`,
        schema: z.object({
            dogId: z.string().describe('ID of the dog to find matches for'),
            limit: z.number().int().min(1).max(10).default(1)
                .describe('Maximum number of matches to return (default: 1)'),
        }),
    },
    async ({ dogId, limit }) => {
        return object(await apiCall('GET', `/api/v1/dogs/${dogId}/matches?limit=${limit}`));
    },
);

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;
console.log(`Server running on port ${PORT}`);
// Start the server
server.listen(PORT);

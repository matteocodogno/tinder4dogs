import { MCPServer, object, text, completable } from "mcp-use/server";
import { z } from "zod";


const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;

// Create MCP server instance
const server = new MCPServer({
  name: "tinder-for-dogs-mcp",
  title: "tinder-for-dogs-mcp", // display name
  version: "1.0.0",
  description: "My first MCP server with all features",
  baseUrl: process.env.MCP_URL || "http://localhost:3000", // Full base URL (e.g., https://myserver.com)
  favicon: "favicon.ico",
  websiteUrl: "https://mcp-use.com", // Can be customized later
  icons: [
    {
      src: "icon.svg",
      mimeType: "image/svg+xml",
      sizes: ["512x512"],
    },
  ],
});

const API_TINDER4DOG_BASE_URL = process.env.API_BASE_URL || "http://localhost:8081";


/**
 * Define UI Widgets
 * All React components in the `resources/` folder
 * are automatically registered as MCP tools and resources.
 *
 * Just export widgetMetadata with description and Zod schema,
 * and mcp-use handles the rest!
 *
 * Docs: https://manufact.com/docs/typescript/server/mcp-apps
 */

/*
 * Define MCP tools
 * Docs: https://mcp-use.com/docs/typescript/server/tools
 */
server.tool(
  {
    name: "fetch-weather",
    description: "Fetch the weather for a city",
    schema: z.object({
      city: z.string().describe("The city to fetch the weather for"),
    }),
  },
  async ({ city }) => {
    return text(`The weather in ${city} is fog`);
  }
);

server.tool(
  {
    name: "match-dog",
    description: "Found match for my dog",
    schema: z.object({
      dogName: z.string().describe("The name of your dog?"),
    }),
  },
  async ({ dogName }) => {
    return text(`Your dog  ${dogName} not found`);
  }
);

  const DogProfileSchema = z.object({
    name: z.string().min(1).max(100),
    breed: z.string().min(1).max(100),
    size: z.enum(["small", "medium", "large", "extra_large"]),
    age: z.number().int().nonnegative(),
    gender: z.enum(["male", "female"]),
    bio: z.string().max(500).nullable().optional()
  });


server.tool(
  {
    name: "t4dog-dog-profile-create",
    description: "Tinder4Dog - Create dog profile",
    schema: DogProfileSchema,
  },
  async (dog) => {
    const response = await fetch(`${API_TINDER4DOG_BASE_URL}/api/v1/dogs`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: dog.name,
        breed: dog.breed,
        size: dog.size.toUpperCase(),
        age: dog.age,
        gender: dog.gender.toUpperCase(),
        bio: dog.bio ?? null,
      }),
    });

    if (!response.ok) {
      const error = await response.text();
      return text(`Error creating dog profile: ${response.status} - ${error}`);
    }

    const created = await response.json();
    return text(`Dog profile created! ID: ${created.id}, Name: ${created.name}`);
  }
);


server.tool(
  {
    name: "t4dog-get-best-match",
    description: "Tinder4Dog - Retrieve best matches for a dog by ID, ordered by compatibility score",
    schema: z.object({
      dogId: z.string().uuid().describe("The ID of the dog profile"),
      limit: z.coerce.number().int().positive().optional().describe("Maximum number of matches to return (default: 10)"),
    }),
  },
  async ({ dogId, limit }) => {
    const params = new URLSearchParams();
    if (limit !== undefined) params.set("limit", String(limit));

    const response = await fetch(
      `${API_TINDER4DOG_BASE_URL}/api/v1/dogs/${dogId}/matches?${params}`,
      { method: "GET" }
    );

    if (!response.ok) {
      const error = await response.text();
      return text(`Error retrieving matches: ${response.status} - ${error}`);
    }

    const matches = await response.json() as Array<{ dog: { id: string; name: string; breed: string; age: number; gender: string }; compatibilityScore: number }>;

    if (matches.length === 0) {
      return text(`No matches found for dog ${dogId}`);
    }

    const list = matches
      .map((m, i) => `${i + 1}. ${m.dog.name} (${m.dog.breed}, ${m.dog.age}y, ${m.dog.gender}) — score: ${(m.compatibilityScore * 100).toFixed(0)}%`)
      .join("\n");

    return text(`Found ${matches.length} match(es) for dog ${dogId}:\n${list}`);
  }
);



/*
 * Define MCP resources
 * Docs: https://mcp-use.com/docs/typescript/server/resources
 */
server.resource(
  {
    name: "config",
    uri: "config://settings",
    description: "Server configuration",
  },
  async () =>
    object({
      theme: "dark",
      language: "en",
    })
);

/*
 * Define MCP prompts
 * Docs: https://mcp-use.com/docs/typescript/server/prompts
 */
server.prompt(
  {
    name: "review-code",
    description: "Review code for best practices and potential issues",
    schema: z.object({
      language: completable(z.string(), [
        "python",
        "javascript",
        "typescript",
        "java",
        "cpp",
        "go",
        "rust",
      ]).describe("The programming language"),
      code: z.string().describe("The code to review"),
    }),
  },
  async ({ language, code }) => {
    return text(`Reviewing ${language} code:\n\n${code}`);
  }
);

console.log(`Server running on port ${PORT}`);
// Start the server
server.listen(PORT);

Generate a @DataJpaTest integration test using Testcontainers (PostgreSQL)
for the following acceptance criterion:

[[PASTE GIVEN/WHEN/THEN BLOCK]]

Rules:
- Use @Testcontainers + @Container with the postgres:15 image
- Seed the database via the repository — never raw SQL
- Assert both the count and the ordering where relevant
- Test method name: shouldReturn[What]_when[Condition]()
- Do not use @SpringBootTest — @DataJpaTest only
# Test Generation Task

Generate comprehensive unit tests for this Kotlin code using these frameworks and patterns [[FRAMEWORK]].

## Code to Test
```kotlin
[[CODE]]
```

## Test Requirements
- Use JUnit 5 (`@Test`, `@BeforeEach`, `@AfterEach`)
- Use MockK for mocking
- Use kotlin.test assertions
- Test all public methods
- Include edge cases and error scenarios
- Use descriptive test names with backticks

## Test Structure

Generate tests following this pattern:
```kotlin
package [same package as source]

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import io.mockk.mockk
import kotlin.test.*

class [ClassName]Test {
    
    private lateinit var [subject]: [ClassName]
    
    @BeforeEach
    fun setup() {
        // Initialize test subject
    }
    
    @Test
    fun `should [behavior] when [condition]`() {
        // Given
        [setup]
        
        // When
        val result = [method call]
        
        // Then
        assertEquals([expected], result)
    }
    
    @Test
    fun `should throw exception when [error condition]`() {
        // Given
        [setup error condition]
        
        // When & Then
        assertFailsWith<[ExceptionType]> {
            [method call]
        }
    }
}
```

## Coverage Requirements
- Happy path (normal operation)
- Edge cases (boundaries, empty inputs)
- Error cases (exceptions, invalid inputs)
- Null handling (if applicable)
- Concurrent access (if applicable)

Generate complete, compilable test code with proper imports, return just the kotlin code without backticks or other formatting.
You have to strip "```kotlin" and "```" from the generated code. 
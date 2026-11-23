package com.quizgate.app
import org.junit.Assert.*
import org.junit.Test

class ResumeMatcherTest {
    @Test fun detectsNamedSkillsCaseInsensitively(){assertEquals(listOf("Spring Boot","Redis","Kafka"),ResumeMatcher.detectSkills("SPRINGBOOT, Redis and Kafka"))}
    @Test fun javaDoesNotMatchJavascript(){assertFalse(ResumeMatcher.detectSkills("JavaScript frontend").contains("Java"))}
    @Test fun springAndRedisMapToExistingBanks(){assertEquals(setOf("Spring Boot","Redis"),ResumeMatcher.topicsFor(setOf("Java","Redis")))}
    @Test fun unsupportedSkillsDoNotInventQuestionCoverage(){assertTrue(ResumeMatcher.topicsFor(setOf("Python","PostgreSQL")).isEmpty())}
    @Test fun distributedSkillsMapToMicroservices(){assertEquals(setOf("Microservices"),ResumeMatcher.topicsFor(setOf("Kafka","gRPC","Resilience4j")))}
    @Test fun projectTitlesExcludeContactsAndUrls(){assertEquals(listOf("Order Service"),ResumeMatcher.reviewedProjects("Order Service\nname@example.com\nhttps://example.com\nOrder Service"))}
    @Test fun candidatesAreOnlySuggestions(){assertEquals(listOf("Order Service"),ResumeMatcher.projectCandidates("Order Service | Spring Boot\n• Built an order system.\nname@example.com"))}
    @Test fun promptCountIsBounded(){assertEquals(10,ResumeMatcher.projectPrompts((1..20).map{"Project $it"}).size)}
    @Test fun templatesDoNotInventPersonalMetrics(){assertTrue(ResumeMatcher.projectPrompts(listOf("Order Service")).all{!it.first.contains("40%")})}
    @Test fun emptyProfileHasNoPrompts(){assertTrue(ResumeMatcher.projectPrompts(emptyList()).isEmpty())}
}

package com.quizgate.app

/** Pure local matching. Document contents are data, never instructions. */
object ResumeMatcher {
    val skills = linkedMapOf(
        "Spring Boot" to Regex("(?i)\\bspring\\s*boot\\b"),
        "Microservices" to Regex("(?i)\\bmicroservices?\\b"),
        "Redis" to Regex("(?i)\\bredis\\b"),
        "Java" to Regex("(?i)\\bjava\\b"),
        "Kafka" to Regex("(?i)\\bkafka\\b"),
        "gRPC" to Regex("(?i)\\bgrpc\\b"),
        "Resilience4j" to Regex("(?i)\\bresilience4j\\b"),
        "PostgreSQL" to Regex("(?i)\\b(postgres(?:ql)?|alloydb)\\b"),
        "Docker" to Regex("(?i)\\bdocker\\b"),
        "Kubernetes" to Regex("(?i)\\bkubernetes\\b"),
        "GCP" to Regex("(?i)\\b(gcp|google cloud)\\b"),
        "Python" to Regex("(?i)\\bpython\\b"),
        "OpenTelemetry" to Regex("(?i)\\bopentelemetry\\b")
    )
    fun detectSkills(text: String): List<String> = skills.filterValues { it.containsMatchIn(text) }.keys.toList()
    fun projectCandidates(text: String): List<String> = text.lineSequence().map { it.substringBefore('|').trim() }
        .filter { it.split(Regex("\\s+")).size<=8 && it.length in 6..75 && !it.contains('@') && !it.contains("http", true) }
        .filter { Regex("(?i)\\b(service|platform|shortener|project|application|system)\\b").containsMatchIn(it) }
        .filterNot { it.startsWith("•") || it.startsWith("-") || it.endsWith('.') || it.count { c -> c.isDigit() } > 3 }
        .distinct().take(5).toList()
    fun topicsFor(selected: Set<String>): Set<String> = buildSet {
        if (selected.any { it == "Spring Boot" || it == "Java" }) add("Spring Boot")
        if (selected.any { it in setOf("Microservices","Kafka","gRPC","Resilience4j","Docker","Kubernetes","GCP","OpenTelemetry") }) add("Microservices")
        if ("Redis" in selected) add("Redis")
    }
    fun reviewedProjects(input: String): List<String> = input.lineSequence().map { it.trim() }
        .filter { it.length in 3..80 && !it.contains('@') && !it.contains("http",true) }
        .distinct().take(5).toList()
    fun projectPrompts(projects: List<String>): List<Pair<String,String>> = projects.flatMap { p -> listOf(
        "For $p, describe one failure that could happen after a partial success. How would you detect it and make a retry safe?" to
            "Explain the actual components, durable state, side effects, idempotency and a test. Do not claim you implemented a design unless you did.",
        "For $p, what would you measure to prove the system is fast and correct under load?" to
            "Discuss a baseline, workload, latency percentiles, errors, saturation and correctness. Use your own evidence rather than invented numbers."
    ) }.take(10)
}

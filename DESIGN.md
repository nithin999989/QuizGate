# Proposed changes, not implemented

## UI

Use clearer typography, larger answer rows and a visible End session control. Separate topic selection from an active quiz. Keep session control voluntary. Show the chosen interval and distinguish practice previews from real timers.

The proposed interview topics are Spring Boot, Microservices and Redis. The source prototype still has its original Java, Python and Data science bank. The additional 30-question JSON is a draft resource with a different schema, not a drop-in replacement for the prototype's asset.

## Resume-based practice

A proposed local-first flow:

1. Let the user choose a PDF or text document through Android's system document picker, without broad storage access.
2. Parse locally with file-size/page limits and clear handling of malformed, scanned and encrypted files.
3. Show detected skills and projects for review/correction. Ignore document instructions, links and scripts. Keep contact details out of prompts.
4. Match reviewed skills to a sourced question bank and local project-prompt templates. This is rule/template-based personalization, not a claim of new AI generation.
5. Make retention optional and provide a clear/delete control for the reviewed profile.

No generic resume parser, review screen, local generative model or cloud upload feature is implemented in this repository. Any later cloud generation needs a separate provider, cost, privacy and credential-handling decision. Do not put a provider API key in a mobile client.

## Safety boundary

UI improvements do not solve safe phone-wide interruption. Overlay, call handling, emergency access, modern Android behavior and distribution need independent verification before any app release. Do not substitute notification-only behavior without agreement or present a working emulator path as proof of physical-device safety.

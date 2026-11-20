# AI usage

## Summary

AI (Claude Code) is used for design, reasoning and code generation, with every change reviewed before it is
committed. This file grows with the project: each prompt that shaped the solution is recorded below.

## Prompt history

### 1. Assignment and repository setup

> here is an assignment
>
> ```text
> Coding Exercise
> You are asked to implement a simple library that manages a Live Football World Cup Scoreboard.
> Please read the full requirements before starting.
> Using AI to help with design, reasoning, and code generation is required. If you're selected for an
> interview, we'll walk through your solution together. We'll ask you to explain key decisions, trade-
> offs, and code details.
> What we expect
> Please provide the implementation of the Live Football World Cup Score Board as a simple Java
> package in a Maven project.
> Provide your solution as a git repository exactly as you would submit it for code review to your
> team-mates at work. Follow your own standards for high quality software engineering.
> Create a README.md documenting:
> • Your assumptions
> • Your reasoning
> • Trade-offs made
> Create a AI.md that includes:
> • Short summary of how AI tools were used
> • Include your prompt history and other contextual information
> • Any artifact that guided the implementation
> Requirements
> You work for a sports data company and must implement a scoreboard library that supports multiple
> simultaneous matches. The requirements below intentionally include open questions and design
> choices; part of the task is deciding how to handle them and documenting your reasoning.
> Core Operations (Required)
> 1. Start a new match
> 2. Update the score
> 3. Finish a match
> 4. Get a summary of matches in progress
> Return the matches in progress ordered by:
> • Total score (descending)
> • If tied → most recently started match first
> 5. Add exactly one additional operation of your choice
> Add one feature of your own choice to the scoreboard. Include documentation in the README.md
> explaining your feature and why you chose it. Please ensure that there is a distinct git commit that
> introduces the feature.
> Example Scenario
> If the following matches are started in the specified order and updated with these scores:
> • Mexico 0 – Canada 5
> • Spain 10 – Brazil 2
> • Germany 2 – France 2
> • Uruguay 6 – Italy 6
> • Argentina 3 – Australia 1
> Expected summary ordering:
> • Uruguay 6 – Italy 6
> • Spain 10 – Brazil 2
> • Mexico 0 – Canada 5
> • Argentina 3 – Australia 1
> • Germany 2 – France 2
> Deliverables
> Your repository must include:
> 1. Library implementation
> 2. Documentation (README.md, AI.md)
> ```
>
> for now prepare a repo. use com.sportradar. base package. use token-optimized instructions template for the
> java maven projects

Result: Maven scaffolding only (no scoreboard logic yet). Base package `com.sportradar.scoreboard`, Java 25,
JUnit 6, AssertJ, JaCoCo line-coverage gate, Spotless formatting, GitHub Actions CI, and agent instructions
(`AGENTS.md`, imported by `CLAUDE.md`) written to keep build and test output out of the model's context.

### 2. Branch naming

> add branch naming to instructions and skill: branch names {feature|bugfix}/{short-feature-summary}, rename current
> branch accourdingly

Result: rule added to `AGENTS.md`; setup branch renamed to `feature/maven-project-scaffold`.

## Artifacts

- `AGENTS.md` / `CLAUDE.md`: rules given to coding agents working in this repo.
- `scripts/build.sh`: quiet build wrapper the agent uses so only failures reach its context.

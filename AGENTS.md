# AGENTS.md

## Global Instruction
- All references to user identifiers in code, tests, comments, variables, parameters, and API contracts must use `userPK` (not `userId`).
- If an external system provides a login ID, map it to `userPK` immediately and do not propagate `userId` further into the codebase.

# TicketFlow1 — Prompt Log (Gregor & Aleks)

The substantive product and engineering prompts that shaped the project,
in order. Navigation, debugging, and off-topic messages are omitted; this is
the decision-bearing subset. See DECISIONS.md for the distilled design kernel.
(References to the former internal codename have been normalized to the product name.)

## 2026-07-02
- **08:04** — good you need to create documentation or atleast lest plan how to build this thing. There is already some documentation but we need to create all documentation before starting
- **08:43** — create a html so we as humans stay in the loop ... we need to visualize it bettere
- **08:48** — do we have docs for each feature?
- **09:22** — Good, so from what I understood, the mentor would like to see our features first and then dive deep into implementation and SQL and tech choices.  So which documents should we send him first?
- **10:02** — ``` Flyway our mentor told us that this might be overkill since we gonnna use just one db ```
- **13:19** — yes and fix this also:I think there should be a pop-up with "Are you sure" or "Accept" or "Approve".
- **13:32** — is it normal that client user jane two for example can see all the tickets?

## 2026-07-06
- **09:07** — workflow builder is also a priority. and full-text-serch + agent sdk for llm to help find tickets. I talked to devops guy at the company he said that ideal ticketing app is almost like gmail where on the left side you have all the tickets listed and when you click on one in the list it opens a window in the middle of the screen with all the details and everything. And he said that ideally for him would be to have like a chat bot llm to ask him "tell me all the tickets that are open for me and how much time i have" something similar
- **12:59** — so there is not doc about the db and tables and stuf flike that?
- **13:12** — make er diagram
- **13:27** — Can you check if we still have spec kits, first-step documents of the project? Because, before anything was implemented
- **13:29** — Can you check if there are any specific enums for the Postgres table? Mentor is not the most happy about them because in the future, when the code base is big, it's hard to make changes when we have already defined enums.
- **13:33** — Is it hard to change the enum types now?
- **13:33** — The mentor asked us not to use the internal company codename in the project. Can you change all of its occurrences to TicketFlow1 — documents, prompts, everything should read only TicketFlow1?
- **13:34** — Are there dynamic enums?
- **13:35** — So the argument is that, since we're using these static enums, it's hard to change them as we go. If we were to add many more, there will be a problem with the codebase growth. Is this correct?
- **13:40** — also rename the project to TicketFlow1
- **13:46** — why not use types letsay there is a company with different needs

## 2026-07-07
- **06:56** — Can we discuss the use of enums in Postgres? I heard this is not the best choice for a multi-tenant application. It's static. We need to go more flexible. What do you recommend?
- **08:26** — yes start. i see we use uuid for users?
- **08:28** — so mentor said that uuid is like overkill we should use integer
- **08:52** — also make sure the old codename doesn't appear anywhere, and that credit goes to the team (Gregor and Aleks), on the configurable-ticketing-spec

